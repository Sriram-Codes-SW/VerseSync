package dev.sriramcodessw.versesync.stt

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException

private const val TAG = "VoskVoiceRecognition"

class VoskVoiceRecognition(
    private val context: Context,
) : RecognitionListener {
    private var currentModel: Model? = null
    private var currentLanguage: String? = null
    private var speechService: SpeechService? = null
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

    @Synchronized
    fun startListening() {
        val model = currentModel
        if (model == null) {
            Log.w(TAG, "Start requested but no model is loaded.")
            return
        }

        try {
            stopListening() // never leave a previous service holding the microphone
            _recognitionResult.value = "" // Clear previous results
            val rec = Recognizer(model, 16000.0f)
            recognizer = rec
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(this)
            Log.d(TAG, "Started Vosk listening.")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to initialize recognizer", e)
        }
    }

    /**
     * Safe to call from any thread, and more than once. Vosk reports the final result on the main
     * thread after [SpeechService.stop], and that callback calls this again. Without taking
     * ownership of the objects first, two overlapping calls freed the native recognizer twice and
     * crashed the process.
     */
    @Synchronized
    fun stopListening() {
        val service = speechService
        val rec = recognizer
        speechService = null
        recognizer = null
        service?.stop()
        service?.shutdown()
        // The recognizer owns native memory and is not closed by SpeechService.
        rec?.close()
        _recognitionResult.value = "" // Clear results on stop to prevent state leaks
        Log.d(TAG, "Stopped Vosk listening and cleared results.")
    }

    override fun onPartialResult(hypothesis: String?) {
        hypothesis?.let { parseAndEmit(it) }
    }

    override fun onResult(hypothesis: String?) {
        hypothesis?.let { parseAndEmit(it) }
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

    override fun onFinalResult(hypothesis: String?) {
        stopListening()
    }

    override fun onError(e: Exception?) {
        Log.e(TAG, "Recognition error", e)
    }

    override fun onTimeout() {
        stopListening()
    }
}
