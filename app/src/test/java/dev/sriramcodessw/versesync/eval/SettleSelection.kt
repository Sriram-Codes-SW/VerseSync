package dev.sriramcodessw.versesync.eval

import java.io.File
import kotlin.math.ln

/*
 * Chooses a settle rule on the selection corpora (dev and test, built with libraries of the same size) and reports it
 * ONCE on a fresh corpus (the FLEURS train split). The grid, the criterion and the pass bar are fixed in
 * docs/09-evaluation-harness.md section 12, which was committed before this was run. Do not change them to suit a result.
 */
internal object SettleSelection {
    const val MIN_WORDS = 4
    val shares = listOf(0.65f, 0.8f)

    /** The gap in units of ln(1 + songs), so the same setting means the same thing in a library of any size. */
    val gapUnits = listOf(0.4f, 0.6f, 0.8f, 1.0f)

    /** Hold in 64 ms frames: 0, 0.5 s, 1 s and 2 s. */
    val holds = listOf(0, 8, 16, 31)
    val evidence = listOf(false, true) // retractable?

    // Selection criterion: at most 1 percent of unknown-song recordings (upper end of the interval at most 3 percent),
    // and at most 1 percent of known-song recordings named wrongly.
    const val SELECT_FALSE = 0.01
    const val SELECT_FALSE_UPPER = 0.03
    const val SELECT_WRONG = 0.01

    // Pass bar on the report corpus: the upper end for unknown-song decisions at most 5 percent, wrong songs at most 1 percent.
    const val PASS_FALSE_UPPER = 0.05
    const val PASS_WRONG = 0.01

    class Spec(
        val rule: DecisionRule,
        val settle: Settle,
        val gapUnits: Float,
    ) {
        fun label() = "words>=${rule.minWords} share>=${rule.minShare} gap>${"%.1f".format(gapUnits)}ln(1+N) ${settle.label()}"
    }

    class Scored(
        val spec: Spec,
        val slice: RuleSlice,
    ) {
        val eligible: Boolean
            get() = slice.firstFalse.value <= SELECT_FALSE && slice.firstFalse.high <= SELECT_FALSE_UPPER && slice.firstWrongSong.value <= SELECT_WRONG
    }

    fun grid(librarySize: Int): List<Spec> {
        val unit = ln(1.0 + librarySize).toFloat()
        return shares.flatMap { s ->
            gapUnits.flatMap { g ->
                holds.flatMap { h ->
                    evidence.map { r -> Spec(DecisionRule(MIN_WORDS, s, g * unit), Settle(h, r), g) }
                }
            }
        }
    }

    fun scoreGrid(
        results: List<QueryResult>,
        librarySize: Int,
    ): List<Scored> = grid(librarySize).map { Scored(it, RuleMetrics.evaluate(it.label(), results, it.rule, it.settle)) }

    fun choose(scored: List<Scored>): Spec? =
        scored
            .filter { it.eligible }
            .sortedWith(
                compareByDescending<Scored> { it.slice.firstRight.value }
                    .thenBy { it.spec.settle.holdFrames }
                    .thenBy { it.spec.settle.retractable }
                    .thenBy { it.spec.rule.minShare }
                    .thenBy { it.spec.gapUnits },
            ).firstOrNull()
            ?.spec

    private fun table(rows: List<Pair<String, RuleSlice>>): String {
        val sb = StringBuilder()
        sb.appendLine("| rule | known | right first | wrong song first | no decision | unknown | decides on unknown first | median s to right | p90 s to right |")
        sb.appendLine("|---|---|---|---|---|---|---|---|---|")
        rows.forEach { (name, s) ->
            val none = s.known - s.firstRight.hits - s.firstWrongSong.hits

            fun secs(x: Double?) = x?.let { "%.1f".format(it) } ?: "n/a"
            sb.appendLine("| $name | ${s.known} | ${s.firstRight} | ${s.firstWrongSong} | $none | ${s.unknown} | ${s.firstFalse} | ${secs(s.medianSecondsToRight)} | ${secs(s.p90SecondsToRight)} |")
        }
        return sb.toString()
    }

