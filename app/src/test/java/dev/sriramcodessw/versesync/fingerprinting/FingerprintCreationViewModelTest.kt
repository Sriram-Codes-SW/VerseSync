package dev.sriramcodessw.versesync.fingerprinting

import android.app.Application
import dev.sriramcodessw.versesync.audio.AudioFormat
import dev.sriramcodessw.versesync.audio.FakeAudioSource
import dev.sriramcodessw.versesync.audio.FixedPitchDetector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FingerprintCreationViewModelTest {
    private lateinit var audioSource: FakeAudioSource
    private lateinit var detector: FixedPitchDetector
    private lateinit var viewModel: FingerprintCreationViewModel

    // A loud frame. Frames that are nearly silent never reach the pitch detector (issue #33).
    private val frame = FloatArray(AudioFormat.FRAME_SAMPLES) { if (it % 2 == 0) 0.3f else -0.3f }

    @BeforeEach
    fun setup() {
        audioSource = FakeAudioSource()
        detector = FixedPitchDetector(330f)
        viewModel =
            FingerprintCreationViewModel(
                mock<Application>(),
                audioSourceFactory = { audioSource },
                pitchDetectorFactory = { detector },
            )
    }

    @Test
    fun startRecording_startsTheAudioSourceAndRunsFramesThroughTheDetector() {
        viewModel.startRecording()
        audioSource.emit(frame)
        audioSource.emit(frame)

        assertTrue(audioSource.isRunning)
        assertEquals(2, detector.frames)
        assertNull(viewModel.error.value)
    }

    @Test
    fun stopRecording_stopsTheAudioSource() {
        viewModel.startRecording()

        viewModel.stopRecording()

        assertEquals(1, audioSource.stopCount)
        assertFalse(audioSource.isRunning)
    }

    @Test
    fun stopRecording_beforeAnyRecording_doesNotFail() {
        viewModel.stopRecording()

        assertEquals(0f, viewModel.instantPitch.value)
    }

    @Test
    fun startRecording_whenTheMicrophoneCannotBeOpened_reportsAnError() {
        val failing =
            FingerprintCreationViewModel(
                mock<Application>(),
                audioSourceFactory = { throw IllegalStateException("no microphone") },
                pitchDetectorFactory = { detector },
            )

        failing.startRecording()

        assertEquals("Microphone initialization failed. Please ensure no other app is using it.", failing.error.value)
    }
}
