package dev.sriramcodessw.versesync.engine

import android.app.Application
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.lang.reflect.Field

class HybridRecognitionViewModelTest {

    private lateinit var applicationMock: Application
    private lateinit var engineMock: HybridRecognitionEngine
    private lateinit var viewModel: HybridRecognitionViewModel

    @BeforeEach
    fun setup() {
        applicationMock = mock()
        engineMock = mock()
        viewModel = HybridRecognitionViewModel(applicationMock)

        // Use reflection to inject the mock engine for true unit testing
        val engineField: Field = viewModel.javaClass.getDeclaredField("hybridRecognitionEngine")
        engineField.isAccessible = true
        engineField.set(viewModel, engineMock)
    }

    @Test
    fun startRecognition_shouldDelegateCallToEngine() {
        // Given
        val isPro = true

        // When
        viewModel.startRecognition(isPro)

        // Then
        verify(engineMock).startRecognition(isPro)
    }

    @Test
    fun stopRecognition_shouldDelegateCallToEngine() {
        // When
        viewModel.stopRecognition()

        // Then
        verify(engineMock).stopRecognition()
    }
}
