package dev.sriramcodessw.versesync.concert

/** One place in the setlist being played: the entry (its own id, since a song may appear twice) and its song. */
data class ConcertEntry(
    val entryId: Int,
    val songId: Int,
)

/** What the concert screen shows, derived from [ConcertController]. */
sealed interface ConcertShown {
    /** The setlist has no entries. */
    data object Empty : ConcertShown

    /** The screen is blanked by the person. */
    data object Blank : ConcertShown

    /** A song of the setlist; [index] counts from 0. */
    data class InSetlist(
        val entry: ConcertEntry,
        val index: Int,
        val total: Int,
    ) : ConcertShown

    /** A library song that is not part of the setlist, shown without changing the setlist. */
    data class OffList(
        val songId: Int,
    ) : ConcertShown
}

/**
 * Where the person is in a setlist during a show (ADR-20). It is pure: it holds no audio, no
 * network and no time, and every move returns a new controller, in the style of
 * `VoiceShortcutAttempts`.
 *
 * The place is anchored on the entry id, not on a position number, because songs repeat, entries
 * are reordered, and an entry can be deleted while the screen is open.
 *
 * - "Next" and "previous" stop at the ends and do nothing past them.
 * - A blank screen is shown until the person moves on or blanks again; it is never saved.
 * - An off-list song is shown over the setlist, which keeps its place. "Previous" returns to that
 *   place, and "next" goes to the song after it, or back to it when it was the last.
 */
data class ConcertController(
    val entries: List<ConcertEntry> = emptyList(),
    /** The entry the setlist is at, even while an off-list song or the blank screen is shown. */
    val anchorEntryId: Int? = null,
    val offListSongId: Int? = null,
    val blank: Boolean = false,
) {
    /** Where the anchor entry is, or 0 for an empty list. */
    val index: Int
        get() = entries.indexOfFirst { it.entryId == anchorEntryId }.coerceAtLeast(0)

    val shown: ConcertShown
        get() =
            when {
                entries.isEmpty() && offListSongId == null -> ConcertShown.Empty
                blank -> ConcertShown.Blank
                offListSongId != null -> ConcertShown.OffList(offListSongId)
                else -> ConcertShown.InSetlist(entries[index], index, entries.size)
            }

    val hasNext: Boolean get() = entries.isNotEmpty() && (offListSongId != null || index < entries.lastIndex)

    val hasPrevious: Boolean get() = entries.isNotEmpty() && (offListSongId != null || index > 0)

    fun next(): ConcertController =
        when {
            entries.isEmpty() -> this

            // Off the list, go to the song after the place, or back to the place when it was the last.
            offListSongId != null -> goTo(minOf(index + 1, entries.lastIndex))

            index < entries.lastIndex -> goTo(index + 1)

            // At the last song, "next" does nothing, but it still brings the screen back from blank.
            blank -> copy(blank = false)

            else -> this
        }

    fun previous(): ConcertController =
        when {
            entries.isEmpty() -> this
            offListSongId != null -> goTo(index)
            index > 0 -> goTo(index - 1)
            blank -> copy(blank = false)
            else -> this
        }

    /** Jumps to an entry by its position. A position outside the list is ignored. */
    fun jumpTo(position: Int): ConcertController = if (position in entries.indices) goTo(position) else this

    fun toggleBlank(): ConcertController = copy(blank = !blank)

    /** Shows any library song without changing the setlist. */
    fun showOffList(songId: Int): ConcertController = copy(offListSongId = songId, blank = false)

    /** Leaves an off-list song and shows the setlist at its place again. */
    fun backToSetlist(): ConcertController = copy(offListSongId = null, blank = false)

    /**
     * Takes a new list of entries, as when the setlist is edited or loaded, and keeps the place:
     * the same entry if it is still there, else the same position clamped to the new list, else
     * nothing for an empty list.
     */
    fun withEntries(newEntries: List<ConcertEntry>): ConcertController {
        val anchor =
            when {
                newEntries.isEmpty() -> null
                newEntries.any { it.entryId == anchorEntryId } -> anchorEntryId
                else -> newEntries[index.coerceAtMost(newEntries.lastIndex)].entryId
            }
        return copy(entries = newEntries, anchorEntryId = anchor)
    }

    /**
     * Puts the place back from what was saved. The saved entry is used if it still exists, else the
     * saved position clamped to the list. The blank screen and an off-list song are not restored.
     */
    fun restore(
        savedEntryId: Int?,
        savedIndex: Int,
    ): ConcertController {
        val target =
            when {
                entries.isEmpty() -> null
                entries.any { it.entryId == savedEntryId } -> savedEntryId
                else -> entries[savedIndex.coerceIn(0, entries.lastIndex)].entryId
            }
        return copy(anchorEntryId = target, offListSongId = null, blank = false)
    }

    private fun goTo(position: Int): ConcertController = copy(anchorEntryId = entries[position].entryId, offListSongId = null, blank = false)
}
