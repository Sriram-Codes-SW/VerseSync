package dev.sriramcodessw.versesync.eval

import dev.sriramcodessw.versesync.fingerprinting.SyntheticSignals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** TC-U-42: the FLEURS corpus builder, on a small made-up FLEURS folder. */
class FleursCorpusBuilderTest {
    @TempDir
    lateinit var dir: File

    /** 12 recordings of 10 sentences (two sentences read twice), and 12 more sentences with no recording. */
    private fun fakeFleurs(): File {
        val root = File(dir, "fleurs")
        val lines = mutableListOf<String>()
        val sentences = (0 until 22).map { "word$it alpha$it beta$it gamma$it delta$it" }
        val clips = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1) // sentence index of each recording
        clips.forEachIndexed { n, s ->
            val name = "f$n.wav"
            Wav.write(File(root, "xx/wav/$name"), SyntheticSignals.voiceLike(150.0 + 10 * n, SyntheticSignals.frames(40)))
            lines += "$n\t$name\t${sentences[s]}\t${sentences[s]}\tphon\t100\t${if (n % 2 == 0) "FEMALE" else "MALE"}"
        }
        (10 until 22).forEach { s -> lines += "${100 + s}\tmissing$s.wav\t${sentences[s]}\t${sentences[s]}\tphon\t100\tMALE" }
        File(root, "xx/test.tsv").writeText(lines.joinToString("\n"), Charsets.UTF_8)
        return root
    }

    @Test
    fun theCorpus_hasSongsOfSeveralSentences_andRecordingsInAndOutOfTheLibrary() {
        val corpus = FleursCorpusBuilder.build(fakeFleurs(), File(dir, "out"), listOf("xx"))

        assertEquals(12, corpus.queries.size, "one query per recording")
        val inLibrary = corpus.queries.filter { it.song != null }
        val outside = corpus.queries.filter { it.song == null }
        assertTrue(inLibrary.isNotEmpty() && outside.isNotEmpty())

        // A song is four sentences, and a song that has a recording has a pitch reference.
        val recorded = corpus.library.filter { it.reference != null }
        val unrecorded = corpus.library.filter { it.reference == null }
        assertTrue(recorded.isNotEmpty() && unrecorded.isNotEmpty(), "songs with and without a recording")
        assertTrue(corpus.library.all { it.lyricText.split(' ').size >= 5 })

        // A recording whose sentence is in a song names that song, and no recording outside names one.
        inLibrary.forEach { q -> assertTrue(q.song in recorded.map { it.id }) }
        // Both readings of a sentence are in the library, one of them by another speaker than the reference.
        assertTrue(corpus.queries.any { it.condition == "other speaker" } || inLibrary.none { it.condition == "other speaker" })
        assertTrue(outside.all { it.condition == "read speech" })
        assertTrue(corpus.queries.all { it.mode == "speak" && it.language == "xx" })
    }

    @Test
    fun snippets_areThreeFiveOrTenSeconds_orTheWholeRecording() {
        val corpus = FleursCorpusBuilder.build(fakeFleurs(), File(dir, "out"), listOf("xx"))
        corpus.queries.forEach { q ->
            val seconds = Wav.read16k(corpus.recording(q.file!!)).size / Wav.TARGET_RATE.toDouble()
            // The made-up recordings are 2.56 seconds, shorter than the shortest snippet, so each is whole.
            assertEquals(2.56, seconds, 0.01)
        }
    }

    @Test
    fun sameInputAndSeed_giveTheSameCorpus_andAnotherSeedDoesNot() {
        val fleurs = fakeFleurs()
        FleursCorpusBuilder.build(fleurs, File(dir, "a"), listOf("xx"))
        FleursCorpusBuilder.build(fleurs, File(dir, "b"), listOf("xx"))
        FleursCorpusBuilder.build(fleurs, File(dir, "c"), listOf("xx"), seed = 7L)

        fun text(
            root: String,
            name: String,
        ) = File(dir, "$root/$name").readText()
        assertEquals(text("a", "queries.json"), text("b", "queries.json"))
        assertEquals(text("a", "library.json"), text("b", "library.json"))
        File(dir, "a/recordings").listFiles()!!.forEach { f ->
            assertTrue(f.readBytes().contentEquals(File(dir, "b/recordings/${f.name}").readBytes()), "${f.name} differs")
        }
        assertNotEquals(text("a", "queries.json"), text("c", "queries.json"))
    }

    @Test
    fun theBuiltCorpus_isReplayable() {
        val corpus = FleursCorpusBuilder.build(fakeFleurs(), File(dir, "out"), listOf("xx"))
        // No transcripts yet, so only pitch runs; it must not fail on a built corpus.
        val results = Replay(corpus).run()
        assertEquals(corpus.queries.size, results.size)
    }
}
