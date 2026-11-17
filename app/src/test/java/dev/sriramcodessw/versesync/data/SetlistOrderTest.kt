package dev.sriramcodessw.versesync.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** TC-U-55: pure setlist moves (ADR-20). */
class SetlistOrderTest {
    private val ids = listOf(10, 20, 30, 40)

    @Test
    fun movesAnEntryDownAndUp() {
        assertEquals(listOf(20, 10, 30, 40), SetlistOrder.move(ids, from = 0, to = 1))
        assertEquals(listOf(10, 30, 20, 40), SetlistOrder.move(ids, from = 2, to = 1))
    }

    @Test
    fun movesToTheEnds() {
        assertEquals(listOf(20, 30, 40, 10), SetlistOrder.move(ids, from = 0, to = 3))
        assertEquals(listOf(40, 10, 20, 30), SetlistOrder.move(ids, from = 3, to = 0))
    }

    @Test
    fun aTargetOutsideTheListIsClampedToTheNearestEnd() {
        assertEquals(listOf(20, 30, 40, 10), SetlistOrder.move(ids, from = 0, to = 99))
        assertEquals(listOf(30, 10, 20, 40), SetlistOrder.move(ids, from = 2, to = -5))
    }

    @Test
    fun movingToTheSamePlaceOrFromOutsideTheListChangesNothing() {
        assertSame(ids, SetlistOrder.move(ids, from = 1, to = 1))
        assertSame(ids, SetlistOrder.move(ids, from = -1, to = 2))
        assertSame(ids, SetlistOrder.move(ids, from = 4, to = 0))
        assertSame(ids, SetlistOrder.move(ids, from = 0, to = 0))
    }

    @Test
    fun aMoveAtTheEndOfTheListDoesNothing() {
        assertSame(ids, SetlistOrder.move(ids, from = 3, to = 4))
        assertSame(ids, SetlistOrder.move(ids, from = 0, to = -1))
    }

    @Test
    fun anEmptyOrSingleEntryListIsUnchanged() {
        val empty = emptyList<Int>()
        val one = listOf(7)

        assertSame(empty, SetlistOrder.move(empty, 0, 0))
        assertSame(one, SetlistOrder.move(one, 0, 3))
    }

    @Test
    fun theOriginalListIsNotChanged() {
        SetlistOrder.move(ids, from = 0, to = 3)

        assertEquals(listOf(10, 20, 30, 40), ids)
    }
}
