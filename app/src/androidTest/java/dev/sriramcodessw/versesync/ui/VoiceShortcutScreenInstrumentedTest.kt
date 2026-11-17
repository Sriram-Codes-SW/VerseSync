package dev.sriramcodessw.versesync.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sriramcodessw.versesync.data.Song
import dev.sriramcodessw.versesync.engine.HybridRecognitionViewModel
import dev.sriramcodessw.versesync.ui.screens.LockCopy
import dev.sriramcodessw.versesync.ui.screens.VocalLockScreen
import dev.sriramcodessw.versesync.ui.screens.VoiceShortcutAttempts
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * TC-M-23 (screen part, issue #60): the voice shortcut screen reports what is tapped, the device lock button reports
 * once and not as an attempt, and listening times out only after the limit. It runs on a device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class VoiceShortcutScreenInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val song = Song(id = 1, title = "Target", artist = "test", lyrics = "ocean river mountain valley")

    private class Taps {
        val mic = AtomicInteger()
        val matched = AtomicInteger()
        val timedOut = AtomicInteger()
        val fallback = AtomicInteger()
    }

    private fun show(
        listening: Boolean,
        taps: Taps,
    ) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as dev.sriramcodessw.versesync.VerseSyncApp
        val viewModel = HybridRecognitionViewModel(app)
        val isListening = mutableStateOf(listening)
        compose.setContent {
            VocalLockScreen(
                targetSong = song,
                hybridViewModel = viewModel,
                isListening = isListening.value,
                tryNumber = 1,
                onMicTapped = { taps.mic.incrementAndGet() },
                onMatched = { taps.matched.incrementAndGet() },
                onTimedOut = { taps.timedOut.incrementAndGet() },
                onFallback = { taps.fallback.incrementAndGet() },
            )
        }
    }

    @Test
    fun theDeviceLockButton_reportsOnce_andTheMicrophoneButtonReportsSeparately() {
        val taps = Taps()
        show(listening = false, taps = taps)

        compose.onNodeWithText(LockCopy.FALLBACK_BUTTON).performClick()
        assertEquals(1, taps.fallback.get())
        assertEquals(0, taps.mic.get())

        compose.onNodeWithContentDescription("Listen").performClick()
        assertEquals(1, taps.mic.get())
        assertEquals(1, taps.fallback.get())
    }

    @Test
    fun theScreenShowsTheTryAndTheCopy() {
        show(listening = false, taps = Taps())

        compose.onNodeWithText(LockCopy.tryLabel(1, 2)).assertExists()
        compose.onNodeWithText(LockCopy.PROMPT).assertExists()
        compose.onNodeWithText(LockCopy.FALLBACK_BUTTON).assertExists()
    }

    @Test
    fun listening_timesOutOnlyAfterTheLimit() {
        val taps = Taps()
        compose.mainClock.autoAdvance = false
        show(listening = true, taps = taps)

        compose.mainClock.advanceTimeBy(VoiceShortcutAttempts.LISTEN_MS - 1_000)
        assertEquals("timed out before the limit", 0, taps.timedOut.get())

        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(1, taps.timedOut.get())
    }

    @Test
    fun notListening_neverTimesOut() {
        val taps = Taps()
        compose.mainClock.autoAdvance = false
        show(listening = false, taps = taps)

        compose.mainClock.advanceTimeBy(VoiceShortcutAttempts.LISTEN_MS * 2)
        assertEquals(0, taps.timedOut.get())
    }
}
