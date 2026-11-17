package dev.sriramcodessw.versesync.eval

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Builds the FLEURS corpus on disk. Skipped unless both folders are given:
 *
 *   ./gradlew :app:testDebugUnitTest --tests "*BuildFleursCorpus*" -Pversesync.eval.fleurs=<fleursbig dir> -Pversesync.eval.corpus=<output dir>
 *     [-Pversesync.eval.languages=en-us,hi,es,de,fr] [-Pversesync.eval.recordedSongs=<n> -Pversesync.eval.distractorSongs=<n>]
 *
 * Then capture transcripts (tools/audio-test/capture_transcripts.sh) and run EvaluationRun on the output.
 * See docs/09-evaluation-harness.md.
 */
class BuildFleursCorpus {
    @Test
    fun build() {
        val fleurs = System.getProperty("versesync.eval.fleurs")
        val out = System.getProperty("versesync.eval.corpus")
        assumeTrue(!fleurs.isNullOrBlank() && !out.isNullOrBlank(), "no folders given, skipping")
        val codes = (System.getProperty("versesync.eval.languages") ?: "en-us,hi,es,de,fr").split(',').map { it.trim() }
        val recorded = System.getProperty("versesync.eval.recordedSongs")?.toIntOrNull()
        val distractors = System.getProperty("versesync.eval.distractorSongs")?.toIntOrNull()
        val corpus = FleursCorpusBuilder.build(File(fleurs), File(out), codes, recordedSongs = recorded, distractorSongs = distractors)

        val inLibrary = corpus.queries.count { it.song != null }
        println("Built ${corpus.queries.size} recordings ($inLibrary of songs in the library), ${corpus.library.size} songs, in $out")
        assertTrue(corpus.queries.isNotEmpty())
    }
}
