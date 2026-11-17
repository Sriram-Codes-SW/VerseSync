package dev.sriramcodessw.versesync.concert

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** TC-U-53: the concert controller (ADR-20). Pure logic, no audio and no Android. */
class ConcertControllerTest {
    private fun entry(
        id: Int,
        song: Int = id * 10,
    ) = ConcertEntry(entryId = id, songId = song)

    private val three = listOf(entry(1), entry(2), entry(3))

    private fun started(list: List<ConcertEntry> = three) = ConcertController().withEntries(list)

    private fun ConcertController.shownEntryId(): Int? = (shown as? ConcertShown.InSetlist)?.entry?.entryId

    @Test
    fun startsOnTheFirstSong() {
        val c = started()

        assertEquals(1, c.shownEntryId())
        assertEquals(ConcertShown.InSetlist(entry(1), 0, 3), c.shown)
        assertFalse(c.hasPrevious)
        assertTrue(c.hasNext)
    }

    @Test
    fun nextAndPreviousMoveOneSongAtATime() {
        val c = started().next()

        assertEquals(2, c.shownEntryId())
        assertEquals(1, c.previous().shownEntryId())
    }

    @Test
    fun nextOnTheLastSongAndPreviousOnTheFirstDoNothing() {
        val last = started().jumpTo(2)
        val first = started()

        assertSame(last, last.next())
        assertSame(first, first.previous())
        assertFalse(last.hasNext)
    }

    @Test
    fun jumpGoesToAnyPositionAndIgnoresOnesOutsideTheList() {
        val c = started()

        assertEquals(3, c.jumpTo(2).shownEntryId())
        assertSame(c, c.jumpTo(3))
        assertSame(c, c.jumpTo(-1))
    }

    @Test
    fun blankHidesTheSongAndComesBackOnAnyMove() {
        val blank = started().next().toggleBlank()

        assertEquals(ConcertShown.Blank, blank.shown)
        assertEquals(2, blank.toggleBlank().shownEntryId())
        assertEquals(3, blank.next().shownEntryId())
        assertEquals(1, blank.previous().shownEntryId())
        assertEquals(1, blank.jumpTo(0).shownEntryId())
    }

    @Test
    fun nextAtTheEndStillBringsTheScreenBackFromBlank() {
        val blankAtEnd = started().jumpTo(2).toggleBlank()

        assertEquals(3, blankAtEnd.next().shownEntryId())
    }

    @Test
    fun anOffListSongKeepsTheSetlistPlace() {
        val c = started().next().showOffList(songId = 999)

        assertEquals(ConcertShown.OffList(999), c.shown)
        assertEquals(1, c.index)
        assertEquals(2, c.backToSetlist().shownEntryId())
    }

    @Test
    fun nextFromAnOffListSongGoesToTheSongAfterThePlace() {
        val c = started().next().showOffList(999)

        assertEquals(3, c.next().shownEntryId())
    }

    @Test
    fun nextFromAnOffListSongAfterTheLastSongReturnsToTheLastSong() {
        val c = started().jumpTo(2).showOffList(999)

        assertEquals(3, c.next().shownEntryId())
    }

    @Test
    fun previousFromAnOffListSongReturnsToThePlace() {
        val c = started().next().showOffList(999)

        assertEquals(2, c.previous().shownEntryId())
    }

    @Test
    fun anOffListSongIsShownEvenWhenTheSetlistIsEmpty() {
        val c = started(emptyList()).showOffList(999)

        assertEquals(ConcertShown.OffList(999), c.shown)
        assertSame(c, c.next())
        assertSame(c, c.previous())
    }

    @Test
    fun anEmptySetlistShowsTheEmptyState() {
        val c = started(emptyList())

        assertEquals(ConcertShown.Empty, c.shown)
        assertFalse(c.hasNext)
        assertFalse(c.hasPrevious)
        assertSame(c, c.next())
        assertSame(c, c.jumpTo(0))
    }

    @Test
    fun theSamePlaceIsKeptWhenAnotherEntryIsDeleted() {
        val c = started().next().withEntries(listOf(entry(2), entry(3)))

        assertEquals(2, c.shownEntryId())
        assertEquals(0, c.index)
    }

    @Test
    fun deletingTheCurrentEntryMovesToTheSamePositionOrTheNewLast() {
        val middle = started().next().withEntries(listOf(entry(1), entry(3)))
        val last = started().jumpTo(2).withEntries(listOf(entry(1), entry(2)))

        assertEquals(3, middle.shownEntryId())
        assertEquals(2, last.shownEntryId())
    }

    @Test
    fun deletingEveryEntryEmptiesTheScreenAndAddingOneComesBack() {
        val empty = started().withEntries(emptyList())
        val back = empty.withEntries(listOf(entry(7)))

        assertEquals(ConcertShown.Empty, empty.shown)
        assertEquals(7, back.shownEntryId())
    }

    @Test
    fun theAnchorFollowsAnEntryWhenTheListIsReordered() {
        val c = started().next().withEntries(listOf(entry(3), entry(2), entry(1)))

        assertEquals(2, c.shownEntryId())
        assertEquals(1, c.index)
    }

    @Test
    fun aSongTwiceInTheListIsTwoPlacesAnchoredByEntryId() {
        val twice = listOf(entry(1, song = 5), entry(2, song = 6), entry(3, song = 5))
        val c = started(twice).jumpTo(2)

        assertEquals(3, c.shownEntryId())
        assertEquals(5, (c.shown as ConcertShown.InSetlist).entry.songId)
        // Deleting the first copy must not move the place to the other one.
        assertEquals(3, c.withEntries(listOf(entry(2, song = 6), entry(3, song = 5))).shownEntryId())
        assertEquals(1, c.previous().previous().shownEntryId())
    }

    @Test
    fun withEntriesKeepsAnOffListSongAndTheBlankFlag() {
        val c = started().next().showOffList(999).withEntries(listOf(entry(1), entry(2), entry(3), entry(4)))

        assertEquals(ConcertShown.OffList(999), c.shown)
        assertEquals(2, c.backToSetlist().shownEntryId())
    }

    @Test
    fun restoreUsesTheSavedEntryWhenItStillExists() {
        val c = ConcertController(entries = three).restore(savedEntryId = 3, savedIndex = 0)

        assertEquals(3, c.shownEntryId())
    }

    @Test
    fun restoreFallsBackToTheSavedPositionClampedWhenTheEntryIsGone() {
        val c = ConcertController(entries = listOf(entry(1), entry(2))).restore(savedEntryId = 9, savedIndex = 5)
        val low = ConcertController(entries = three).restore(savedEntryId = null, savedIndex = -4)

        assertEquals(2, c.shownEntryId())
        assertEquals(1, low.shownEntryId())
    }

    @Test
    fun restoreOnAnEmptyListLeavesNoPlace() {
        val c = ConcertController().restore(savedEntryId = 1, savedIndex = 0)

        assertEquals(null, c.anchorEntryId)
        assertEquals(ConcertShown.Empty, c.shown)
    }

    @Test
    fun restoreNeverRestoresTheBlankScreenOrAnOffListSong() {
        val c = started().showOffList(999).toggleBlank().restore(savedEntryId = 2, savedIndex = 1)

        assertEquals(2, c.shownEntryId())
        assertFalse(c.blank)
    }
}
