package dev.sriramcodessw.versesync.eval

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Runs the evaluation on a corpus on disk and writes the report. It is skipped unless a corpus is given:
 *
 *   ./gradlew :app:testDebugUnitTest --tests "*EvaluationRun*" -Pversesync.eval.corpus=<dir> [-Pversesync.eval.out=<dir>] [-Pversesync.eval.label=<name>] [-Pversesync.eval.sweep=true]
 *
 * The report goes to app/build/eval/ unless an output directory is given. See docs/09-evaluation-harness.md.
 */
class EvaluationRun {
    @Test
    fun evaluateTheCorpus() {
        val corpusDir = System.getProperty("versesync.eval.corpus")
        assumeTrue(!corpusDir.isNullOrBlank(), "no corpus given (-Pversesync.eval.corpus=<dir>), skipping")
        val corpus = Corpus.load(File(corpusDir))
        val label = System.getProperty("versesync.eval.label") ?: corpus.root.name
        val out = File(System.getProperty("versesync.eval.out") ?: "build/eval")

        val results = Replay(corpus).run()
        val written = Report.write(corpus, results, label, out)
        if (System.getProperty("versesync.eval.sweep") == "true") {
            println("Sweep written to ${Sweep.write(corpus, Sweep.run(corpus), label, out).absolutePath}")
        }

        println("Evaluation of ${results.size} recordings written to ${written.markdown.absolutePath}")
        assertTrue(written.markdown.isFile)
    }
}
