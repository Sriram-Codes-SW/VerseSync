package dev.sriramcodessw.versesync.eval

import java.io.File

/*
 * Chooses a decision rule on one corpus (the selection corpus, built from the FLEURS dev split) and reports it ONCE
 * on another (the report corpus, built from the test split). The two have different sentences, so nothing the rule was
 * chosen on is in what it is reported on. Choosing among many settings on the data they are reported on would
 * flatter the best one (docs/09 section 9).
 *
 * The criterion is fixed here, before any run, and is stated in docs/09:
 *   a rule is eligible if, on the selection corpus and acting the FIRST time it fires,
 *     - it decides on a song not in the library for at most 2 percent of those recordings, and the upper end of the
 *       95 percent interval is at most 5 percent, and
 *     - it names a different song than the one sung for at most 1 percent of recordings of songs in the library;
 *   the rule chosen is the eligible one that names the right song most often. A tie goes to the simpler rule: fewer
 *   words, then lower share, then smaller gap. If no rule is eligible, none is chosen and the report says so.
 */
internal object Selection {
    val minWords = listOf(1, 2, 3, 4, 5, 6, 8)
    val minShares = listOf(0.35f, 0.5f, 0.65f, 0.8f)
    val gaps = listOf(0f, 0.5f, 1f, 1.5f, 2f, 3f)

    const val MAX_FALSE = 0.02
    const val MAX_FALSE_UPPER = 0.05
    const val MAX_WRONG_SONG = 0.01

    val grid: List<DecisionRule> =
        minWords.flatMap { w -> minShares.flatMap { s -> gaps.map { g -> DecisionRule(w, s, g) } } }

    class Scored(
        val rule: DecisionRule,
        val slice: RuleSlice,
    ) {
        val eligible: Boolean
            get() =
                slice.firstFalse.value <= MAX_FALSE &&
                    slice.firstFalse.high <= MAX_FALSE_UPPER &&
                    slice.firstWrongSong.value <= MAX_WRONG_SONG
    }

    fun scoreGrid(results: List<QueryResult>): List<Scored> = grid.map { Scored(it, RuleMetrics.evaluate(it.label(), results, it)) }

    /** The chosen rule among [scored], or null if none is eligible. */
    fun choose(scored: List<Scored>): DecisionRule? =
        scored
            .filter { it.eligible }
            .sortedWith(
                compareByDescending<Scored> { it.slice.firstRight.value }
                    .thenBy { it.rule.minWords }
                    .thenBy { it.rule.minShare }
                    .thenBy { it.rule.absoluteGap },
            ).firstOrNull()
            ?.rule

    private fun table(slices: List<RuleSlice>): String {
        val sb = StringBuilder()
        sb.appendLine("| rule | known | acts right first | acts on wrong song first | unknown | acts on unknown first | right at end | on unknown at end | no rival at end |")
        sb.appendLine("|---|---|---|---|---|---|---|---|---|")
        slices.forEach { s ->
            sb.appendLine(
                "| ${s.name} | ${s.known} | ${s.firstRight} | ${s.firstWrongSong} | ${s.unknown} | ${s.firstFalse} | ${s.finalRight} | ${s.finalFalse} | ${s.noRivalAtEnd} |",
            )
        }
        return sb.toString()
    }

