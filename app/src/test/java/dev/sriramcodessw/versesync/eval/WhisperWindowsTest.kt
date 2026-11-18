package dev.sriramcodessw.versesync.eval

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TC-U-60: the conversion of simulated live windows into a transcript, with the silence and loop filters (docs/09 section 17). */
class WhisperWindowsTest {
    private fun json(vararg text: String): String {
        val body = text.joinToString(",") { """{"offsets":{"from":0,"to":1000},"text":"$it"}""" }
        return """{"transcription":[$body]}"""
    }

    @Test
    fun aWindowBecomesOneEntryWithAllItsSegmentsJoined_atItsEndFrame() {
        val out = WhisperWindows.convert(listOf(WhisperWindows.Window(10_240, json(" first part", " second part"))))
        assertEquals(1, out.heard.size)
        assertEquals("first part second part", out.heard[0].text)
        assertEquals(160, out.heard[0].frame) // 10240 / 64
        assertTrue(out.heard[0].final)
    }

    @Test
    fun aWindowThatWasTooQuietGivesNoEntry() {
        val out = WhisperWindows.convert(listOf(WhisperWindows.Window(5_000, null), WhisperWindows.Window(10_000, json("some words here"))))
        assertEquals(1, out.heard.size)
        assertEquals(1, out.silent)
    }

    @Test
    fun blankTextGivesNoEntry() {
        val out = WhisperWindows.convert(listOf(WhisperWindows.Window(5_000, json("  ")), WhisperWindows.Window(10_000, """{"transcription":[]}""")))
        assertEquals(0, out.heard.size)
        assertEquals(2, out.blank)
    }

    @Test
    fun aLoopedWindowIsDropped_andCounted() {
        val looped = json("love you tender love you tender love you tender")
        val twice = json("love you tender love you tender")
        val out = WhisperWindows.convert(listOf(WhisperWindows.Window(5_000, looped), WhisperWindows.Window(10_000, twice)))
        assertEquals(1, out.loops)
        assertEquals(listOf(10_000L / 64), out.heard.map { it.frame.toLong() })
    }

    @Test
    fun windowsAreOrderedByTheirEnd() {
        val out =
            WhisperWindows.convert(
                listOf(WhisperWindows.Window(15_000, json("third window here")), WhisperWindows.Window(5_000, json("first window here"))),
            )
        assertEquals(listOf("first window here", "third window here"), out.heard.map { it.text })
    }

    @Test
    fun hasLoop_needsThreeRepeatsOfThreeWords() {
        assertTrue(WhisperWindows.hasLoop("one two three one two three one two three"))
        assertFalse(WhisperWindows.hasLoop("one two three one two three"))
        assertFalse(WhisperWindows.hasLoop("short"))
    }
}
