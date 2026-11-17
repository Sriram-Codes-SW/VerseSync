package dev.sriramcodessw.versesync.eval

import dev.sriramcodessw.versesync.engine.SpeechRules
import java.io.File

/*
 * Replays the same corpus under many settings of the speech rules, so the minimum evidence and the margin are
 * chosen from data and not by hand (issue #44). The speech path is swept alone: pitch does not depend on these settings, and on spoken recordings it accepts so many wrong songs that it hides the effect of any speech setting.
 *
 * The row to read is the one that keeps false accepts at or under the target and has the best found and
 * top-1 rates. With few recordings the intervals are wide and several rows are the same within them; then the
 * corpus is too small to choose, and the default stays.
 */
internal object Sweep {
    class Row(
        val rules: SpeechRules,
        val slice: Slice,
    )

    val minWords = listOf(1, 2, 3)
    val shareThresholds = listOf(0.25f, 0.35f, 0.5f)
    val fuzzy = listOf(false, true)
    val leadMargins = listOf(0f, 0.25f, 0.5f)

    fun run(corpus: Corpus): List<Row> {
        val rows = mutableListOf<Row>()
        for (words in minWords) {
            for (share in shareThresholds) {
                for (isFuzzy in fuzzy) {
                    // The lead margin only changes what the lock does, so replay once per speech setting.
                    val rules = SpeechRules(shareThreshold = share, minWords = words, fuzzy = isFuzzy)
                    val results = Replay(corpus, EvalConfig(rules = rules)).run()
                    for (margin in leadMargins) {
                        rows += Row(rules.copy(leadMargin = margin), Metrics.slice("all", results, Path.SPEECH, corpus.library, margin))
                    }
                }
            }
        }
        return rows
    }

    fun markdown(
        corpus: Corpus,
        rows: List<Row>,
        label: String,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("# Speech rule sweep: $label")
        sb.appendLine()
        corpus.caution?.let { sb.appendLine("> **$it**").also { sb.appendLine() } }
        sb.appendLine("Speech path only. The defaults are minWords 2, share 0.35, fuzzy off, margin 0.25.")
        sb.appendLine()
        sb.appendLine("| min words | share | fuzzy | margin | found | top-1 | false accept | lock opens | lock false |")
        sb.appendLine("|---|---|---|---|---|---|---|---|---|")
        rows.forEach { r ->
            val d = SpeechRules()
            val mark = if (r.rules == d) " (default)" else ""
            val s = r.slice
            sb.appendLine(
                "| ${r.rules.minWords}$mark | ${r.rules.shareThreshold} | ${if (r.rules.fuzzy) "on" else "off"} | ${r.rules.leadMargin} | ${s.found} | ${s.top1} | ${s.falseAccept} | ${s.lockOpens} | ${s.lockFalse} |",
            )
        }
        return sb.toString()
    }

    fun write(
        corpus: Corpus,
        rows: List<Row>,
        label: String,
        outDir: File,
    ): File {
        outDir.mkdirs()
        return File(outDir, "sweep.md").also { it.writeText(markdown(corpus, rows, label)) }
    }
}
