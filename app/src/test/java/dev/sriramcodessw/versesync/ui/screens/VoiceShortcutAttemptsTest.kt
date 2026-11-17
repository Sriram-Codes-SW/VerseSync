package dev.sriramcodessw.versesync.ui.screens

import androidx.compose.runtime.saveable.SaverScope
import dev.sriramcodessw.versesync.ui.screens.VoiceShortcutAttempts.Effect
import dev.sriramcodessw.versesync.ui.screens.VoiceShortcutAttempts.State
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TC-U-48: what ends an attempt, and when the device lock opens (issue #60, ADR-10). */
class VoiceShortcutAttemptsTest {
    private val start = VoiceShortcutAttempts()

    private fun listening() = start.micTapped().next

    @Test
    fun theFirstTapOfTheDeviceLockButton_opensTheDeviceLockAtOnce_andIsNotAnAttempt() {
        val step = start.fallbackTapped()

        assertEquals(listOf(Effect.PromptDevice), step.effects)
        assertEquals(State.DeviceLock, step.next.state)
        assertEquals(0, step.next.failed)
    }

    @Test
    fun theDeviceLockButtonWhileListening_stopsTheMicrophoneAndOpensTheDeviceLock() {
        val step = listening().fallbackTapped()

        assertEquals(listOf(Effect.StopListening, Effect.PromptDevice), step.effects)
        assertEquals(0, step.next.failed)
    }

    @Test
    fun tappingTheMicrophone_startsListening() {
        val step = start.micTapped()
        assertEquals(listOf(Effect.StartListening), step.effects)
        assertEquals(State.Listening, step.next.state)
    }

    @Test
    fun anAttemptThatEndsWithoutOpening_countsAsFailed_whetherTheTapOrTheTimeoutEndedIt() {
        val byTap = listening().micTapped()
        val byTimeout = listening().timedOut()

        assertEquals(listOf(Effect.StopListening), byTap.effects)
        assertEquals(listOf(Effect.StopListening), byTimeout.effects)
        assertEquals(VoiceShortcutAttempts(State.Idle, 1), byTap.next)
        assertEquals(byTap.next, byTimeout.next)
        assertEquals(2, byTap.next.tryNumber)
    }

    @Test
    fun theSecondFailedAttempt_opensTheDeviceLock_asTheLimitOfTheShortcutSays() {
        val second =
            listening()
                .timedOut()
                .next
                .micTapped()
                .next
                .timedOut()

        assertEquals(listOf(Effect.StopListening, Effect.PromptDevice), second.effects)
        assertEquals(VoiceShortcutAttempts(State.DeviceLock, VocalLockPolicy.MAX_ATTEMPTS), second.next)
    }

    @Test
    fun aMatchWhileListening_opens_andStopsTheMicrophone() {
        val step = listening().matched()

        assertEquals(State.Open, step.next.state)
        assertEquals(listOf(Effect.StopListening), step.effects)
    }

    @Test
    fun aMatchWhenNotListening_isIgnored_becauseTheRecognizerGivesALastResultAfterAStop() {
        listOf(start, listening().timedOut().next, start.fallbackTapped().next).forEach { notListening ->
            val step = notListening.matched()
            assertEquals(notListening, step.next)
            assertTrue(step.effects.isEmpty())
        }
    }

    @Test
    fun everyWayOutOfListening_stopsTheMicrophone() {
        val ways = listOf(listening().micTapped(), listening().timedOut(), listening().matched(), listening().fallbackTapped())
        ways.forEach { assertTrue(Effect.StopListening in it.effects, "$it") }
    }

    @Test
    fun nothingRestartsListeningFromTheDeviceLockOrOpen() {
        listOf(start.fallbackTapped().next, listening().matched().next).forEach { done ->
            assertEquals(done, done.micTapped().next)
            assertEquals(done, done.timedOut().next)
            assertEquals(done, done.fallbackTapped().next)
            assertFalse(Effect.StartListening in done.micTapped().effects)
        }
    }

    @Test
    fun failedAttempts_neverGoPastTheLimit() {
        var state = start
        repeat(6) {
            state =
                state
                    .micTapped()
                    .next
                    .timedOut()
                    .next
        }
        assertEquals(VocalLockPolicy.MAX_ATTEMPTS, state.failed)
    }

    @Test
    fun aTimedOutAttemptWhileNotListening_isIgnored() {
        val step = start.timedOut()
        assertEquals(start, step.next)
        assertTrue(step.effects.isEmpty())
    }

    @Test
    fun savedAcrossARotation_theCountAndTheDeviceLockStay_butListeningAndOpenStartAgainFromIdle() {
        fun roundTrip(value: VoiceShortcutAttempts): VoiceShortcutAttempts {
            val saved = with(VoiceShortcutAttempts.Saver) { SaverScope { true }.save(value) }!!
            return VoiceShortcutAttempts.Saver.restore(saved)!!
        }

        assertEquals(VoiceShortcutAttempts(State.Idle, 1), roundTrip(VoiceShortcutAttempts(State.Idle, 1)))
        assertEquals(VoiceShortcutAttempts(State.DeviceLock, 2), roundTrip(VoiceShortcutAttempts(State.DeviceLock, 2)))
        assertEquals(VoiceShortcutAttempts(State.Idle, 1), roundTrip(VoiceShortcutAttempts(State.Listening, 1)))
        assertEquals(VoiceShortcutAttempts(State.Idle, 0), roundTrip(VoiceShortcutAttempts(State.Open, 0)))
    }

    @Test
    fun anAttemptListensFiniteTime_longEnoughForTheMeasuredRules() {
        // The proposed rule acted at a median of 2.6 s and a 90th percentile of 4.0 s on read speech (docs/09, section 12).
        assertTrue(VoiceShortcutAttempts.LISTEN_MS in 8_000L..15_000L)
    }

    @Test
    fun goingToTheBackgroundWhileListening_stopsTheMicrophone_andIsNotAFailedAttempt() {
        val step = listening().interrupted()

        assertEquals(listOf(Effect.StopListening), step.effects)
        assertEquals(State.Idle, step.next.state)
        assertEquals(0, step.next.failed)
    }

    @Test
    fun goingToTheBackgroundKeepsEarlierFailedAttempts() {
        val afterOneFailure = listening().timedOut().next

        val step = afterOneFailure.micTapped().next.interrupted()

        assertEquals(1, step.next.failed)
        assertEquals(State.Idle, step.next.state)
    }

    @Test
    fun goingToTheBackgroundWhileNotListeningChangesNothing() {
        val idle = start.interrupted()
        val deviceLock = start.fallbackTapped().next.interrupted()
        val open = listening().matched().next.interrupted()

        assertEquals(emptyList<Effect>(), idle.effects)
        assertEquals(start, idle.next)
        assertEquals(emptyList<Effect>(), deviceLock.effects)
        assertEquals(State.DeviceLock, deviceLock.next.state)
        assertEquals(emptyList<Effect>(), open.effects)
        assertEquals(State.Open, open.next.state)
    }
}
