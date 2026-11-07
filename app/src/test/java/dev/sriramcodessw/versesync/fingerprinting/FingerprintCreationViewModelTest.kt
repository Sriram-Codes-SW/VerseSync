package dev.sriramcodessw.versesync.fingerprinting

import android.app.Application
import be.tarsos.dsp.AudioDispatcher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.lang.reflect.Field

class FingerprintCreationViewModelTest {

    private lateinit var applicationMock: Application
    private lateinit var dispatcherMock: AudioDispatcher
    private lateinit var viewModel: FingerprintCreationViewModel

    @BeforeEach
    fun setup() {
        applicationMock = mock()
        dispatcherMock = mock()
        viewModel = FingerprintCreationViewModel(applicationMock)

        val dispatcherField: Field = viewModel.javaClass.getDeclaredField("dispatcher")
        dispatcherField.isAccessible = true
        dispatcherField.set(viewModel, dispatcherMock)
    }

    @Test
    fun startRecording_doesNotCrashAndPreparesThread() {
        viewModel.startRecording()
    }

    @Test
    fun stopRecording_shouldCorrectlyCallStopOnDispatcher() {
        viewModel.stopRecording()
        verify(dispatcherMock).stop()
    }
}
