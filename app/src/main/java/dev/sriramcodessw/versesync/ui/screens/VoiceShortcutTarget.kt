package dev.sriramcodessw.versesync.ui.screens

import dev.sriramcodessw.versesync.data.Song

/**
 * The song a person is asked to say to open the voice shortcut: the song with [savedId] if it is still among [songs],
 * otherwise one picked by [pick]. The id is kept across a rotation, and across the song list changing, so the song a person
 * is looking at does not change under them (issue #63, ADR-10). [pick] is not called when the saved song is found.
 */
internal fun chooseVoiceShortcutTarget(
    songs: List<Song>,
    savedId: Int?,
    pick: (List<Song>) -> Song? = { it.randomOrNull() },
): Song? = songs.firstOrNull { it.id == savedId } ?: pick(songs)
