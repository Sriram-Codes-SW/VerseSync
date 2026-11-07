| Field | Value |
|---|---|
| Document | VerseSync Design & Architecture Decision Records |
| Version | 0.6 |
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

---

### ADR-07: R8 Keep Rules for JNA/Vosk and Gson-Serialized Models

* **Status:** Accepted
* **Context:**
  Release builds enable R8 (`isMinifyEnabled = true`) but `proguard-rules.pro` only covered Jetpack Compose. Two failures followed, both invisible in debug builds:
  1. R8 renamed `com.sun.jna.Structure`. JNA's native dispatcher looks classes up by name through JNI, so the first Vosk `Model(...)` call threw `UnsatisfiedLinkError` and killed the app.
  2. R8 renamed every field of `Song` (`title` became `b`, and so on). `Song` and `Folder` are written as JSON by Gson for Bluetooth transfer and library export, so the wire format depended on a per-build mapping.
* **Decision Drivers:**
  * Release builds must behave like debug builds.
  * Serialized formats must not depend on obfuscation output.
* **Considered Options:**
  1. *Disable minification:* Larger APK, no code protection (conflicts with the README R8 policy).
  2. *Keep rules for JNA, Vosk and the serialized models:* Chosen.
  3. *Replace reflection-based Gson with explicit field annotations:* Larger change; keep rules suffice for now.
* **Decision:**
  Keep `com.sun.jna.**`, `org.vosk.**`, `Song`, `Folder` and Gson `TypeToken`, and enable `isShrinkResources` as the README states. `VoskVoiceRecognition` also catches `UnsatisfiedLinkError` so a missing native library degrades to pitch-only recognition (ADR-02).
* **Consequences:**
  * Release builds load Vosk models and produce stable JSON.
  * New Gson-serialized classes must be added to the keep rules.

---

### ADR-08: Pro Entitlement Derived from Play Billing

* **Status:** Accepted
* **Context:**
  `BillingClientWrapper.isPro` was hard-coded to `true` ("TEMPORARY"), so every user had Pro. Purchases were never acknowledged or consumed, and Google refunds unacknowledged purchases after three days. The subscription flow passed no offer token, which makes `launchBillingFlow` fail.
* **Decision:**
  1. `isPro` starts as `BuildConfig.DEBUG` and, in release builds, becomes true only for a purchased `pro_subscription`.
  2. Subscriptions are acknowledged and tips are consumed. Both are also processed from `queryPurchasesAsync` to recover interrupted purchases.
  3. Subscription purchases pass the first offer token.
  4. Reconnection after a service disconnect is delayed instead of immediate, and the connection is closed when the ViewModel is cleared.
* **Consequences:**
  * Free-tier limits and Pro gates apply in release builds.
  * Debug builds keep unlocked Pro for development.
  * The README describes a one-time Pro purchase, but the code only defines a subscription product. That product decision is still open.

---

### ADR-09: Inbound Songs Never Keep Foreign Primary Keys

* **Status:** Accepted
* **Context:**
  Songs received over Bluetooth or imported from a library file were inserted with the id they had on the sending database. `SongDao.insert` uses `REPLACE`, so a received song with id 1 silently overwrote the local song with id 1. A `folderId` pointing to a folder that does not exist locally would also violate the foreign key.
* **Decision:**
  `addSongWithConflictResolution` always inserts with `id = 0` (auto-generate) and a caller-supplied local `folderId` (null by default). Library import inserts folders first with new ids and remaps each song to the new id of its folder.
* **Consequences:**
  * Receiving or importing can no longer replace local data.
  * Folder structure survives an import.

---

### ADR-10: App Lock Fails Closed During Startup

* **Status:** Accepted
* **Context:**
  `SecurityViewModel.isAppLockEnabled` defaulted to `false` until DataStore loaded. `AuthWrapper` treated that default as "lock disabled" and set `isUnlocked = true`, which was never reset, so a saved lock could be skipped on cold start. A vocal lock with no fingerprinted song also unlocked automatically, and a dismissed system prompt left only a spinner.
