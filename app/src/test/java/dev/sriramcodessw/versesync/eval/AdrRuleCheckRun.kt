package dev.sriramcodessw.versesync.eval

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.ln
import kotlin.test.assertTrue

/**
 * Replays the decision rule proposed in ADR-18, exactly as written, beside the two rules the app runs today, on a corpus
 * of recordings with captured transcripts (docs/09 section 18). Nothing is chosen or tuned here and nothing in the app
 * changes. Skipped unless a corpus is given:
 *
 *   ./gradlew :app:testDebugUnitTest --tests "*AdrRuleCheckRun*" -Pversesync.eval.corpus=<dir> [-Pversesync.eval.label=<name>]
 *
 * Writes `build/eval/adr18-<label>.md`: counts per rule and kind of evidence, and for every recording what each rule
 * named (the first time it fires, as the app would act) so that nothing is hidden in a total.
 */
class AdrRuleCheckRun {
    @Test
    fun replayTheRules() {
        val corpusDir = System.getProperty("versesync.eval.corpus")
        assumeTrue(!corpusDir.isNullOrBlank(), "no corpus given, skipping")
        val corpus = Corpus.load(File(corpusDir))
        val label = System.getProperty("versesync.eval.label") ?: corpus.root.name
        val results = Replay(corpus, EvalConfig(pitch = false)).run()
        val n = corpus.library.size

        // ADR-18: at least 4 different words, share at least 0.8, a lead of MORE than 1.0 * ln(1 + N) in rarity weight.
        val adr = DecisionRule(minWords = 4, minShare = 0.8f, absoluteGap = ln(1.0 + n).toFloat())
        val rules = listOf("ADR-18 rule" to adr, "current lock words rule" to DecisionRule.currentLock, "current speech rule" to DecisionRule.current)
        val settles = listOf("sticky evidence (what the app keeps)" to Settle(0, false), "retractable evidence (what ADR-18 measured)" to Settle(0, true))

        val out = StringBuilder()
        out.appendLine("# Decision rules on real recordings: $label")
        out.appendLine()
        out.appendLine("Library of $n songs; ADR-18's gap is 1.0 * ln(1 + $n) = ${"%.2f".format(ln(1.0 + n))}. Acting the first time a rule fires.")
        out.appendLine("${results.count { it.query.song != null }} recordings of songs in the library, ${results.count { it.query.song == null }} of songs not in it.")
        out.appendLine()
        for ((settleName, settle) in settles) {
            out.appendLine("## $settleName")
            out.appendLine()
            out.appendLine("| Rule | Right song, of songs in the library | Wrong song | Decides on a song not in the library |")
            out.appendLine("|---|---|---|---|")
            for ((name, rule) in rules) {
                val s = RuleMetrics.evaluate(name, results, rule, settle)
                out.appendLine("| $name | ${s.firstRight} | ${s.firstWrongSong} | ${s.firstFalse} |")
            }
            out.appendLine()
        }
        out.appendLine("## What each rule named, per recording (sticky / retractable)")
        out.appendLine()
        out.appendLine("| Recording | Song sung | ADR-18 rule | current lock words rule | current speech rule |")
        out.appendLine("|---|---|---|---|---|")
        for (result in results) {
            val cells =
                rules.map { (_, rule) ->
                    settles.joinToString(" / ") { (_, settle) ->
                        val steps = if (settle.retractable) result.retractableSteps else result.speechSteps
                        RuleMetrics.firing(steps, rule, settle)?.song ?: "-"
                    }
                }
            out.appendLine("| ${result.query.id} | ${result.query.song ?: "(not in library)"} | ${cells.joinToString(" | ")} |")
        }
        val file = File("build/eval").also { it.mkdirs() }.resolve("adr18-$label.md")
        file.writeText(out.toString(), Charsets.UTF_8)
        println("Written to ${file.absolutePath}")
        assertTrue(file.isFile)
    }
}
