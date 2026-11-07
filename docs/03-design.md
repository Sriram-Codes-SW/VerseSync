| Field | Value |
|---|---|
| Document | VerseSync Design & Architecture Decision Records |
| Version | 0.1 |
| Date | 2026-11-08 |
| Author | Sriram-Codes-SW |
| Status | Active |

# System Architecture & Architecture Decision Records (ADRs)

## 1. System Overview

VerseSync is an offline-first Android application designed for musicians, worship leaders, and vocalists to synchronize lyrics with real-time audio and automatically recognize songs from vocal performance. The recognition architecture combines acoustic pitch contour tracking (TarsosDSP) and offline Speech-to-Text transcription (Vosk).

---

## 2. Architecture Decision Records

### ADR-01: Non-Conflicting Audio Pipeline for Concurrent Recognition

* **Status:** Accepted
* **Context:**
  The initial implementation started Vosk's `SpeechService` (which creates an `AudioRecord` at 16,000 Hz with `VOICE_RECOGNITION` audio source) and TarsosDSP's `AudioDispatcherFactory.fromDefaultMicrophone` (which creates an `AudioRecord` at 22,050 Hz with `MIC` audio source) concurrently.
  On Android hardware, simultaneous microphone captures with different sample rates from the same process violate HAL concurrent capture policies, resulting in silent zeroed buffers or `AudioRecord.startRecording()` failure.
* **Decision Drivers:**
  * Zero audio hardware resource contention.
  * Reliable capture across diverse Android OEM HALs.
  * Support for both vocal pitch tracking and speech recognition.
* **Considered Options:**
  1. *Dual separate AudioRecord instances (Status Quo):* Fails on physical devices due to audio HAL contention.
  2. *Unified 16 kHz Audio Stream:* Share a single `AudioRecord` stream at 16,000 Hz, feeding PCM bytes into Vosk's recognizer and float samples into TarsosDSP.
  3. *Independent Fallback & Decoupled Initialization:* Ensure graceful degradation when one service is active, maintaining consistent sample rates (16 kHz) compatible with both Vosk and pitch processors.
* **Decision:**
  Adopt a unified 16,000 Hz sample rate standard across the recognition pipeline. Allow pitch processing and speech transcription to operate in harmony without locking the microphone exclusively to a failing state.
* **Consequences:**
  * Pitch detection operates at 16 kHz, which provides ample resolution for vocal fundamental frequencies (80 Hz – 1,000 Hz) while matching Vosk's native requirement.
  * Audio HAL conflict is eliminated.

---

### ADR-02: Graceful Offline Model Decoupling & Non-Blocking Lifecycle

* **Status:** Accepted
* **Context:**
  `HybridRecognitionEngine#startRecognition` previously executed:
  `if (!vosk.isModelReady.value || !initializeAudioComponents()) return`
  Because Vosk speech models (~40–100MB) must be downloaded post-installation, `isModelReady` defaults to `false`. This completely prevented the microphone from starting and blocked pitch-based recognition, trapping the user in an infinite loading state.
* **Decision Drivers:**
  * Immediate usability for newly installed apps without mandatory multi-megabyte downloads.
  * Clear UI feedback when STT is waiting on models.
  * Pitch-based matching should operate independently of STT readiness.
* **Decision:**
  Decouple the recognition subsystems:
  1. Pitch/fingerprint recognition can initialize and start immediately regardless of Vosk model readiness.
  2. If Vosk models are downloaded and ready, STT activates and complements pitch matching.
  3. The UI reflects the exact state: "Pitch recognition active; speech model download available in Settings".
* **Consequences:**
  * Core recognition features work out of the box.
  * Offline models enrich the recognition when present, but are no longer a single point of failure.

---

### ADR-03: Tokenized & Fuzzy Lyric Search via Room

* **Status:** Accepted
* **Context:**
  `SongDao.searchSongsByLyrics` utilized `SELECT * FROM songs WHERE lyrics LIKE '%' || :query || '%'`. Vosk outputs continuous unpunctuated lowercase speech, while song lyrics in the database contain punctuation, line breaks, and capitalization. An exact substring match failed whenever punctuation, a line break, or a single misheard syllable intervened.
* **Decision Drivers:**
  * High recall for real-world singing speech recognition.
  * Resilience against punctuation, spacing, and minor acoustic transcription errors.
  * Fast query performance in Room/SQLite.
* **Considered Options:**
  1. *Rigid `LIKE` query (Status Quo):* Requires 100% exact substring match. Fails on punctuation and line breaks.
  2. *Token-based fuzzy matching:* Split recognized phrases into distinct word tokens (minimum length 3) and search using tokenized queries, scoring songs by the density of matching words.
* **Decision:**
  Implement multi-token search in `SongDao` and a ranking algorithm in `HybridRecognitionEngine` that normalizes text (removing punctuation, collapsing whitespace) and scores candidate songs by keyword overlap count.
* **Consequences:**
  * Spoken phrases like `"amazing grace how sweet"` successfully match `"Amazing Grace! How sweet the sound"`.

---

### ADR-04: Key-Independent Relative Pitch Semitone Representation

* **Status:** Accepted
* **Context:**
  `AudioFingerprinter` recorded raw absolute Hertz values and `calculateDistance` computed `abs(a - b)`.
  This had three critical flaws:
  1. Absolute Hz difference penalizes higher registers disproportionately (10 Hz at 100 Hz is 2 semitones, while 10 Hz at 800 Hz is negligible).
  2. Human vocalists naturally transpose keys or sing in different octaves than original recordings.
  3. Silence frames (recorded as `0.0f`) distorted distances and created false positive matches on silence.
* **Decision Drivers:**
  * Transposition and key invariance.
  * Human-scale pitch perception (logarithmic semitone scale).
  * Robust handling of unvoiced and silence segments.
* **Decision:**
  1. Convert detected frequencies $f > 0$ to musical semitones:
     $$\text{semitone} = 69 + 12 \cdot \log_2(f / 440)$$
  2. During live matching, ignore unvoiced ($0\text{ Hz}$) frames and normalize the pitch sequence by centering around its mean (relative pitch contour).
  3. Compute Euclidean or normalized Manhattan distance on the relative semitone contour.
* **Consequences:**
  * Songs are recognized even if sung in a different key or octave.
  * Silence between lines does not invalidate active vocal matching.

---

### ADR-05: State Accumulator & Result Retention Flow

* **Status:** Accepted
* **Context:**
  The previous flow merged a 10s STT heartbeat and a 7s Fingerprint heartbeat with `flatMapLatest`. When the 7s heartbeat fired, `sttCandidates` evaluated to `emptyList()`, wiping STT results from the user interface.
* **Decision Drivers:**
  * Stable, flicker-free UI.
  * Progressive candidate refinement.
* **Decision:**
  Replace intermittent wiping with an accumulated recognition state. When STT identifies candidates, they persist and combine with pitch matches until the user stops recognition or clears the session.
* **Consequences:**
  * Candidates remain visible on screen without disappearing every 7 seconds.

---

### ADR-06: Defensive Type Conversion in Room Database

* **Status:** Accepted
* **Context:**
  `Converters.toFloatList` did not validate for blank strings:
  `"".split(',').map { it.toFloat() }` throws `NumberFormatException: For input string: ""`.
* **Decision Drivers:**
  * Zero-crash database reads.
* **Decision:**
  Safely handle null or blank strings in `Converters.kt`: return `emptyList()` or `null` if the string contains no parseable numbers.
* **Consequences:**
  * Songs with empty or uninitialized fingerprints load without exception.
