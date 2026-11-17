package dev.sriramcodessw.versesync.eval

import java.io.File
import kotlin.math.ln

/*
 * Checks on the held-out result of docs/09 section 10, made on data that already exists, before a new rule is built.
 * The rule chosen there decided on a song not in the library for 1.9 percent of selection recordings and 7.8 percent of
 * report recordings. Was that firing early on garbled partial text, or something else (selection optimism, a different
 * corpus, a different library size)? Nothing here chooses anything.
 */
internal object Diagnostics {
    /** The rule chosen in docs/09 section 10, chosen on the selection corpus with a library of 110 songs. */
    val chosen = DecisionRule(minWords = 4, minShare = 0.65f, absoluteGap = 3.0f)
    const val CHOSEN_LIBRARY = 110

    private fun seconds(frame: Int) = frame * Replay.FRAME_MS / 1000.0

    class Named(
        val name: String,
        val corpus: Corpus,
        val results: List<QueryResult>,
    )

    /** Why the rule did not still decide on the same song at the end of the recording, for a first firing that was wrong. */
    private fun whyNotAtEnd(
        result: QueryResult,
        rule: DecisionRule,
        firedSong: String,
    ): String {
        val state = result.speechSteps.last().state
        val atEnd = rule.decide(state)
        if (atEnd != null) return if (atEnd.song == firedSong) "still decides on the same song at the end" else "decides on a different song at the end"
        val ranked = state.entries.sortedByDescending { it.value.weight }
        val top = ranked.first()
        if (top.key != firedSong) return "no decision at the end: another song overtook it"
        val detail = top.value
        val rival = if (ranked.size > 1) ranked[1].value.weight else 0f
        return when {
            detail.words < minOf(rule.minWords, detail.lyricWords) -> "no decision at the end: too few words"
            detail.share < rule.minShare -> "no decision at the end: the share fell"
            else -> "no decision at the end: a rival caught up (gap ${"%.1f".format(detail.weight - rival)})"
        }
    }

