package dev.sriramcodessw.versesync.engine

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import dev.sriramcodessw.versesync.data.Song
import dev.sriramcodessw.versesync.data.SongDao
import dev.sriramcodessw.versesync.fingerprinting.AudioFingerprinter
import dev.sriramcodessw.versesync.stt.VoskVoiceRecognition
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.abs

private const val TAG = "HybridRecognitionEngine"

data class RecognitionResult(val candidates: List<Song>)

@SuppressLint("MissingPermission")
class HybridRecognitionEngine(private val context: Context, private val songDao: SongDao) {

    private val vosk = VoskVoiceRecognition(context)
    private var dispatcher: be.tarsos.dsp.AudioDispatcher? = null
    private var fingerprinter: AudioFingerprinter? = null
    private var recognitionThread: Thread? = null
    
    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var currentLanguage = "en-us"

    val isModelReady = vosk.isModelReady
    val sttText = vosk.recognitionResult

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _instantPitch = MutableStateFlow(0f)
    val instantPitch = _instantPitch.asStateFlow()

    // Separate Heartbeats for different resolutions
    private val sttHeartbeat = flow {
        while (currentCoroutineContext().isActive) {
            emit(Unit)
            delay(10000) // STT every 10 seconds
        }
    }

    private val fingerprintHeartbeat = flow {
        while (currentCoroutineContext().isActive) {
            emit(Unit)
            delay(7000) // Fingerprint every 7 seconds
        }
    }

    private fun initializeAudioComponents(): Boolean {
        return try {
            if (dispatcher == null) {
                dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050, 1024, 0)
                val newFingerprinter = AudioFingerprinter(dispatcher!!)
                fingerprinter = newFingerprinter
                
                engineScope.launch {
                    newFingerprinter.instantPitch.collect { _instantPitch.value = it }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio components", e)
            _error.value = "Microphone initialization failed."
            false
        }
    }

    /**
     * Unified Multi-Resolution Recognition Flow
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val recognitionResult: Flow<RecognitionResult> = merge(
        sttHeartbeat.map { "stt" },
        fingerprintHeartbeat.map { "fingerprint" }
    ).flatMapLatest { triggerType ->
        searchHybrid(triggerType)
    }.flowOn(Dispatchers.Default)

    private suspend fun searchHybrid(triggerType: String): Flow<RecognitionResult> = flow {
        val sttQuery = normalizeForSanskrit(vosk.recognitionResult.value)
        val sttCandidates = if (sttQuery.isNotBlank() && triggerType == "stt") {
            songDao.searchSongsByLyrics(sttQuery).first()
        } else emptyList()

        val currentLiveFingerprint = fingerprinter?.fingerprint?.value ?: emptyList()
        val scoredCandidates = if (currentLiveFingerprint.isNotEmpty()) {
            val allSongs = songDao.getAllSongs().first()
            allSongs.mapNotNull { song ->
                val score = calculateMatchScore(song.fingerprint, currentLiveFingerprint)
                // Increase tolerance slightly for Sanskrit verses when using Hindi model
                val threshold = if (currentLanguage == "sa") 600f else 500f
                if (score < threshold) song to score else null
            }.sortedBy { it.second }.map { it.first }.take(3)
        } else emptyList()

        val combined = (sttCandidates + scoredCandidates).distinctBy { it.id }
        emit(RecognitionResult(combined))
    }

    /**
     * Precision Hack: Normalizes Devanagari text to improve 
     * Sanskrit matching accuracy when using the Hindi model.
     */
    private fun normalizeForSanskrit(text: String): String {
        if (currentLanguage != "sa") return text
        return text
            .replace("़", "") // Remove Nukta (Hindi specific)
            .replace("v", "b") // Sanskrit 'v' often heard as 'b' by Hindi models
            .trim()
    }

    private fun calculateMatchScore(reference: List<Float>?, live: List<Float>): Float {
        if (reference == null || reference.isEmpty() || live.isEmpty()) return Float.MAX_VALUE
        var minDistance = Float.MAX_VALUE
        val windowSize = live.size.coerceAtMost(reference.size)
        if (windowSize == 0) return Float.MAX_VALUE
        val liveSegment = live.takeLast(windowSize)

        for (i in 0..(reference.size - windowSize) step 10) {
            val subRef = reference.subList(i, i + windowSize)
            val dist = calculateDistance(subRef, liveSegment)
            if (dist < minDistance) minDistance = dist
        }
        return minDistance
    }

    private fun calculateDistance(list1: List<Float>, list2: List<Float>): Float {
        return list1.zip(list2).sumOf { (a, b) -> abs(a - b).toDouble() }.toFloat() / list1.size
    }

    fun loadLanguageModel(languageCode: String) {
        currentLanguage = languageCode
        vosk.loadModel(languageCode)
    }

    fun startRecognition(isPro: Boolean) {
        if (!vosk.isModelReady.value || !initializeAudioComponents()) return
        try {
            vosk.startListening()
            if (isPro) fingerprinter?.attachPitchProcessor()
            recognitionThread = Thread(dispatcher, "Audio Dispatcher")
            recognitionThread?.start()
        } catch (e: Exception) {
            _error.value = "Failed to start recognition."
        }
    }

    fun stopRecognition() {
        vosk.stopListening()
        _instantPitch.value = 0f
        dispatcher?.stop()
        recognitionThread?.interrupt()
        dispatcher = null
        fingerprinter = null
        recognitionThread = null
    }
}
