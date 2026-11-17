package dev.sriramcodessw.versesync.stt

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import dev.sriramcodessw.versesync.audio.AudioFormat
import dev.sriramcodessw.versesync.audio.AudioFrame
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException

private const val TAG = "VoskVoiceRecognition"

/**
 * Offline speech recognition with Vosk.
 *
 * It does not record. The recognition engine reads the microphone once and hands every frame to
 * [acceptFrame], as it does for the pitch detector, so only one capture runs (ADR-01).
 */
class VoskVoiceRecognition(
    private val context: Context,
) {
    private var currentModel: Model? = null
    private var currentLanguage: String? = null
    private var decoder: SpeechDecoder? = null
    private var recognizer: Recognizer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val gson = Gson()

    private val _recognitionResult = MutableStateFlow("")
    val recognitionResult: StateFlow<String> = _recognitionResult.asStateFlow()

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    fun loadModel(languageCode: String) {
        if (currentLanguage == languageCode && _isModelReady.value) return

        // Release the active recognizer before its model can be swapped out.
        stopListening()
        _isModelReady.value = false
        currentLanguage = languageCode

        val modelPath = File(context.filesDir, "models/model-$languageCode")

        scope.launch {
            try {
                if (modelPath.exists()) {
                    val loadedModel =
                        withContext(Dispatchers.IO) {
                            Model(modelPath.absolutePath)
                        }
                    val previousModel = currentModel
                    currentModel = loadedModel
                    _isModelReady.value = true
                    // Vosk models hold tens of MB of native memory that the GC cannot free.
                    previousModel?.close()
                    Log.d(TAG, "Vosk model for $languageCode loaded successfully.")
                } else {
                    Log.e(TAG, "Model directory not found: ${modelPath.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize model", e)
            } catch (e: UnsatisfiedLinkError) {
                // Native library failed to load. Degrade to pitch-only recognition instead of crashing.
                Log.e(TAG, "Vosk native library unavailable", e)
            }
        }
    }

    /** Starts recognizing the frames given to [acceptFrame]. */
    @Synchronized
    fun startListening() {
        val model = currentModel
        if (model == null) {
            Log.w(TAG, "Start requested but no model is loaded.")
            return
        }

        try {
            stopListening() // never leave a previous decoder running
            _recognitionResult.value = "" // Clear previous results
            val rec = Recognizer(model, AudioFormat.SAMPLE_RATE.toFloat())
            recognizer = rec
            decoder = SpeechDecoder(VoskWaveformRecognizer(rec), ::parseAndEmit)
            Log.d(TAG, "Started Vosk listening.")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to initialize recognizer", e)
        }
    }

    /**
     * Gives one frame of microphone audio to the recognizer. Called on the audio thread, so it only
     * queues the frame. It does nothing when [startListening] has not been called.
     */
    fun acceptFrame(frame: AudioFrame) {
        decoder?.offer(frame.samples)
    }

    /**
     * Safe to call from any thread, and more than once. It takes ownership of the decoder and
     * recognizer first, so two overlapping calls cannot free the native recognizer twice, which
     * crashed the process before. The recognizer is only closed once the decoder thread has
     * finished with it.
     */
    @Synchronized
    fun stopListening() {
        val activeDecoder = decoder
        val rec = recognizer
        decoder = null
        recognizer = null
        val decoderFinished = activeDecoder?.stop() ?: true
        // The recognizer owns native memory. If the decoder thread is somehow still inside it,
        // leaking the recognizer is safer than freeing memory that is in use.
        if (decoderFinished) rec?.close() else Log.e(TAG, "The decoder did not stop, so the recognizer was not closed")
        _recognitionResult.value = "" // Clear results on stop to prevent state leaks
        Log.d(TAG, "Stopped Vosk listening and cleared results.")
    }

    private fun parseAndEmit(json: String) {
        try {
            val jsonObject = gson.fromJson(json, JsonObject::class.java)
            val text =
                if (jsonObject.has("partial")) {
                    jsonObject.get("partial").asString
                } else if (jsonObject.has("text")) {
                    jsonObject.get("text").asString
                } else {
                    ""
                }

            if (text.isNotBlank()) {
                _recognitionResult.value = text
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Vosk JSON", e)
        }
    }
}

private class VoskWaveformRecognizer(
    private val recognizer: Recognizer,
) : WaveformRecognizer {
    override fun acceptWaveForm(
        samples: ShortArray,
        length: Int,
    ) = recognizer.acceptWaveForm(samples, length)

    override fun result(): String = recognizer.result

    override fun partialResult(): String = recognizer.partialResult
}
