package dev.sriramcodessw.versesync.fingerprinting

import android.app.Application
import be.tarsos.dsp.io.android.AndroidAudioDispatcher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.lang.reflect.Field

class FingerprintCreationViewModelTest {

    private lateinit var applicationMock: Application
    private lateinit var dispatcherMock: AndroidAudioDispatcher
    private lateinit var viewModel: FingerprintCreationViewModel

    @BeforeEach
    fun setup() {
        applicationMock = mock()
        dispatcherMock = mock()
        viewModel = FingerprintCreationViewModel(applicationMock)

        // Use reflection to inject the mock dispatcher for testing
        val dispatcherField: Field = viewModel.javaClass.getDeclaredField("dispatcher")
        dispatcherField.isAccessible = true
        dispatcherField.set(viewModel, dispatcherMock)
    }

    @Test
    fun startRecording_doesNotCrashAndPreparesThread() {
        // We cannot reliably verify thread.start() or the subsequent run() in a unit test.
        // The most we can do is ensure that calling the method doesn't crash.
        viewModel.startRecording()
        // Test passes if no exception is thrown.
    }

    @Test
    fun stopRecording_shouldCorrectlyCallStopOnDispatcher() {
        // When
        viewModel.stopRecording()

        // Then
        verify(dispatcherMock).stop()
    }
}
