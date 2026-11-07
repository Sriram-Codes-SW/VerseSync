package dev.sriramcodessw.versesync.stt

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.vosk.Model
import org.vosk.Recognizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException

private const val TAG = "VoskVoiceRecognition"

class VoskVoiceRecognition(private val context: Context) : RecognitionListener {

    private var currentModel: Model? = null
    private var currentLanguage: String? = null
    private var speechService: SpeechService? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val gson = Gson()

    private val _recognitionResult = MutableStateFlow("")
    val recognitionResult: StateFlow<String> = _recognitionResult.asStateFlow()

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    fun loadModel(languageCode: String) {
        if (currentLanguage == languageCode && _isModelReady.value) return

        _isModelReady.value = false
        currentLanguage = languageCode
        
        val modelPath = File(context.filesDir, "models/model-$languageCode")
        
        scope.launch {
            try {
                if (modelPath.exists()) {
                    val loadedModel = withContext(Dispatchers.IO) {
                        Model(modelPath.absolutePath)
                    }
                    currentModel = loadedModel
                    _isModelReady.value = true
                    Log.d(TAG, "Vosk model for $languageCode loaded successfully.")
                } else {
                    Log.e(TAG, "Model directory not found: ${modelPath.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize model", e)
            }
        }
    }

    fun startListening() {
        val model = currentModel
        if (model == null) {
            Log.w(TAG, "Start requested but no model is loaded.")
            return
        }

        try {
            _recognitionResult.value = "" // Clear previous results
            val rec = Recognizer(model, 16000.0f)
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(this)
            Log.d(TAG, "Started Vosk listening.")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to initialize recognizer", e)
        }
    }

    fun stopListening() {
        speechService?.stop()
        speechService = null
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
            val text = if (jsonObject.has("partial")) {
                jsonObject.get("partial").asString
            } else if (jsonObject.has("text")) {
                jsonObject.get("text").asString
            } else ""
            
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
