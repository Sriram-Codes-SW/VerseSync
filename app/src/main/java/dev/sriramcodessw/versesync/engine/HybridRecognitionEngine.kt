package dev.sriramcodessw.versesync.engine

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import dev.sriramcodessw.versesync.audio.AudioFrameSource
import dev.sriramcodessw.versesync.audio.PitchDetector
import dev.sriramcodessw.versesync.audio.tarsos.TarsosMicrophoneSource
import dev.sriramcodessw.versesync.audio.tarsos.TarsosPitchDetector
import dev.sriramcodessw.versesync.data.Song
import dev.sriramcodessw.versesync.data.SongDao
import dev.sriramcodessw.versesync.fingerprinting.AudioFingerprinter
import dev.sriramcodessw.versesync.stt.VoskVoiceRecognition
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private const val TAG = "HybridRecognitionEngine"

/** How often the pitch contour is re-scored against the library. */
private const val PITCH_INTERVAL_MS = 5000L

/**
 * How strongly a song matched, for callers that need more than "it is a candidate", such as the
 * vocal lock. [speechWords] is the most different heard words found in the lyrics in one piece of
 * speech. [pitchScore] and [pitchOverlap] are the best pitch distance and the voiced frames it used.
 */
data class MatchEvidence(
    val speechWords: Int = 0,
    val pitchScore: Float = Float.MAX_VALUE,
    val pitchOverlap: Int = 0,
)

data class RecognitionResult(
    val candidates: List<Song>,
    val evidence: Map<Int, MatchEvidence> = emptyMap(),
)

