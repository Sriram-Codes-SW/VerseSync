package dev.sriramcodessw.versesync.eval

import dev.sriramcodessw.versesync.engine.SpeechRules
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Writes the diagnostics of the held-out result (docs/09 section 11). Skipped unless both corpora are given:
 *
 *   ./gradlew :app:testDebugUnitTest --tests "*DiagnosticsRun*" -Pversesync.eval.select=<selection corpus> -Pversesync.eval.corpus=<report corpus>
 */
class DiagnosticsRun {
    @Test
    fun diagnose() {
        val select = System.getProperty("versesync.eval.select")
        val report = System.getProperty("versesync.eval.corpus")
        assumeTrue(!select.isNullOrBlank() && !report.isNullOrBlank(), "no corpora given, skipping")
        val out = File(System.getProperty("versesync.eval.out") ?: "build/eval")

        fun load(
            name: String,
            dir: String,
        ): Diagnostics.Named {
            val corpus = Corpus.load(File(dir))
            return Diagnostics.Named(name, corpus, Replay(corpus, EvalConfig(pitch = false, rules = SpeechRules())).run())
        }

        val written = Diagnostics.write(load("dev", select), load("test", report), out)
        println("Diagnostics written to ${written.absolutePath}")
        assertTrue(written.isFile)
    }
}
