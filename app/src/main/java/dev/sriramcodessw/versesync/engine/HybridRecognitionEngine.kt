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

/** Minimum frames voiced in both contours before a pitch distance is trusted. */
private const val MIN_VOICED_OVERLAP = 8

/** How often the pitch contour is re-scored against the library. */
private const val PITCH_INTERVAL_MS = 5000L

/** Share of the spoken words that must appear in a song's lyrics for it to be a candidate. */
private const val SPEECH_MATCH_THRESHOLD = 0.35f

data class RecognitionResult(val candidates: List<Song>)

@SuppressLint("MissingPermission")
class HybridRecognitionEngine(
    private val context: Context,
    private val songDao: SongDao,
    // Both are parameters so tests can supply a fake recognizer and a short pitch interval
    // instead of reaching into private fields and sleeping for seconds.
    private val vosk: VoskVoiceRecognition = VoskVoiceRecognition(context),
    private val pitchIntervalMs: Long = PITCH_INTERVAL_MS
) {

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

    // The pitch contour grows continuously and only needs re-scoring now and then.
    private val pitchHeartbeat = flow {
        while (currentCoroutineContext().isActive) {
            emit(Unit)
            delay(pitchIntervalMs)
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
     * Recognition flow with candidate retention (ADR-05).
     *
     * A search runs whenever the speech text changes, when the song list changes, and on every
     * pitch tick. Every search reads the current speech text, so `conflate` can safely skip
     * triggers that arrive while one is running. Speech used to be checked only on a fixed tick
     * that fired together with the pitch tick, and the later of the two cancelled the earlier one,
     * so a sentence the model had heard in full could be missed.
     *
     * Speech changes several times a second while someone sings, so a search must be cheap:
     * - the song list is read once and updated when Room reports a change, not read per search;
     * - each song's lyrics are tokenized once and the tokens are reused while the lyrics are the same;
     * - the pitch contour is re-scored only on the pitch tick, and the last result is reused.
     */
    val recognitionResult: Flow<RecognitionResult> = flow {
        val songs = MutableStateFlow<List<Song>?>(null)
        val lyricTokens = HashMap<Int, CachedLyricTokens>()
        var pitchCandidates: List<Song> = emptyList()

        coroutineScope {
            launch { songDao.getAllSongs().collect { songs.value = it } }

            merge(
                songs.filterNotNull().map { SearchTrigger.LIBRARY },
                flow { emitAll(vosk.recognitionResult.map { SearchTrigger.SPEECH }) }, // read at collection time
                pitchHeartbeat.map { SearchTrigger.PITCH }
            ).conflate().collect { trigger ->
                val library = songs.value ?: return@collect
                if (trigger == SearchTrigger.LIBRARY) {
                    val ids = library.mapTo(HashSet()) { it.id }
                    lyricTokens.keys.retainAll(ids)
                }
                if (trigger != SearchTrigger.SPEECH) pitchCandidates = scorePitch(library)
                matchSpeech(library, lyricTokens)
                emit(combineCandidates(pitchCandidates))
            }
        }
    }.flowOn(Dispatchers.Default)

    private enum class SearchTrigger { SPEECH, PITCH, LIBRARY }

    private class CachedLyricTokens(val lyrics: String, val tokens: Set<String>)

    /** ADR-03: tokenized and fuzzy lyric matching of the current speech text. */
    private fun matchSpeech(library: List<Song>, cache: HashMap<Int, CachedLyricTokens>) {
        val sttQuery = normalizeForSanskrit(vosk.recognitionResult.value)
        if (sttQuery.isBlank()) return

        val spoken = spokenMatchTokens(sttQuery)
        if (spoken.isEmpty()) return
        val newlyFound = library.filter { song ->
            val cached = cache[song.id]?.takeIf { it.lyrics == song.lyrics }
                ?: CachedLyricTokens(song.lyrics, lyricTokenSet(song.lyrics)).also { cache[song.id] = it }
            tokenMatchScore(spoken, cached.tokens) >= SPEECH_MATCH_THRESHOLD
        }
        synchronized(accumulatedSttCandidates) {
            newlyFound.forEach { candidate ->
                if (accumulatedSttCandidates.none { it.id == candidate.id }) {
                    accumulatedSttCandidates.add(candidate)
                }
            }
        }
    }

    /** ADR-04: musical semitone pitch matching of the live contour. */
    private fun scorePitch(library: List<Song>): List<Song> {
        val currentLiveFingerprint = fingerprinter?.fingerprint?.value ?: emptyList()
        if (currentLiveFingerprint.size < 10) return emptyList()
        return library.mapNotNull { song ->
            val score = calculateMatchScore(song.fingerprint, currentLiveFingerprint)
            // Threshold in relative musical semitones
            val threshold = if (currentLanguage == "sa") 3.5f else 3.0f
            if (score < threshold) song to score else null
        }.sortedBy { it.second }.map { it.first }.take(3)
    }

    private fun combineCandidates(pitchCandidates: List<Song>): RecognitionResult {
        val retained = synchronized(accumulatedSttCandidates) { accumulatedSttCandidates.toList() }
        return RecognitionResult((retained + pitchCandidates).distinctBy { it.id })
    }

    /**
     * Tokenized fuzzy lyric matching that is resilient to punctuation, capitalization, formatting,
     * Unicode composition form, and the way each script marks word boundaries (see [tokenizeLyrics]).
     */
    fun matchLyricsScore(spokenQuery: String, lyrics: String): Float {
        return lyricMatchScore(spokenQuery, lyrics)
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

    /**
     * Mean absolute semitone difference over frames that are voiced in BOTH contours (ADR-04).
     * A frame that is silent in only one contour is skipped rather than compared against 0,
     * and too little shared voiced material is treated as no match.
     */
    private fun calculateDistance(list1: List<Float>, list2: List<Float>): Float {
        var count = 0
        var sum = 0.0
        for (i in list1.indices) {
            val a = list1[i]
            val b = list2[i]
            if (!a.isNaN() && !b.isNaN()) {
                sum += abs(a - b)
                count++
            }
        }
        return if (count >= MIN_VOICED_OVERLAP) (sum / count).toFloat() else Float.MAX_VALUE
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