* **Decision:**
  1. `SecurityViewModel.lockConfig` is `null` until both saved settings are read, and `AuthWrapper` shows a loading state while it is null.
  2. The app never unlocks automatically when a lock is enabled. If vocal lock has no song to sing, or the prompt is dismissed, the user sees a locked screen with an Unlock button that opens the device credential prompt.
  3. An unrecognized stored lock type falls back to the device credential instead of crashing.
* **Consequences:**
  * A saved lock cannot be bypassed by a slow settings read.
  * Vocal lock remains a convenience factor and not a security boundary, because a recording of the song can satisfy it.

---

### ADR-11: Untrusted Input at Download and Bluetooth Boundaries

* **Status:** Accepted
* **Context:**
  1. Model archives were extracted with `File(targetDir, entry.name)`, so an entry such as `../../x` could write outside the model directory.
  2. Unpacking ran on the main thread inside a `BroadcastReceiver`.
  3. `receiveSongs` allocated `ByteArray(totalSize)` from a size sent by the remote device, so a single message could exhaust memory.
  4. The receiver reported a stored resume offset but did not keep the partial data, so a resumed transfer would decode a zero-filled prefix.
* **Decision:**
  1. Every archive entry is resolved through `resolveInside`, which rejects paths outside the target directory. A rejected entry aborts the unpack and removes the partial model.
  2. Unpacking runs on a worker thread using `goAsync`.
  3. Transfers above 8 MiB or with a negative size are refused, and the receiver replies `-1` so the sender stops.
  4. The receiver always restarts at offset 0.
* **Consequences:**
  * A malicious archive or peer cannot write files or exhaust memory through these paths.
  * Resumable transfer is not supported until partial data is persisted.
  * The download receiver stays exported and the handler validates the id against the app's own `DownloadManager`. Making it non-exported is untested on the Bluetooth and download paths and was not adopted.

---

### ADR-12: Data Durability, Backup Scope and Bluetooth Permissions

* **Status:** Accepted
* **Context:**
  1. `fallbackToDestructiveMigration()` would delete the whole library on any schema version bump, and the schema was not exported.
  2. Auto Backup rules were empty, so trial counters and app-lock settings were restored onto other installs.
  3. Bluetooth permissions were declared but never requested at runtime. On Android 12 and later, scanning or listening throws `SecurityException`.
  4. Legacy Bluetooth and fine-location permissions applied to all Android versions.
* **Decision:**
  1. Room exports its schema, and destructive fallback applies only to downgrades. Version bumps require a `Migration`.
  2. Backup and device-transfer rules exclude the DataStore files and downloaded models.
  3. `ShareScreen` requests `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` and `BLUETOOTH_ADVERTISE` (Android 12 and later) or fine location (earlier) before scanning or listening.
  4. Legacy Bluetooth and fine-location permissions are limited to API 30 and below, and `BLUETOOTH_SCAN` is marked `neverForLocation`.
* **Consequences:**
  * Upgrades preserve user data once migrations are written.
  * The first Share action shows a permission prompt.

---

### ADR-13: License the Project under GPL-3.0

* **Status:** Accepted
* **Context:**
  The repository is public and had no license file, so by default nobody had permission to use it. The README said the repository would be private to protect intellectual property. The app also bundles TarsosDSP, which is licensed under GPL-3.0 (`app/libs`, version 2.4, built 2016-12-08), and links it directly. Distributing the app without offering its source under a compatible license would break that license.
* **Decision Drivers:**
  * The repository is already public.
  * The bundled GPL-3.0 library cannot be shipped in a closed-source app.
  * Every other dependency must stay compatible.
* **Considered Options:**
  1. *Replace TarsosDSP and keep the app closed:* Needs a new pitch detector (YIN is a published algorithm) and re-validation of matching. Largest effort.
  2. *Obtain a commercial license for TarsosDSP:* Depends on the author, and the bundled jar has no recorded source.
  3. *License the app under GPL-3.0:* Chosen. It matches the current state and the bundled library.
