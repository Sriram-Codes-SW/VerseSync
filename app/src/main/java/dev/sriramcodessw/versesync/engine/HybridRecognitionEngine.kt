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

    // Persistent candidate accumulator to prevent state flickering
    private val accumulatedSttCandidates = mutableListOf<Song>()

    // Separate Heartbeats for different resolutions
    private val sttHeartbeat = flow {
        while (currentCoroutineContext().isActive) {
            emit(Unit)
            delay(5000) // STT every 5 seconds
        }
    }

    private val fingerprintHeartbeat = flow {
        while (currentCoroutineContext().isActive) {
            emit(Unit)
            delay(5000) // Fingerprint every 5 seconds
        }
    }

    private fun initializeAudioComponents(): Boolean {
        return try {
            if (dispatcher == null) {
                // ADR-01: Use 16,000 Hz to align with Vosk STT and hardware HAL standards
                dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(16000, 1024, 0)
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
     * Unified Multi-Resolution Recognition Flow with Candidate Retention (ADR-05)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val recognitionResult: Flow<RecognitionResult> = merge(
        sttHeartbeat.map { "stt" },
        fingerprintHeartbeat.map { "fingerprint" }
    ).flatMapLatest { triggerType ->
        searchHybrid(triggerType)
    }.flowOn(Dispatchers.Default)

    private suspend fun searchHybrid(triggerType: String): Flow<RecognitionResult> = flow {
        val allSongs = songDao.getAllSongs().first()

        // ADR-03: Tokenized & Fuzzy Lyric Matching
        val sttQuery = normalizeForSanskrit(vosk.recognitionResult.value)
        if (sttQuery.isNotBlank() && triggerType == "stt") {
            val newlyFound = allSongs.filter { song ->
                matchLyricsScore(sttQuery, song.lyrics) >= 0.35f
            }
            synchronized(accumulatedSttCandidates) {
                newlyFound.forEach { candidate ->
                    if (accumulatedSttCandidates.none { it.id == candidate.id }) {
                        accumulatedSttCandidates.add(candidate)
                    }
                }
            }
        }

        // ADR-04: Musical Semitone Pitch Matching
        val currentLiveFingerprint = fingerprinter?.fingerprint?.value ?: emptyList()
        val scoredCandidates = if (currentLiveFingerprint.size >= 10) {
            allSongs.mapNotNull { song ->
                val score = calculateMatchScore(song.fingerprint, currentLiveFingerprint)
                // Threshold in relative musical semitones
                val threshold = if (currentLanguage == "sa") 3.5f else 3.0f
                if (score < threshold) song to score else null
            }.sortedBy { it.second }.map { it.first }.take(3)
        } else emptyList()

        val combined = (accumulatedSttCandidates + scoredCandidates).distinctBy { it.id }
        emit(RecognitionResult(combined))
    }

    /**
     * Tokenized fuzzy lyric matching that is resilient to punctuation, capitalization, and formatting.
     */
    fun matchLyricsScore(spokenQuery: String, lyrics: String): Float {
        val cleanSpoken = spokenQuery.lowercase().replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
        val spokenWords = cleanSpoken.split("\\s+".toRegex()).filter { it.length >= 3 }
        if (spokenWords.isEmpty()) return 0f

        val cleanLyrics = lyrics.lowercase().replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
        val lyricWords = cleanLyrics.split("\\s+".toRegex()).filter { it.isNotBlank() }.toSet()
        if (lyricWords.isEmpty()) return 0f

        val matchingWords = spokenWords.count { lyricWords.contains(it) }
        return matchingWords.toFloat() / spokenWords.size
    }

    /**
     * Normalizes Devanagari text to improve Sanskrit matching accuracy when using Hindi model.
     */
    private fun normalizeForSanskrit(text: String): String {
        if (currentLanguage != "sa") return text
        return text
            .replace("़", "") // Remove Nukta (Hindi specific)
            .replace("v", "b") // Sanskrit 'v' often heard as 'b' by Hindi models
            .trim()
    }

    fun calculateMatchScore(reference: List<Float>?, live: List<Float>): Float {
        if (reference == null || reference.isEmpty() || live.isEmpty()) return Float.MAX_VALUE

        val refContour = AudioFingerprinter.toRelativeSemitoneContour(reference)
        val liveContour = AudioFingerprinter.toRelativeSemitoneContour(live)
        if (refContour.isEmpty() || liveContour.isEmpty()) return Float.MAX_VALUE

        var minDistance = Float.MAX_VALUE
        val windowSize = liveContour.size.coerceAtMost(refContour.size)
        if (windowSize < 8) return Float.MAX_VALUE
        val liveSegment = liveContour.takeLast(windowSize)

        val step = 5.coerceAtMost((refContour.size - windowSize).coerceAtLeast(1))
        for (i in 0..(refContour.size - windowSize) step step) {
            val subRef = refContour.subList(i, i + windowSize)
            val dist = calculateDistance(subRef, liveSegment)
            if (dist < minDistance) minDistance = dist
        }
        return minDistance
    }

    private fun calculateDistance(list1: List<Float>, list2: List<Float>): Float {
        var count = 0
        var sum = 0.0
        for (i in list1.indices) {
            val a = list1[i]
            val b = list2[i]
            if (a != 0f || b != 0f) {
                sum += abs(a - b)
                count++
            }
        }
        return if (count > 0) (sum / count).toFloat() else Float.MAX_VALUE
    }

    fun loadLanguageModel(languageCode: String) {
        currentLanguage = languageCode
        vosk.loadModel(languageCode)
    }

    /**
     * Starts recognition. Pitch detection functions immediately; STT activates when model is ready (ADR-02).
     */
    fun startRecognition(isPro: Boolean) {
        _error.value = null
        if (!initializeAudioComponents()) {
            _error.value = "Microphone initialization failed."
            return
        }

        try {
            // Start STT if speech model is available
            if (vosk.isModelReady.value) {
                vosk.startListening()
            }

            // Start pitch processor
            if (isPro) {
                fingerprinter?.attachPitchProcessor(sampleRate = 16000f, bufferSize = 1024)
            }

            recognitionThread = Thread(dispatcher, "Audio Dispatcher")
            recognitionThread?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recognition", e)
            _error.value = "Failed to start recognition."
        }
    }

    fun stopRecognition() {
        vosk.stopListening()
        _instantPitch.value = 0f
        synchronized(accumulatedSttCandidates) {
            accumulatedSttCandidates.clear()
        }
        dispatcher?.stop()
        recognitionThread?.interrupt()
        dispatcher = null
        fingerprinter = null
        recognitionThread = null
    }
}
