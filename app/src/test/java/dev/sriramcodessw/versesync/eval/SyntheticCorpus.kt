package dev.sriramcodessw.versesync.eval

import com.google.gson.Gson
import dev.sriramcodessw.versesync.fingerprinting.SyntheticSignals
import java.io.File

/**
 * A small corpus made of generated tones and invented words, to test the harness itself. It says nothing about
 * how well the app recognizes real singing; it only has known answers.
 */
object SyntheticCorpus {
    private const val BASE_HZ = 180.0
    private const val FRAMES_PER_NOTE = 8

    val melodyA = listOf(0, 4, 7, 4, 0, -3, 0, 4, 7, 12, 7, 4, 0, -3, -5)
    val melodyB = listOf(0, 2, 4, 5, 7, 9, 11, 12, 11, 9, 7, 5, 4, 2, 0)
    val melodyC = listOf(7, 7, 0, 0, 9, 9, 7, 5, 5, 4, 4, 2, 2, 0, 0)
    val melodyD = listOf(12, -5, 10, -3, 8, -1, 6, 1, 4, 3, 2, -12, 5, -8, 0)

    /** One voice-like note per entry, [FRAMES_PER_NOTE] frames each, [transposeSemitones] above the base. */
    fun melody(
        semitones: List<Int>,
        transposeSemitones: Int = 0,
        notes: Int = semitones.size,
    ): FloatArray =
        SyntheticSignals.join(
            *semitones
                .take(notes)
                .map { st ->
                    val hz = BASE_HZ * Math.pow(2.0, (st + transposeSemitones) / 12.0)
                    SyntheticSignals.voiceLike(hz, SyntheticSignals.frames(FRAMES_PER_NOTE))
                }.toTypedArray(),
        )

    private fun obj(vararg pairs: Pair<String, Any?>) = pairs.toMap()

    /** Writes the corpus under [root] and loads it. */
    fun build(root: File): Corpus {
        val gson = Gson()

        fun wav(
            name: String,
            samples: FloatArray,
        ) = "recordings/$name.wav".also { Wav.write(File(root, it), samples) }

        val library =
            listOf(
                obj("id" to "ocean", "title" to "Ocean", "language" to "en-us", "lyrics" to "ocean river mountain valley thunder", "reference" to wav("ref-ocean", melody(melodyA))),
                obj("id" to "garden", "title" to "Garden", "language" to "en-us", "lyrics" to "garden window candle evening lantern", "reference" to wav("ref-garden", melody(melodyB))),
                obj("id" to "forest", "title" to "Forest", "language" to "en-us", "lyrics" to "forest meadow whisper sunrise shadow", "reference" to wav("ref-forest", melody(melodyC))),
            )

        fun query(
            id: String,
            song: String?,
            samples: FloatArray,
            condition: String,
            heard: List<Pair<Int, String>>?,
        ): Map<String, Any?> {
            if (heard != null) {
                File(root, "transcripts").mkdirs()
                File(root, "transcripts/$id.json").writeText(gson.toJson(heard.map { obj("frame" to it.first, "text" to it.second) }))
            }
            return obj(
                "id" to id,
                "file" to wav(id, samples),
                "song" to song,
                "mode" to "sing",
                "language" to "en-us",
                "condition" to condition,
                "device" to "synthetic",
                "speaker" to "s-$id",
                "speakerGroup" to "adult",
            )
        }

        val queries =
            listOf(
                // Right song, a different key, three right words: found by both paths.
                query("q1", "ocean", melody(melodyA, transposeSemitones = 4), "quiet", listOf(30 to "ocean river mountain")),
                // Right song, same key, only one right word: the words are not enough, the pitch is.
                query("q2", "garden", melody(melodyB), "quiet", listOf(25 to "garden")),
                // Not in the library, nothing matches: correctly nothing found.
                query("q3", null, melody(melodyD), "quiet", listOf(25 to "pencil shoulder")),
                // Right song but only 3 seconds: the app does not look at pitch before 5 seconds.
                query("q4", "forest", melody(melodyC, notes = 6), "street", listOf(20 to "forest meadow")),
                // Not in the library, but two heard words happen to be in one song's lyrics: a false accept.
                query("q5", null, melody(melodyD, transposeSemitones = 2), "street", listOf(30 to "ocean thunder")),
            )

        File(root, "library.json").writeText(gson.toJson(library))
        File(root, "queries.json").writeText(gson.toJson(queries))
        return Corpus.load(root)
    }
}
