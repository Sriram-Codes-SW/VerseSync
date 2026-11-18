package dev.sriramcodessw.versesync.ui.screens

import dev.sriramcodessw.versesync.data.Song
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** TC-U-63: the song to say for the voice shortcut stays the same across a rotation and a changing song list (issue #63). */
class VoiceShortcutTargetTest {
    private val songs = List(5) { Song(it + 1, "T${it + 1}", "A", "lyrics ${it + 1}") }

    @Test
    fun theSavedSongIsKept_andNothingIsPicked() {
        var picked = false
        val target =
            chooseVoiceShortcutTarget(songs, savedId = 3) {
                picked = true
                it.first()
            }
        assertEquals(3, target?.id)
        assertFalse(picked, "a saved song must not be replaced by a new pick")
    }

    @Test
    fun withNothingSaved_aSongIsPicked() {
        assertEquals(1, chooseVoiceShortcutTarget(songs, savedId = null) { it.first() }?.id)
    }

    @Test
    fun ifTheSavedSongIsGone_aNewOneIsPicked() {
        assertEquals(1, chooseVoiceShortcutTarget(songs, savedId = 99) { it.first() }?.id)
    }

    @Test
    fun theSavedSongSurvivesAChangeOfTheListThatKeepsIt() {
        val changed = songs.filter { it.id != 1 } + Song(6, "T6", "A", "lyrics 6")
        assertEquals(3, chooseVoiceShortcutTarget(changed, savedId = 3) { it.first() }?.id)
    }

    @Test
    fun noSongs_noTarget() {
        assertNull(chooseVoiceShortcutTarget(emptyList(), savedId = 3))
        assertNull(chooseVoiceShortcutTarget(emptyList(), savedId = null))
    }
}