    fun report(
        selection: Named,
        reportSet: Named,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("# Diagnostics of the held-out result")
        sb.appendLine()
        sb.appendLine("Rule: `${chosen.label()}`, chosen on ${selection.name} (${selection.corpus.library.size} songs). Report corpus: ${reportSet.name} (${reportSet.corpus.library.size} songs). Read speech, not singing.")
        sb.appendLine()

        sb.appendLine("## 1. The false first firings on the report corpus")
        sb.appendLine()
        val firings =
            reportSet.results
                .filter { it.query.song == null }
                .mapNotNull { r -> RuleMetrics.firing(r.speechSteps, chosen)?.let { r to it } }
        sb.appendLine("${firings.size} of ${reportSet.results.count { it.query.song == null }} recordings of songs not in the library fired the rule at some point. What the evidence looked like at the end of each:")
        sb.appendLine()
        sb.appendLine("| at the end of the recording | recordings |")
        sb.appendLine("|---|---|")
        firings.groupBy { (r, f) -> whyNotAtEnd(r, chosen, f.song).substringBefore(" (") }.toSortedMap().forEach { (k, v) -> sb.appendLine("| $k | ${v.size} |") }
        sb.appendLine()
        val firedAt = firings.map { (_, f) -> seconds(f.frame) }.sorted()
        val ends = firings.map { (r, _) -> seconds(r.speechSteps.last().frame) }
        val share = firings.map { (r, f) -> f.frame.toDouble() / r.speechSteps.last().frame }.sorted()
        if (firedAt.isNotEmpty()) {
            sb.appendLine("The rule fired at a median of ${"%.1f".format(firedAt[firedAt.size / 2])} s (earliest ${"%.1f".format(firedAt.first())}, latest ${"%.1f".format(firedAt.last())}), at a median of ${"%.0f".format(share[share.size / 2] * 100)} percent of the way to the last reported update (recordings last a median of ${"%.1f".format(ends.sorted()[ends.size / 2])} s).")
        }
        sb.appendLine()

        sb.appendLine("## 2. First firing against the end, on both corpora")
        sb.appendLine()
        sb.appendLine("| corpus | decides on unknown, first | decides on unknown, at the end | right, first | right, at the end |")
        sb.appendLine("|---|---|---|---|---|")
        listOf(selection, reportSet).forEach { c ->
            val s = RuleMetrics.evaluate(c.name, c.results, chosen)
            sb.appendLine("| ${c.name} | ${s.firstFalse} | ${s.finalFalse} | ${s.firstRight} | ${s.finalRight} |")
        }
        sb.appendLine()

        sb.appendLine("## 3. Acting by a given time")
        sb.appendLine()
        sb.appendLine("Recordings that had acted by T seconds of audio, over recordings of songs not in the library (false) and in the library (right).")
        sb.appendLine()
        sb.appendLine("| T (s) | ${selection.name} false | ${reportSet.name} false | ${selection.name} right | ${reportSet.name} right |")
        sb.appendLine("|---|---|---|---|---|")
        val limits = listOf(1, 2, 3, 4, 5, 6, 8, 10)

        fun byTime(
            c: Named,
            t: Int,
        ): Pair<Rate, Rate> {
            val frames = t * 1000 / Replay.FRAME_MS
            var unknown = 0
            var falseBy = 0
            var known = 0
            var rightBy = 0
            c.results.forEach { r ->
                val fired = RuleMetrics.firing(r.speechSteps, chosen)
                val early = fired != null && fired.frame <= frames
                if (r.query.song == null) {
                    unknown++
                    if (early) falseBy++
                } else {
                    known++
                    if (early && fired?.song == r.query.song) rightBy++
                }
            }
            return Rate(falseBy, unknown) to Rate(rightBy, known)
        }
        limits.forEach { t ->
            val (sf, sr) = byTime(selection, t)
            val (rf, rr) = byTime(reportSet, t)
            sb.appendLine("| $t | $sf | $rf | $sr | $rr |")
        }
        sb.appendLine()

        sb.appendLine("## 4. The gap scaled for the size of the library")
        sb.appendLine()
        val factor = ln(1.0 + reportSet.corpus.library.size) / ln(1.0 + CHOSEN_LIBRARY)
        val scaled = chosen.copy(absoluteGap = (chosen.absoluteGap * factor).toFloat())
        sb.appendLine("A word's weight is `ln(1 + songs / songs containing it)`, so the same gap means more in a smaller library. The gap was chosen with $CHOSEN_LIBRARY songs; with ${reportSet.corpus.library.size} songs the same relative gap is ${"%.2f".format(scaled.absoluteGap)}.")
        sb.appendLine()
        sb.appendLine("| rule on ${reportSet.name} | decides on unknown, first | at the end | right, first |")
        sb.appendLine("|---|---|---|---|")
        listOf("gap ${chosen.absoluteGap} (as chosen)" to chosen, "gap ${"%.2f".format(scaled.absoluteGap)} (scaled)" to scaled).forEach { (label, rule) ->
            val s = RuleMetrics.evaluate(label, reportSet.results, rule)
            sb.appendLine("| $label | ${s.firstFalse} | ${s.finalFalse} | ${s.firstRight} |")
        }
        sb.appendLine()

        sb.appendLine("## 5. By language, on both corpora")
        sb.appendLine()
        sb.appendLine("| language | ${selection.name} unknown | ${selection.name} decides first | ${reportSet.name} unknown | ${reportSet.name} decides first |")
        sb.appendLine("|---|---|---|---|---|")
        val languages = (selection.results + reportSet.results).map { it.query.languageCode }.toSortedSet()
        languages.forEach { l ->
            val a = RuleMetrics.evaluate(l, selection.results.filter { it.query.languageCode == l }, chosen)
            val b = RuleMetrics.evaluate(l, reportSet.results.filter { it.query.languageCode == l }, chosen)
            sb.appendLine("| $l | ${a.unknown} | ${a.firstFalse} | ${b.unknown} | ${b.firstFalse} |")
        }
        return sb.toString()
    }

    fun write(
        selection: Named,
        reportSet: Named,
        outDir: File,
    ): File {
        outDir.mkdirs()
        return File(outDir, "diagnostics.md").also { it.writeText(report(selection, reportSet)) }
    }
}