@SuppressLint("MissingPermission")
class HybridRecognitionEngine(
    private val context: Context,
    private val songDao: SongDao,
    // Both are parameters so tests can supply a fake recognizer and a short pitch interval
    // instead of reaching into private fields and sleeping for seconds.
    private val vosk: VoskVoiceRecognition = VoskVoiceRecognition(context),
    private val pitchIntervalMs: Long = PITCH_INTERVAL_MS,
    // Where audio comes from and which detector finds its pitch. Tests supply fakes.
    private val audioSourceFactory: () -> AudioFrameSource = { TarsosMicrophoneSource() },
    private val pitchDetectorFactory: () -> PitchDetector = { TarsosPitchDetector() },
) {
    private var audioSource: AudioFrameSource? = null
    private var fingerprinter: AudioFingerprinter? = null

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

    // Most different words heard and found in a song's lyrics in one piece of speech, per song id.
    // Guarded by the same lock as the candidates.
    private val speechEvidence = HashMap<Int, Int>()

    // The pitch contour grows continuously and only needs re-scoring now and then.
    private val pitchHeartbeat =
        flow {
            while (currentCoroutineContext().isActive) {
                emit(Unit)
                delay(pitchIntervalMs)
            }
        }

    private fun initializeAudioComponents(): Boolean =
        try {
            if (audioSource == null) {
                // ADR-01: audio is 16,000 Hz to align with Vosk STT and hardware HAL standards
                audioSource = audioSourceFactory()
                val newFingerprinter = AudioFingerprinter(pitchDetectorFactory())
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
    val recognitionResult: Flow<RecognitionResult> =
        flow {
            val songs = MutableStateFlow<List<Song>?>(null)
            val lyricTokens = HashMap<Int, CachedLyricTokens>()
            var pitchMatches: List<Pair<Song, PitchMatch>> = emptyList()

            coroutineScope {
                launch { songDao.getAllSongs().collect { songs.value = it } }

                merge(
                    songs.filterNotNull().map { SearchTrigger.LIBRARY },
                    flow { emitAll(vosk.recognitionResult.map { SearchTrigger.SPEECH }) }, // read at collection time
                    pitchHeartbeat.map { SearchTrigger.PITCH },
                ).conflate().collect { trigger ->
                    val library = songs.value ?: return@collect
                    if (trigger == SearchTrigger.LIBRARY) {
                        val ids = library.mapTo(HashSet()) { it.id }
                        lyricTokens.keys.retainAll(ids)
                        // A deleted song must not stay a candidate.
                        synchronized(accumulatedSttCandidates) {
                            accumulatedSttCandidates.removeAll { it.id !in ids }
                            speechEvidence.keys.retainAll(ids)
                        }
                    }
                    if (trigger != SearchTrigger.SPEECH) pitchMatches = scorePitch(library)
                    matchSpeech(library, lyricTokens)
                    emit(combineCandidates(pitchMatches))
                }
            }
        }.flowOn(Dispatchers.Default)

    private enum class SearchTrigger { SPEECH, PITCH, LIBRARY }

    private class CachedLyricTokens(
        val lyrics: String,
        val tokens: Set<String>,
    )

    /** ADR-03: tokenized and fuzzy lyric matching of the current speech text. */
    private fun matchSpeech(
        library: List<Song>,
        cache: HashMap<Int, CachedLyricTokens>,
    ) {
        val sttQuery = normalizeForSanskrit(vosk.recognitionResult.value)
        if (sttQuery.isBlank()) return

        val spoken = spokenMatchTokens(sttQuery)
        if (spoken.isEmpty()) return
        val found = mutableListOf<Pair<Song, Int>>()
        for (song in library) {
            val cached =
                cache[song.id]?.takeIf { it.lyrics == song.lyrics }
                    ?: CachedLyricTokens(song.lyrics, lyricTokenSet(song.lyrics)).also { cache[song.id] = it }
            SpeechMatcher.matchedWords(spoken, cached.tokens)?.let { words -> found += song to words }
        }
        synchronized(accumulatedSttCandidates) {
            found.forEach { (candidate, words) ->
                if (accumulatedSttCandidates.none { it.id == candidate.id }) {
                    accumulatedSttCandidates.add(candidate)
                }
                speechEvidence.merge(candidate.id, words) { old, new -> maxOf(old, new) }
            }
        }
    }

    /** ADR-04: musical semitone pitch matching of the live contour. */
    private fun scorePitch(library: List<Song>): List<Pair<Song, PitchMatch>> {
        val currentLiveFingerprint = fingerprinter?.fingerprint?.value ?: emptyList()
        if (currentLiveFingerprint.size < 10) return emptyList()
        // Threshold in relative musical semitones
        val threshold = PitchMatcher.threshold(currentLanguage)
        return library
            .mapNotNull { song ->
                val match = calculatePitchMatch(song.fingerprint, currentLiveFingerprint)
                if (match.score < threshold) song to match else null
            }.sortedBy { it.second.score }
            .take(3)
    }

    private fun combineCandidates(pitchMatches: List<Pair<Song, PitchMatch>>): RecognitionResult {
        val (retained, words) = synchronized(accumulatedSttCandidates) { accumulatedSttCandidates.toList() to HashMap(speechEvidence) }
        val candidates = (retained + pitchMatches.map { it.first }).distinctBy { it.id }
        val pitchById = pitchMatches.associate { it.first.id to it.second }
        val evidence =
            candidates.associate { song ->
                val pitch = pitchById[song.id]
                song.id to
                    MatchEvidence(
                        speechWords = words[song.id] ?: 0,
                        pitchScore = pitch?.score ?: Float.MAX_VALUE,
                        pitchOverlap = pitch?.overlap ?: 0,
                    )
            }
        return RecognitionResult(candidates, evidence)
    }

    /**
     * Tokenized fuzzy lyric matching that is resilient to punctuation, capitalization, formatting,
     * Unicode composition form, and the way each script marks word boundaries (see [tokenizeLyrics]).
     */
    fun matchLyricsScore(
        spokenQuery: String,
        lyrics: String,
    ): Float = lyricMatchScore(spokenQuery, lyrics)

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

    fun calculateMatchScore(
        reference: List<Float>?,
        live: List<Float>,
    ): Float = calculatePitchMatch(reference, live).score

    /** The best pitch distance of [live] against [reference], and the voiced frames it was measured over. */
    internal fun calculatePitchMatch(
        reference: List<Float>?,
        live: List<Float>,
    ): PitchMatch = PitchMatcher.match(reference, live)

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

            // Pitch is found for Pro users only. The source starts delivering frames on its own thread.
            val pitchFingerprinter = if (isPro) fingerprinter else null
            audioSource?.start { frame -> pitchFingerprinter?.onFrame(frame) }
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
            speechEvidence.clear()
        }
        audioSource?.stop()
        audioSource = null
        fingerprinter = null
    }
}
