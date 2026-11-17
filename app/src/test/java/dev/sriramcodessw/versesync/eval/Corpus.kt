package dev.sriramcodessw.versesync.eval

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/*
 * The evaluation corpus: recordings of people singing or speaking, plus what is needed to score them.
 * Layout and the recording protocol are in docs/09-evaluation-harness.md.
 *
 *   <corpus>/library.json     the songs the app would hold
 *   <corpus>/queries.json     one entry per recording to recognize
 *   <corpus>/recordings/      the audio (16-bit PCM or 32-bit float WAV, any sample rate)
 *   <corpus>/transcripts/     optional: what the speech recognizer heard, captured on a device
 */

/** A song in the library. [reference] is a recording from which the stored fingerprint is made, as when a user syncs. */
class LibrarySong(
    val id: String,
    val title: String?,
    val language: String?,
    val lyrics: String?,
    val reference: String?,
) {
    val languageCode get() = language ?: "en-us"
    val lyricText get() = lyrics ?: ""
}

/**
 * One recording to recognize. [song] is the id of the song being sung or said, or null when it is
 * not in the library, which measures false accepts. The other fields only slice the report.
 */
class Query(
    val id: String,
    val file: String?,
    val song: String?,
    val mode: String?,
    val language: String?,
    val condition: String?,
    val device: String?,
    val speaker: String?,
    val speakerGroup: String?,
) {
    val modeName get() = mode ?: "unknown"
    val languageCode get() = language ?: "unknown"
    val conditionName get() = condition ?: "unknown"
    val deviceName get() = device ?: "unknown"
    val speakerGroupName get() = speakerGroup ?: "unknown"
}

/** What the speech recognizer reported at one point of a recording: the text, and the 64 ms frame it was reported at. */
class HeardText(
    val frame: Int,
    val text: String,
)

class Corpus(
    val root: File,
    val library: List<LibrarySong>,
    val queries: List<Query>,
    /** Optional text from corpus.json that the report prints at the top, such as a warning that this is only a seed. */
    val caution: String? = null,
) {
    fun recording(path: String): File = File(root, path)

    /** The transcript captured for [queryId], or null if none was captured. */
    fun transcript(queryId: String): List<HeardText>? {
        val file = File(root, "transcripts/$queryId.json")
        if (!file.isFile) return null
        return Gson().fromJson(file.readText(), object : TypeToken<List<HeardText>>() {}.type)
    }

    companion object {
        fun load(root: File): Corpus {
            val gson = Gson()
            val library: List<LibrarySong> =
                gson.fromJson(File(root, "library.json").readText(), object : TypeToken<List<LibrarySong>>() {}.type)
            val queries: List<Query> =
                gson.fromJson(File(root, "queries.json").readText(), object : TypeToken<List<Query>>() {}.type)
            val ids = library.map { it.id }
            require(ids.size == ids.toSet().size) { "library.json has duplicate song ids" }
            queries.forEach { q ->
                require(q.song == null || q.song in ids) { "Query ${q.id} names song ${q.song}, which is not in library.json" }
            }
            val meta = File(root, "corpus.json")
            val caution = if (meta.isFile) gson.fromJson(meta.readText(), com.google.gson.JsonObject::class.java).get("caution")?.asString else null
            return Corpus(root, library, queries, caution)
        }
    }
}
