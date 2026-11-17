package dev.sriramcodessw.versesync.eval

import com.google.gson.GsonBuilder
import java.io.File
import kotlin.random.Random

/*
 * Builds an evaluation corpus from recordings of read speech in Google FLEURS (CC BY 4.0), with a fixed
 * random seed so that the same input gives the same corpus, byte for byte.
 *
 * Compared with the seed corpus (tools/audio-test/make_seed_corpus.ps1) it has several songs per language,
 * so songs share common words, many more recordings of songs that are not in the library, and snippets of
 * 3, 5 and 10 seconds from random starts. It is still read speech, not singing.
 *
 * Input, per language code: <fleurs>/<code>/<split>.tsv and <fleurs>/<code>/wav/ holding the .wav files (tools/audio-test/fetch_fleurs_more.sh).
 *
 * How a library is made, per language:
 * - the recordings are shuffled, and about a third go to the library: their sentences become "songs" of four
 *   sentences each, and the library's lyrics are those sentences;
 * - the stored pitch contour of a song is made from the recordings of its sentences, as when a user syncs;
 * - a sentence that was read by two speakers gives a recording by another speaker than the reference, which is
 *   the only way to test pitch on a song that is in the library (the others are the reference itself);
 * - the other recordings are of sentences that are not in any song;
 * - more songs are made from sentences that have no recording, with no pitch contour, so the library is larger
 *   than the recordings that are heard.
 */
internal object FleursCorpusBuilder {
    const val SENTENCES_PER_SONG = 4
    const val LIBRARY_SHARE = 0.35
    val snippetSeconds = listOf(3, 5, 10)

    private class Clip(
        val file: File,
        val sentence: String,
        val gender: String,
    )

    fun build(
        fleurs: File,
        out: File,
        codes: List<String>,
        seed: Long = 43L,
        /** If set, exactly this many songs per language have recordings, so libraries are the same size in every corpus. */
        recordedSongs: Int? = null,
        /** If set, exactly this many songs per language have no recording. Otherwise twice as many as recorded sentences allow. */
        distractorSongs: Int? = null,
    ): Corpus {
        val recordings = File(out, "recordings").also { it.mkdirs() }
        val library = mutableListOf<Map<String, Any?>>()
        val queries = mutableListOf<Map<String, Any?>>()

        for (code in codes.sorted()) {
            val rng = Random(seed * 31 + code.hashCode())
            val sentences = HashMap<String, Pair<String, String>>() // file name -> sentence, gender
            val tsv = File(fleurs, code).listFiles { f -> f.extension == "tsv" }?.minByOrNull { it.name } ?: error("no .tsv list in $fleurs/$code")
            tsv.readLines(Charsets.UTF_8).forEach { line ->
                val cols = line.split('\t')
                if (cols.size >= 7) sentences[cols[1]] = cols[2].trim() to cols[6].trim()
            }
            val clips =
                (File(fleurs, "$code/wav").listFiles { f -> f.extension == "wav" } ?: emptyArray())
                    .sortedBy { it.name }
                    .mapNotNull { f -> sentences[f.name]?.let { Clip(f, it.first, it.second) } }
                    .shuffled(rng)
            if (clips.isEmpty()) continue

            // Recordings of the library's sentences first, then those of every other sentence.
            val inLibrary = clips.take((clips.size * LIBRARY_SHARE).toInt().coerceAtLeast(1)).toMutableList()
            val librarySentences = inLibrary.map { it.sentence }.toSet()
            val rest = clips.drop(inLibrary.size)
            // A recording of a library sentence is in the library, wherever the shuffle put it.
            val (extra, outside) = rest.partition { it.sentence in librarySentences }
            inLibrary += extra

            // One reference recording per sentence: the first by file name. The others are by another speaker.
            val byName = inLibrary.sortedBy { it.file.name }
            val referenceOf = LinkedHashMap<String, Clip>()
            byName.forEach { referenceOf.putIfAbsent(it.sentence, it) }
            val songSentences =
                referenceOf.keys
                    .sorted()
                    .let { if (recordedSongs != null) it.shuffled(rng) else it }
                    .chunked(SENTENCES_PER_SONG)
                    .let { if (recordedSongs != null) it.take(recordedSongs) else it }

            val songIdBySentence = HashMap<String, String>()
            songSentences.forEachIndexed { n, group ->
                val id = "$code-s$n"
                group.forEach { songIdBySentence[it] = id }
                val reference = "recordings/ref-$id.wav"
                val audio = group.map { Wav.read16k(referenceOf.getValue(it).file) }
                Wav.write(File(out, reference), audio.fold(FloatArray(0)) { acc, part -> acc + part })
                library += mapOf("id" to id, "title" to id, "language" to code, "lyrics" to group.joinToString(" "), "reference" to reference)
            }

            // Songs whose sentences were never recorded here: they can only be found by the words.
            val recorded = clips.map { it.sentence }.toSet()
            val unrecorded =
                sentences.values
                    .map { it.first }
                    .distinct()
                    .sorted()
                    .filter { it !in recorded }
                    .shuffled(rng)
            unrecorded.take(if (distractorSongs != null) distractorSongs * SENTENCES_PER_SONG else referenceOf.size * 2).chunked(SENTENCES_PER_SONG).forEachIndexed { n, group ->
                library += mapOf("id" to "$code-d$n", "title" to "$code-d$n", "language" to code, "lyrics" to group.joinToString(" "), "reference" to null)
            }

            var n = 0

            fun query(
                clip: Clip,
                song: String?,
                condition: String,
            ) {
                val samples = Wav.read16k(clip.file)
                val seconds = snippetSeconds[rng.nextInt(snippetSeconds.size)]
                val length = seconds * Wav.TARGET_RATE
                val from = if (samples.size <= length) 0 else rng.nextInt(samples.size - length + 1)
                val snippet = samples.copyOfRange(from, minOf(samples.size, from + length))
                val name = "recordings/q-$code-${n++}.wav"
                Wav.write(File(out, name), snippet)
                queries +=
                    mapOf(
                        "id" to "q-$code-${n - 1}",
                        "file" to name,
                        "song" to song,
                        "mode" to "speak",
                        "language" to code,
                        "condition" to condition,
                        "device" to "fleurs",
                        "speaker" to null,
                        "speakerGroup" to clip.gender.lowercase(),
                        "snippet" to seconds,
                    )
            }

            inLibrary.sortedBy { it.file.name }.forEach { clip ->
                val isReference = referenceOf[clip.sentence] === clip
                // With a fixed number of songs, some library sentences are in no song, and their recordings are of songs not in the library.
                val song = songIdBySentence[clip.sentence]
                if (song == null) query(clip, null, "read speech") else query(clip, song, if (isReference) "same recording as reference" else "other speaker")
            }
            outside.sortedBy { it.file.name }.forEach { clip -> query(clip, null, "read speech") }
        }

        val meta =
            mapOf(
                "name" to "FLEURS same-language corpus (Google FLEURS, CC BY 4.0), seed $seed",
                "caution" to
                    "READ SPEECH, NOT SINGING. Several songs per language, snippets of 3, 5 and 10 seconds. " +
                    "A recording that is the reference itself matches its own pitch contour, so pitch numbers for those " +
                    "mean nothing; only recordings by another speaker than the reference test pitch on a song in the library.",
            )
        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        File(out, "library.json").writeText(gson.toJson(library), Charsets.UTF_8)
        File(out, "queries.json").writeText(gson.toJson(queries), Charsets.UTF_8)
        File(out, "corpus.json").writeText(gson.toJson(meta), Charsets.UTF_8)
        return Corpus.load(out)
    }
}