* **Decision:**
  License the project under GPL-3.0-only. Add `LICENSE` with the full text and `THIRD_PARTY_NOTICES.md` listing each dependency's license. Correct the README, which no longer claims the repository is private.
* **Consequences:**
  * Anyone may use, modify and redistribute the app under GPL-3.0, including the source of the Pro gate, so a fork can remove it. Revenue then relies on Google Play distribution, convenience and support, not on keeping the code secret.
  * Distributing an APK, including on Google Play, requires offering the matching source.
  * Contributions are accepted under the same license.
  * Replacing TarsosDSP later would allow relicensing, but code already published stays available under GPL-3.0.
  * The Apache-2.0 dependencies and the dual-licensed JNA are compatible with GPL-3.0.

---

## 3. Amendments to Accepted ADRs

These corrections bring the records in line with the code as built.

* **ADR-01 (implementation status):** Only the shared 16 kHz sample rate is implemented. TarsosDSP (`AudioDispatcherFactory.fromDefaultMicrophone`) and Vosk (`SpeechService`) still open their own `AudioRecord`, so the "single shared stream" option was not built. Concurrent capture by one app is permitted on Android 10 and later, but it has not been verified on every device.
* **ADR-03 (implementation):** Tokenized matching exists only in `HybridRecognitionEngine`, which loads all songs and ranks them in memory. No multi-token query was added to `SongDao`. The unused exact-substring query `searchSongsByLyrics` has been removed so the code no longer contradicts this record.
* **ADR-04 (distance rule):** Unvoiced frames are `NaN` in the relative contour and are skipped when either contour is unvoiced at that frame. Previously a frame silent in only one contour was compared against 0 and inflated the distance. A match also needs at least 8 frames voiced in both contours. Mean-centering still uses the whole live buffer and not only the compared window.
* **ADR-10 (enabling the lock):** The Enable App Lock switch refuses to turn on, and explains why, when the device has no screen lock or biometrics. Because the lock now fails closed, enabling it without a way to authenticate would lock the user out of their own library.
* **ADR-11 and ADR-12 (lifecycle):** The Bluetooth server tracks every listening socket and closes all of them after the first connection or on stop. Before, only the last socket was closed and the other accept thread blocked forever. The location permission is requested on the first-run language screen only, not on every app start.
* **Preferences storage (ADR-10, ADR-12):** The preferences classes take an injectable `DataStore<Preferences>`, and each view model accepts its preferences object. The default is unchanged, so the app still uses one process-wide DataStore. Tests use a private DataStore per test. The three DataStore-backed test classes previously shared one on-disk file through a mock context, so results depended on test order. They also reset the Main dispatcher before cancelling coroutines that still needed it. Tests run on a real single-thread Main dispatcher. Write paths are asserted on the preferences object, and view model state is asserted by creating the view model after the data is saved, as on a cold start.

---

## 4. Known Open Items

* **Transfer integrity (TC-U-10):** The transfer protocol has no checksum. Integrity relies on the announced length and JSON parsing. Adding a checksum needs a new protocol version.
* **Licensing:** Resolved by ADR-13, which licenses the project under GPL-3.0 because the bundled TarsosDSP jar is GPL-3.0. Revisit if TarsosDSP is replaced.
* **Pro product:** Only the `pro_subscription` product is implemented (ADR-08). The README now says so, but the one-time purchase it describes is still a product decision.
* **Resumable transfer:** Not supported until partial data is persisted (ADR-11).
* **State flow after a live write:** In unit tests, a view model state flow occasionally did not show a value written just after the view model started, even though the DataStore held the correct value. It happened about 1 in 10 times under test dispatchers and was not seen in 100 runs with a real Main thread. DataStore 1.3.0 reduced it but did not remove it. The cause is not identified. An emulator run (TC-M-06, TC-M-07) showed the trial counter, the lock switch and the lock type label updating immediately, so this has not been seen in the app.
