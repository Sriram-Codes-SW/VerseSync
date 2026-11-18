package dev.sriramcodessw.versesync.data

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TC-U-64: what a free person may add (ADR-20, decision 3): songs outside every setlist, at most 10; Pro has no limit. */
class FreeLimitTest {
    @Test
    fun aFreePersonMayAddWhileUnderTheLimit() {
        assertTrue(FreeLimit.canAddLooseSong(isPro = false, looseSongs = 0))
        assertTrue(FreeLimit.canAddLooseSong(isPro = false, looseSongs = 9))
    }

    @Test
    fun aFreePersonMayNotAddAtOrOverTheLimit() {
        assertFalse(FreeLimit.canAddLooseSong(isPro = false, looseSongs = 10))
        assertFalse(FreeLimit.canAddLooseSong(isPro = false, looseSongs = 11))
        assertFalse(FreeLimit.canAddLooseSong(isPro = false, looseSongs = 300))
    }

    @Test
    fun proHasNoLimit() {
        assertTrue(FreeLimit.canAddLooseSong(isPro = true, looseSongs = 10))
        assertTrue(FreeLimit.canAddLooseSong(isPro = true, looseSongs = 10_000))
    }

    @Test
    fun theLimitIsTen() {
        assertEquals(10, FreeLimit.MAX_LOOSE_SONGS)
    }
}
