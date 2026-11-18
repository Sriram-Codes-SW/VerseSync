package dev.sriramcodessw.versesync.engine

import android.content.Context
import dev.sriramcodessw.versesync.audio.FakeAudioSource
import dev.sriramcodessw.versesync.audio.FixedPitchDetector
import dev.sriramcodessw.versesync.data.SongDao
import dev.sriramcodessw.versesync.stt.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** TC-U-59: the engine works with any [SpeechRecognizer], not only Vosk (docs/09 section 17, step 1). */
class SpeechRecognizerSeamTest {
    /** A recognizer written by hand, which records what the engine asks of it. */
    private class FakeSpeech(
        ready: Boolean,
    ) : SpeechRecognizer {
        val ready = MutableStateFlow(ready)
        val text = MutableStateFlow("")
        val calls = mutableListOf<String>()

        override val isModelReady: StateFlow<Boolean> = this.ready
        override val recognitionResult: StateFlow<String> = text

        override fun loadModel(languageCode: String) {
            calls += "load:$languageCode"
        }

        override fun startListening() {
            calls += "start"
        }

        override fun stopListening() {
            calls += "stop"
        }

        override fun release() {
            calls += "release"
        }
    }

    private fun engine(speech: SpeechRecognizer) =
        HybridRecognitionEngine(
            mock<Context>(),
            mock<SongDao>(),
            speech,
            audioSourceFactory = { FakeAudioSource() },
            pitchDetectorFactory = { FixedPitchDetector(220f) },
        )

    @Test
    fun theEngineExposesTheRecognizersState() {
        val speech = FakeSpeech(ready = true)
        val engine = engine(speech)
        assertSame(speech.isModelReady, engine.isModelReady)
        assertSame(speech.recognitionResult, engine.sttText)
    }

    @Test
    fun loadStartStopAndReleaseReachTheRecognizerInOrder() {
        val speech = FakeSpeech(ready = true)
        val engine = engine(speech)

        engine.loadLanguageModel("de")
        engine.startRecognition(isPro = false)
        engine.stopRecognition()
        engine.release()

        // release() stops the recognizer again before freeing it, as it did before the interface was extracted.
        assertEquals(listOf("load:de", "start", "stop", "stop", "release"), speech.calls)
    }

    @Test
    fun aRecognizerThatIsNotReadyIsNotStarted() {
        val speech = FakeSpeech(ready = false)
        val engine = engine(speech)

        engine.startRecognition(isPro = false)

        assertTrue("start" !in speech.calls, "a recognizer with no model must not be started")
    }
}
