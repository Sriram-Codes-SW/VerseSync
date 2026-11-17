package dev.sriramcodessw.versesync.ui.screens

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VocalLockPolicyTest {
    @Test
    fun theDeviceLockTakesOverAfterTwoFailedAttempts() {
        assertFalse(VocalLockPolicy.fallsBackToDeviceLock(0))
        assertFalse(VocalLockPolicy.fallsBackToDeviceLock(1))
        assertTrue(VocalLockPolicy.fallsBackToDeviceLock(2))
        assertTrue(VocalLockPolicy.fallsBackToDeviceLock(3))
    }

    @Test
    fun theScreenShowsTheSameLimitAsThePolicy() {
        assertEquals(2, VocalLockPolicy.MAX_ATTEMPTS)
    }
}
