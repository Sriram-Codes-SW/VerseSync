package dev.sriramcodessw.versesync.eval

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Chooses a decision rule on a selection corpus and reports it once on a held-out corpus. Skipped unless both are given:
 *
 *   ./gradlew :app:testDebugUnitTest --tests "*HeldOutRun*" -Pversesync.eval.select=<selection corpus dir> -Pversesync.eval.corpus=<report corpus dir>
 *     [-Pversesync.eval.out=<dir>] [-Pversesync.eval.label=<name>]
 *
 * Both corpora need transcripts. See docs/09-evaluation-harness.md, section 9.
 */
class HeldOutRun {
    @Test
    fun chooseAndReport() {
        val select = System.getProperty("versesync.eval.select")
        val report = System.getProperty("versesync.eval.corpus")
        assumeTrue(!select.isNullOrBlank() && !report.isNullOrBlank(), "no corpora given, skipping")
        val selectionCorpus = Corpus.load(File(select))
        val reportCorpus = Corpus.load(File(report))
        val label = System.getProperty("versesync.eval.label") ?: reportCorpus.root.name
        val out = File(System.getProperty("versesync.eval.out") ?: "build/eval")

        val rules =
            dev.sriramcodessw.versesync.engine
                .SpeechRules()
        val selectionResults = Replay(selectionCorpus, EvalConfig(pitch = false, rules = rules)).run()
        val reportResults = Replay(reportCorpus, EvalConfig(pitch = false, rules = rules)).run()
        val written = Selection.write(selectionResults, reportResults, label, out)

        println("Held-out report written to ${written.absolutePath}")
        assertTrue(written.isFile)
    }
}