    fun report(
        selection: List<QueryResult>,
        reportSet: List<QueryResult>,
        label: String,
    ): String {
        val scored = scoreGrid(selection)
        val chosen = choose(scored)
        val baseline = listOf("current speech rule" to DecisionRule.current, "current lock words rule" to DecisionRule.currentLock)
        val sb = StringBuilder()
        sb.appendLine("# Held-out report: $label")
        sb.appendLine()
        sb.appendLine("Read speech, not singing. Selection corpus: ${selection.size} recordings (${selection.count { it.query.song == null }} of songs not in the library). Report corpus: ${reportSet.size} recordings (${reportSet.count { it.query.song == null }} of songs not in the library).")
        sb.appendLine()
        sb.appendLine("Criterion, fixed before the run: acting the first time it fires, on at most 2 percent of unknown-song recordings (upper end of the interval at most 5 percent), naming a wrong song for at most 1 percent of known-song recordings; of those, the rule that names the right song most often. Ties go to fewer words, lower share, smaller gap.")
        sb.appendLine()
        sb.appendLine("Every rate is hits/total = percent (95 percent Wilson interval). \"Acts first\" counts a rule as acting the first time it fires during the recording; \"at end\" looks only at the evidence at the end. The rival is taken from every song with a heard word, not only candidates.")
        sb.appendLine()

        sb.appendLine("## Chosen on the selection corpus")
        sb.appendLine()
        if (chosen == null) {
            sb.appendLine("**No rule in the grid of ${grid.size} met the criterion on the selection corpus, so none is chosen.** The closest by false-decision rate:")
            sb.appendLine()
            val closest = scored.filter { it.slice.firstWrongSong.value <= MAX_WRONG_SONG }.sortedBy { it.slice.firstFalse.high }.take(5)
            sb.append(table(closest.map { it.slice }))
        } else {
            sb.appendLine("Chosen: `${chosen.label()}`.")
            sb.appendLine()
            sb.append(table(listOf(RuleMetrics.evaluate("chosen: ${chosen.label()}", selection, chosen)) + baseline.map { (n, r) -> RuleMetrics.evaluate(n, selection, r) }))
        }
        sb.appendLine()
        sb.appendLine("The ten eligible rules with the best right rate on the selection corpus, to see how flat the top is:")
        sb.appendLine()
        sb.append(
            table(
                scored
                    .filter { it.eligible }
                    .sortedByDescending { it.slice.firstRight.value }
                    .take(10)
                    .map { it.slice },
            ),
        )
        sb.appendLine()

        sb.appendLine("## Reported on the held-out corpus")
        sb.appendLine()
        val rows = baseline.map { (n, r) -> RuleMetrics.evaluate(n, reportSet, r) }
        sb.append(table((if (chosen == null) emptyList() else listOf(RuleMetrics.evaluate("chosen: ${chosen.label()}", reportSet, chosen))) + rows))
        sb.appendLine()
        if (chosen != null) {
            sb.appendLine("### Chosen rule by language and by snippet length")
            sb.appendLine()
            val byLanguage = reportSet.groupBy { it.query.languageCode }.toSortedMap().map { (k, v) -> RuleMetrics.evaluate("language: $k", v, chosen) }
            val bySnippet = reportSet.groupBy { it.query.snippetName }.toSortedMap().map { (k, v) -> RuleMetrics.evaluate("snippet: $k", v, chosen) }
            sb.append(table(byLanguage + bySnippet))
            sb.appendLine()

            sb.appendLine("### Leave one language out")
            sb.appendLine()
            sb.appendLine("For each language, the rule is chosen on the selection corpus WITHOUT that language and reported on that language's recordings in the report corpus: does the rule carry over to a language it was not tuned on?")
            sb.appendLine()
            val lolo =
                reportSet.groupBy { it.query.languageCode }.toSortedMap().map { (language, recordings) ->
                    val without = scoreGrid(selection.filter { it.query.languageCode != language })
                    val rule = choose(without)
                    if (rule == null) {
                        "| $language | none eligible | | |"
                    } else {
                        val s = RuleMetrics.evaluate(rule.label(), recordings, rule)
                        "| $language | `${rule.label()}` | ${s.firstRight} | ${s.firstFalse} |"
                    }
                }
            sb.appendLine("| language left out | rule chosen without it | acts right first | acts on unknown first |")
            sb.appendLine("|---|---|---|---|")
            lolo.forEach { sb.appendLine(it) }
        }
        return sb.toString()
    }

    fun write(
        selection: List<QueryResult>,
        reportSet: List<QueryResult>,
        label: String,
        outDir: File,
    ): File {
        outDir.mkdirs()
        return File(outDir, "heldout.md").also { it.writeText(report(selection, reportSet, label)) }
    }
}
