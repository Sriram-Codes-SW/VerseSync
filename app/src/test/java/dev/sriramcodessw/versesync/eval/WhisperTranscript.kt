package dev.sriramcodessw.versesync.eval

import com.google.gson.JsonParser
import dev.sriramcodessw.versesync.engine.spokenMatchTokens
import kotlin.math.roundToInt

/*
 * Turns the output of a Whisper-class recognizer into the transcript the replay reads, and counts what it heard.
 * The rules are fixed in docs/09-evaluation-harness.md section 16 before any run, and nothing here is tuned:
 *
 *   - one transcript entry per segment, exactly as the recognizer emitted it (no merging, no splitting);
 *   - frame = the segment's END time in milliseconds divided by 64, rounded, because the text only exists once the
 *     segment has been decoded (the start time would flatter the time to a decision; timing is not measured in stage 1);
 *   - final = true, text kept as written except for trimming, segments with no text dropped.
 */
internal object WhisperTranscript {
    /** Reads the file written by `whisper-cli -oj` (field `transcription[].offsets.to` in milliseconds, and `text`). */
    fun toHeard(json: String): List<HeardText> {
        val segments = JsonParser.parseString(json).asJsonObject.getAsJsonArray("transcription") ?: return emptyList()
        return segments.mapNotNull { element ->
            val segment = element.asJsonObject
            val text =
                segment
                    .get("text")
                    ?.asString
                    ?.trim()
                    .orEmpty()
            if (text.isEmpty()) return@mapNotNull null
            val endMs = segment.getAsJsonObject("offsets").get("to").asLong
            HeardText(frame = (endMs / Replay.FRAME_MS.toDouble()).roundToInt(), text = text, final = true)
        }
    }

    /** What was heard in one recording, as counts. [lyricWords] is null for a song that is not in the library. */
    class Counts(
        val entries: Int,
        val words: Int,
        val distinctHeard: Int,
        val distinctInLyrics: Int?,
        val loopSuspected: Boolean,
    )

    /**
     * Counts with the same tokens the app matches on ([spokenMatchTokens]). A loop is suspected when one sequence of 3
     * heard words appears 3 or more times, which a repeated chorus also does; it is a flag to look at, not a filter.
     */
    fun count(
        heard: List<HeardText>,
        lyricWords: Set<String>?,
    ): Counts {
        val perEntry = heard.sortedBy { it.frame }.map { spokenMatchTokens(it.text) }
        val all = perEntry.flatten()
        val distinct = all.toSet()
        val triples = HashMap<List<String>, Int>()
        for (tokens in perEntry) {
            for (i in 0..tokens.size - 3) triples.merge(tokens.subList(i, i + 3), 1, Int::plus)
        }
        return Counts(
            entries = heard.size,
            words = all.size,
            distinctHeard = distinct.size,
            distinctInLyrics = lyricWords?.let { words -> distinct.count { it in words } },
            loopSuspected = triples.values.any { it >= 3 },
        )
    }
}