    fun report(
        selection: List<QueryResult>,
        selectionLibrary: Int,
        reportSet: List<QueryResult>,
        label: String,
    ): String {
        val scored = scoreGrid(selection, selectionLibrary)
        val chosen = choose(scored)
        val baselines = listOf("current speech rule (sticky, no hold)" to DecisionRule.current, "current lock words rule (sticky, no hold)" to DecisionRule.currentLock)
        val sb = StringBuilder()
        sb.appendLine("# Settle rule report: $label")
        sb.appendLine()
        sb.appendLine("Read speech, not singing. Selection: ${selection.size} recordings (${selection.count { it.query.song == null }} of songs not in the library), libraries of $selectionLibrary songs. Report: ${reportSet.size} recordings (${reportSet.count { it.query.song == null }} of songs not in the library). Grid of ${scored.size} rules, criterion and pass bar as fixed in docs/09 section 12. Updates at the last reported frame are not counted.")
        sb.appendLine()

        sb.appendLine("## Chosen on the selection corpora")
        sb.appendLine()
        if (chosen == null) {
            sb.appendLine("**No rule met the criterion on the selection corpora, so none is chosen.** The five with the lowest upper bound on unknown-song decisions:")
            sb.appendLine()
            sb.append(
                table(
                    scored
                        .filter { it.slice.firstWrongSong.value <= SELECT_WRONG }
                        .sortedBy { it.slice.firstFalse.high }
                        .take(5)
                        .map { it.spec.label() to it.slice },
                ),
            )
        } else {
            sb.appendLine("Chosen: `${chosen.label()}`.")
            sb.appendLine()
            sb.append(table(listOf("chosen: ${chosen.label()}" to RuleMetrics.evaluate("c", selection, chosen.rule, chosen.settle)) + baselines.map { (n, r) -> n to RuleMetrics.evaluate(n, selection, r) }))
        }
        sb.appendLine()
        sb.appendLine("The ten eligible rules that name the right song most often on the selection corpora:")
        sb.appendLine()
        sb.append(
            table(
                scored
                    .filter { it.eligible }
                    .sortedByDescending { it.slice.firstRight.value }
                    .take(10)
                    .map { it.spec.label() to it.slice },
            ),
        )
        sb.appendLine()
        sb.appendLine("Effect of the hold and the evidence on the selection corpora, at the share and gap of the best eligible rule (or the first grid row if none):")
        sb.appendLine()
        val anchor = chosen ?: scored.first().spec
        sb.append(
            table(
                scored
                    .filter { it.spec.rule.minShare == anchor.rule.minShare && it.spec.gapUnits == anchor.gapUnits }
                    .map { it.spec.settle.label() to it.slice },
            ),
        )
        sb.appendLine()

        sb.appendLine("## Reported once on the fresh corpus")
        sb.appendLine()
        val reportRows = mutableListOf<Pair<String, RuleSlice>>()
        if (chosen != null) reportRows += "chosen: ${chosen.label()}" to RuleMetrics.evaluate("c", reportSet, chosen.rule, chosen.settle)
        baselines.forEach { (n, r) -> reportRows += n to RuleMetrics.evaluate(n, reportSet, r) }
        sb.append(table(reportRows))
        sb.appendLine()
        if (chosen != null) {
            val s = RuleMetrics.evaluate("c", reportSet, chosen.rule, chosen.settle)
            val passFalse = s.firstFalse.high <= PASS_FALSE_UPPER
            val passWrong = s.firstWrongSong.value <= PASS_WRONG
            sb.appendLine("**Pass bar:** decides on an unknown song with the upper end of the interval at most ${(PASS_FALSE_UPPER * 100).toInt()}% (${s.firstFalse}): ${if (passFalse) "PASS" else "FAIL"}. Names a different song than the one sung for at most ${(PASS_WRONG * 100).toInt()}% (${s.firstWrongSong}): ${if (passWrong) "PASS" else "FAIL"}.")
            sb.appendLine()
            sb.appendLine("### By language and by snippet length")
            sb.appendLine()
            val byLanguage = reportSet.groupBy { it.query.languageCode }.toSortedMap().map { (k, v) -> "language: $k" to RuleMetrics.evaluate(k, v, chosen.rule, chosen.settle) }
            val bySnippet = reportSet.groupBy { it.query.snippetName }.toSortedMap().map { (k, v) -> "snippet: $k" to RuleMetrics.evaluate(k, v, chosen.rule, chosen.settle) }
            sb.append(table(byLanguage + bySnippet))
            sb.appendLine()
            sb.appendLine("### Leave one language out")
            sb.appendLine()
            sb.appendLine("The rule is chosen on the selection corpora WITHOUT that language and reported on that language's recordings in the fresh corpus.")
            sb.appendLine()
            sb.appendLine("| language left out | rule chosen without it | right first | decides on unknown first |")
            sb.appendLine("|---|---|---|---|")
            reportSet.groupBy { it.query.languageCode }.toSortedMap().forEach { (language, recordings) ->
                val rule = choose(scoreGrid(selection.filter { it.query.languageCode != language }, selectionLibrary))
                if (rule == null) {
                    sb.appendLine("| $language | none eligible | | |")
                } else {
                    val r = RuleMetrics.evaluate("l", recordings, rule.rule, rule.settle)
                    sb.appendLine("| $language | `${rule.label()}` | ${r.firstRight} | ${r.firstFalse} |")
                }
            }
        }
        return sb.toString()
    }

    fun write(
        selection: List<QueryResult>,
        selectionLibrary: Int,
        reportSet: List<QueryResult>,
        label: String,
        outDir: File,
    ): File {
        outDir.mkdirs()
        return File(outDir, "settle.md").also { it.writeText(report(selection, selectionLibrary, reportSet, label)) }
    }
}
