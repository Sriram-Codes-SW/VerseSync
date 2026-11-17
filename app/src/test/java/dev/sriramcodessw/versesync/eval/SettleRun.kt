package dev.sriramcodessw.versesync.eval

import dev.sriramcodessw.versesync.engine.SpeechRules
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Chooses a settle rule on the selection corpora and reports it once on a fresh corpus (docs/09 section 12). Skipped
 * unless both are given; the selection corpora are a comma-separated list, and must have libraries of the same size:
 *
 *   ./gradlew :app:testDebugUnitTest --tests "*SettleRun*" -Pversesync.eval.select=<dev dir>,<test dir> -Pversesync.eval.corpus=<train dir>
 */
class SettleRun {
    @Test
    fun chooseAndReport() {
        val select = System.getProperty("versesync.eval.select")
        val report = System.getProperty("versesync.eval.corpus")
        assumeTrue(!select.isNullOrBlank() && !report.isNullOrBlank(), "no corpora given, skipping")
        val label = System.getProperty("versesync.eval.label") ?: File(report).name
        val out = File(System.getProperty("versesync.eval.out") ?: "build/eval")

        val rules = SpeechRules()
        val selectionCorpora = select.split(',').map { Corpus.load(File(it.trim())) }
        val sizes = selectionCorpora.map { it.library.size }.toSet()
        assertEquals(1, sizes.size, "the selection corpora must have libraries of the same size, not $sizes")
        val selection = selectionCorpora.flatMap { Replay(it, EvalConfig(pitch = false, rules = rules)).run() }
        val reportCorpus = Corpus.load(File(report))
        val reportResults = Replay(reportCorpus, EvalConfig(pitch = false, rules = rules)).run()

        val written = SettleSelection.write(selection, sizes.single(), reportResults, label, out)
        println("Settle report written to ${written.absolutePath}")
        assertTrue(written.isFile)
    }
}
