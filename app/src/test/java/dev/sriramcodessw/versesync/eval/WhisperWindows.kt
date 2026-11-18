package dev.sriramcodessw.versesync.eval

import dev.sriramcodessw.versesync.engine.spokenMatchTokens
import kotlin.math.roundToInt

/*
 * Turns the windows of a simulated live Whisper session into the transcript the replay reads (docs/09 section 17).
 * The rules are fixed there before any run:
 *   - one entry per decoded window: the window's whole text as one string, never blank, frame = the window's end / 64 ms;
 *   - a window that was too quiet to decode gives no entry;
 *   - a window whose text holds one sequence of 3 words 3 or more times is dropped (the loop filter).
 */
internal object WhisperWindows {
    /** One window as the runner wrote it: [json] is the program's output, or null when the window was too quiet to decode. */
    class Window(
        val endMs: Long,
        val json: String?,
    )

    class Converted(
        val heard: List<HeardText>,
        val silent: Int,
        val loops: Int,
        val blank: Int,
    )

    fun convert(windows: List<Window>): Converted {
        var silent = 0
        var loops = 0
        var blank = 0
        val heard = ArrayList<HeardText>()
        for (window in windows.sortedBy { it.endMs }) {
            if (window.json == null) {
                silent++
                continue
            }
            val text = WhisperTranscript.toHeard(window.json).joinToString(" ") { it.text }.trim()
            if (text.isEmpty()) {
                blank++
                continue
            }
            if (hasLoop(text)) {
                loops++
                continue
            }
            heard += HeardText(frame = (window.endMs / Replay.FRAME_MS.toDouble()).roundToInt(), text = text, final = true)
        }
        return Converted(heard, silent, loops, blank)
    }

    /** One sequence of 3 heard words (the app's own word rules) appearing 3 or more times in [text]. */
    fun hasLoop(text: String): Boolean {
        val tokens = spokenMatchTokens(text)
        val triples = HashMap<List<String>, Int>()
        for (i in 0..tokens.size - 3) {
            if (triples.merge(tokens.subList(i, i + 3), 1, Int::plus)!! >= 3) return true
        }
        return false
    }
}
