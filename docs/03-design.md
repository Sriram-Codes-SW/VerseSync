| Field | Value |
|---|---|
| Document | VerseSync Design & Architecture Decision Records |
| Version | 0.42 |
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
  * A single subscription product is used on purpose for now, so Pro behavior can be tested separately before the final products are set up. The one-time purchase and the Pro+ tier in the README are planned.

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
  License the project under GPL-3.0-only. Add `LICENSE` with the full text and `THIRD_PARTY_NOTICES.md` listing each dependency's license. Correct the README, which no longer claims the repository is private. The owner confirmed this choice on 2026-11-08.
* **Consequences:**
  * Anyone may use, modify and redistribute the app under GPL-3.0, including the source of the Pro gate, so a fork can remove it. Revenue then relies on Google Play distribution, convenience and support, not on keeping the code secret.
  * Distributing an APK, including on Google Play, requires offering the matching source.
  * Contributions are accepted under the same license.
  * Replacing TarsosDSP later would allow relicensing, but code already published stays available under GPL-3.0.
  * The owner also plans a refactor that makes the pitch logic independent of TarsosDSP (issue #13, design in `docs/07-pitch-detection-design.md` once it is merged). It does not change this decision. It keeps relicensing possible for future work.
  * The Apache-2.0 dependencies and the dual-licensed JNA are compatible with GPL-3.0.

---

### ADR-14: Script-Aware Lyric Tokenization for All Supported Languages

* **Status:** Accepted
* **Context:**
  `matchLyricsScore` removed every character that was not a letter, digit or space. Combining marks, such as the vowel signs of Devanagari, Tamil, Telugu, Bengali and Punjabi, are neither, so words were cut into one or two letter fragments and then dropped by the three character minimum. Arabic printed with short-vowel marks no longer matched speech without them. Chinese and Japanese lyrics have no word spaces, while their speech models insert spaces at other places, so no word ever matched. Text in decomposed Unicode form did not match composed text. A test over all 29 configured languages failed for 22 of 88 checks. Some other languages passed only because both sides were damaged in the same way.
* **Decision:**
  Replace the inline matching with `tokenizeLyrics` and `lyricMatchScore`:
  1. Lowercase and normalize both sides to composed Unicode form.
  2. Keep combining marks inside words, and treat only punctuation as a separator.
  3. Remove marks that printed lyrics and speech models disagree about: Arabic tatweel and short-vowel marks, the Devanagari nukta, and zero-width space and joiner.
  4. Turn runs of Han, Hiragana and Katakana, ignoring spaces inside them, into overlapping pairs of characters.
  The 0.35 threshold and the three character minimum for other words are unchanged.
* **Consequences:**
  * All 29 languages match their own lyrics and reject the lyrics of other languages in tests.
  * A new language fails a test until a sample phrase is added for it.
  * The samples are short written phrases and a hand-made guess at how each speech model reports them. Real recognition accuracy per language still needs audio and the real models.
  * Chinese and Japanese pair matching can match common pairs by chance, so the threshold may need tuning on real data.

---

### ADR-15: Speech Model Availability per Language

* **Status:** Accepted
* **Context:**
  Nine of the 29 model download links returned HTTP 404 (Tamil, Marathi, Bengali, Kannada, Malayalam, Punjabi, Greek, Swedish and Ukrainian), so downloads for those languages always failed. Vosk's published list has no model of any size for Tamil, Marathi, Bengali, Kannada, Malayalam or Punjabi. For Greek it lists only `vosk-model-el-gr-0.7`, about 1.1 GB. Swedish and Ukrainian models do exist, under different names. The song language list and recognition were both limited to languages whose model was downloaded, so a language with no model could never be used.
* **Decision Drivers:**
  * Never offer a download that cannot succeed.
  * Lyrics and pitch matching do not need a speech model.
  * Do not ship a 1.1 GB model through the download manager.
* **Decision:**
  1. Swedish uses `vosk-model-small-sv-rhasspy-0.15` (about 303 MB) and Ukrainian uses `vosk-model-small-uk-v3-nano` (about 78 MB).
  2. `LanguageModelInfo.url` is nullable. It is null for Tamil, Marathi, Bengali, Kannada, Malayalam, Punjabi and Greek, and `hasSpeechModel` is false for them.
  3. The language screens say "No speech model" for those languages instead of a download button, and `downloadLanguage` does nothing for them.
  4. Add Song offers them, and they are not reported as missing a model on the sync health screen or in Settings.
* **Consequences:**
  * Speech recognition is unavailable for those seven languages. They rely on pitch matching.
  * The recognition screen still lists only languages with an installed model, so a user whose songs are all in those languages cannot start recognition yet. This is an open item.
  * Greek can be added if a small model is published.
  * The Swedish model is much larger than the others.

---

### ADR-16: Application Id Matches the Code Namespace

* **Status:** Accepted
* **Context:**
  `applicationId` was `dev.sriramkaushal.versesync`, while the Kotlin namespace and every package is `dev.sriramcodessw.versesync`. The two ids appear in different places, such as Play Console, adb and permissions, and the mismatch served no purpose. The old id also contained a personal name. Android and Google Play identify an app by its application id, and the id cannot be changed after the first release on Google Play.
* **Decision:**
  Use `dev.sriramcodessw.versesync` as the application id. Do it before the first public release, while no user has the app under the old id.
* **Consequences, and migration note:**
  * To Android this is a different app. It installs next to builds that use the old id, and nothing carries over. Songs, folders, settings, downloaded speech models and the keystore key used for library export all stay with the old app.
  * Purchases and subscriptions belong to the Play Console entry for the old id and are not carried over. The new id needs its own Play Console app, with the `pro_subscription`, `small_tip` and `big_tip` products created again under the same product ids.
  * Anything registered against the package name, such as API keys tied to a signing certificate, must be registered again for the new id.
  * Bluetooth sharing uses fixed service UUIDs and not the application id, so builds with the two ids can still exchange songs. Library exports are encrypted with a device-bound key, so they could not be moved between installs anyway.
  * This is safe only if the old id has no users and no Play listing. If it does, do not merge this change and keep the old id. The owner confirmed on 2026-11-08 that the old id has no users. A Play listing was not mentioned, so check for one before the first upload.
  * The scripts in `tools/audio-test` name the id for adb, and they use the new one.
  * Checked on an emulator: a build with each id installs next to the other, and a file written in the old app's data folder does not exist in the new app.

---

### ADR-17: Stable Room, Biometric on 1.4.0-beta01

* **Status:** Accepted
* **Context:**
  The release build used Room 2.7.0-alpha11 and `biometric-ktx` 1.4.0-alpha02, both alpha versions, and the version catalog held an unused Room 2.6.1. Room has a stable 2.8 line. `androidx.biometric` has no stable version newer than 1.1.0 from 2020, and `biometric-ktx` has only alpha versions. The app uses only the core biometric classes (`BiometricManager.canAuthenticate`, `BiometricPrompt`, `PromptInfo`), no extensions.
* **Considered Options:**
  1. *`biometric` 1.1.0, the last stable:* Compiled before Android 12 to 16 existed, and without five years of fixes. Switching to an older line is a bigger behavior change than a newer build of the line already in use.
  2. *`biometric` 1.4.0-beta01:* The same line the app already ran, one step later, with the public API frozen at beta.
  3. *Stay on the `ktx` alpha:* An alpha with later fixes behind it, for no benefit.
* **Decision:**
  Use Room 2.8.5 and `androidx.biometric:biometric` 1.4.0-beta01, both from the version catalog. The `ktx` artifact is dropped, because nothing from it is used. The choice of biometric version was checked with an independent second opinion, which also preferred option 2.
* **Consequences:**
  * The only pre-release library left in the release build is the biometric beta. Revisit it when 1.4 reaches a stable release.
  * Room 2.8's migration test helper needs kotlinx.serialization 1.8, while the lifecycle libraries pin 1.7.3 and the helper then fails with `AbstractMethodError`. The newer version is added for debug and test builds only. The release classpath is unchanged.
  * Checked: the database of the previous build opens and keeps its songs after an upgrade install, `VerseDatabaseMigrationTest` passes, the exported schema is unchanged, and the app lock enables, prompts and unlocks on an Android 16 (API 36) emulator with a screen lock. The same app lock steps pass on Android 9 and 10 emulators (TC-M-14).

### ADR-18: A Speech Decision Layer for the Vocal Lock

* **Status:** Proposed. This ADR does not authorize code changes. Nothing in the app changes until the conditions below are met and the owner approves.
* **Context:**
  Recognition builds a list of candidate songs and a person chooses one, so a wrong candidate is harmless (ADR-03, ADR-05). The vocal lock decides without a person choosing (ADR-10). Its words path today uses the candidate rule's evidence: at least three different heard words, and a rarity-weighted score at least 25 percent above the strongest other candidate.
  The evidence below is from read speech, not singing, with an evaluation library of 80 songs (docs/09, section 12; earlier sections used libraries of 92, 110 and 155 songs). On 707 fresh recordings of songs that are not in the library, the current lock words rule decided on some song for 32 percent (29 to 35) and the current speech rule for 46 percent (42 to 50). "Decided on some song" means naming any of the 80 songs. These were measured with the rival taken from every song that has a heard word; the app as built takes it among candidates only (`combineCandidates`), which is laxer, so the app itself was not measured exactly. On 105 recordings of songs in the library the lock words rule named a wrong song for 1.0 percent (0.2 to 5.2). The lock opens for one target, and its rate per pair of recording and target song was measured only for earlier corpora and the current rules: 57 of 32,344 (0.2 percent, section 8) and 83 of 93,685 (0.1 percent, section 10).
  A rule chosen on other recordings and reported once on these 812 (section 12) decided on a song not in the library for 0 of 707 (upper end of the 95 percent interval 0.5 percent), so it opened for no target song, and named a wrong song for 0 of 105 (upper end 3.5 percent). It named the right song for 70 percent (60 to 78) of recordings in the library, against 85 percent (77 to 90) for the lock words rule, and for 51 percent (36 to 67) of the 3 second snippets. It acted at a median of 2.6 seconds, against 2.1.
* **Considered Options:**
  1. *Keep the lock's rule:* The measured permissiveness stays in a feature people may read as secure.
  2. *Tighten the candidate rule (share and minimum words):* Changes the recognition list, where a wrong candidate is harmless, and removes right candidates for no gain there. A sweep showed the trade (docs/09, section 9). Rejected.
  3. *A decision rule separate from the candidate rule, used only where the app acts without a person choosing (the lock):* The candidate list stays as it is. This is the proposal.
  4. *Wait before acting (a hold):* Evaluated and not adopted. On the selection corpora, at the chosen thresholds, a hold of 0.5 seconds lowered right answers from 74 to 67 percent for no measurable gain in false decisions (0.6 to 0.5 percent, within noise).
  5. *Use pitch as decision evidence:* On read speech pitch gave a candidate for 53 percent of recordings of songs not in the library, and found only 16 percent of pieces of their own reference (docs/09, section 8). Not usable for a decision until the pitch matching is rebuilt (roadmap item 4).
* **Decision (proposed):**
  The words path of the lock opens only if the rule below names the target song.
  * *Words.* Heard words are the tokens of the recognizer's text after dropping words under 3 letters (Chinese and Japanese tokens are character pairs and are kept); lyrics are tokenized the same way (ADR-14).
  * *Evidence, per song with at least one heard word in its lyrics.* `words`: the number of different heard words found in its lyrics. `share`: the heard words found, repeats counted, divided by the heard words. `weight`: the sum over the different found words of `ln(1 + N / df)`, where `N` is the number of songs in the library, including songs with no lyrics, and `df` is the number of songs whose lyrics contain the word.
  * *Rule.* The target must be the song with the highest weight, with `words` at least 4, `share` at least 0.8, and its weight above the strongest other song's weight by MORE than `1.0 * ln(1 + N)`. The strongest other song is taken over every song with a heard word, not only candidates, and is 0 if there is none. A tie never decides. The measured rule let a song whose lyrics have fewer than 4 different words open on all of them; for the lock that exception is removed, so a target whose lyrics have fewer than 4 different words does not open on the words path. That is stricter than what was measured, and what it costs was not measured.
  * *Evidence over time.* The measured rule used evidence that can be taken back: for each song, the best result among finished utterances plus only the current partial result, so a later partial replaces an earlier one. A result the app has only because the recognizer was stopped does not count. The app keeps evidence that only grows (`speechEvidence`). The growing kind was measured only on the selection corpora (196 against 197 right and 5 against 5 false decisions of 810, at the chosen settings), not on the fresh corpus, so an implementation must pick one and measure it.
  * *Timing.* The rule acts the first time it holds, with no hold.
  The candidate list, the recognition screen, the pitch path of the lock and the two-attempts fallback to the device lock are unchanged. The rule would live in the main sources as its own class beside `VocalLockPolicy`, and the evaluation harness would run that same class.
* **Consequences:**
  * The lock opens less often for the right voice: 70 percent against 85 percent of read recordings, later (median 2.6 seconds), and for about half of 3 second snippets. Each failed attempt counts toward the fallback, so more people reach the device lock. An attempt is one stretch of listening that ends, by a tap or after 10 seconds, without opening (ADR-10, issue #60).
  * The lock asks for more words that the recognizer heard correctly. A garbled transcript, which the small models often give, lowers the share of heard words found and stops the rule from deciding. Speech that the recognizer handles badly, which includes some accents, children's voices and speech impairments, will reach the device lock more often. This was not measured.
  * Not measured, and each is a condition below: singing; real lyrics, which repeat words in choruses (a word counts once toward the minimum and each time toward the share); libraries other than 80 songs, since the gap is scaled by `ln(1 + N)` and a library of fewer than about 15 songs, where a target with no rival and 4 found words (each weighs at least `ln 2`, so at least 2.7 together) always passes the gap test because `ln(1 + N)` is below 2.7, behaves differently; Chinese and Japanese; any language except English, Hindi, Spanish, German and French; the live recognizer, whose timing and partial results differ from the replay; the speakers, who were not checked to be different between the selection and report corpora; and the app's own way of gathering evidence.
  * 0 of 105 wrong songs has an upper end of 3.5 percent, so a wrong-song rate under 1 percent is not shown, only that none occurred.
  * The vocal lock still checks the words, not the speaker. The decision rule does not change that, and the lock's name and copy are a separate item (#48).
* **Conditions before it ships:**
  1. The pass bar is met on real recordings of singing and of speaking from the evaluation set (#43), not only on read speech: the upper end of the interval for deciding on a song not in the library is at most 5 percent, and a wrong song is named for at most 1 percent with an upper bound shown, which needs about 300 recordings of songs in the library with none named wrongly.
  2. The rate at which the lock opens for a wrong target is measured per pair of recording and target song, with libraries of the sizes real users have (for example 1, 5, 20 and 200 songs), and for languages written with character pairs. A library too small for the rule to mean anything is refused on the words path.
  3. A check on a real phone, with the live recognizer, of the time to open and of how often the lock opens for the right voice, including children and accented speech.
  4. The rule is moved into the main sources with tests, one way of gathering evidence is chosen and measured, `VocalLockPolicy` uses it, and ADR-10 is amended.
  5. The owner approves the change in what the lock asks of a person.

* **Amendment (2026-11-18, issue #90): first real recordings.** The rule above, exactly as written and with a library of 15 songs, was replayed on 22 recordings of sung anthems and on clips that are not songs (4 instrumental, 4 of a person speaking English, 1 of applause), with the text of the shipped recognizer and of a Whisper model (docs/09, section 18). It decided on a song for none of the 9 clips that are not songs with the shipped text (upper end 30%) and none of the 15 with Whisper's text (upper end 20%), where the two rules the app runs today each decided on the English anthem for a recording of someone speaking English. It named the right song for 0 of 17 sung anthems with the shipped text (the current lock words rule also 0) and for 2 of 17 with Whisper's text (the current lock words rule 5), and a wrong song for none. This is far short of condition 1 (17 recordings in the library against about 300 needed, one library size, files and not a phone) and none of the conditions is met. It does not change the status of this ADR.
### ADR-19: Landmark Fingerprints for Recorded Music, Offline

* **Status:** Proposed, and narrowed. This covers recorded playback near the phone (a backing track, a lip-synced number, music playing in a room), and it is NOT the concert path: a live band is a different performance, which this method is not expected to match. Only the matcher and its generated test set (phase 1), including a test of how far a re-performance can drift, are being built, and later phases are parked. This ADR does not authorize storing prints, a screen to learn songs, a catalog, or any change to what the app recognizes.
* **Context:**
  The app recognizes a person singing or saying a song's lyrics: Vosk for the words and a monophonic pitch matcher (ADR-03, ADR-04). A recording playing near the phone is a different input. In a trial with two library songs and a commercial recording played into the emulator microphone, the speech model barely transcribed the sung vocals over the instruments, one of two songs was found, and songs not in the library were offered as candidates (ADR-03 amendment for issue #44). A monophonic pitch tracker cannot follow a full mix.
  The owner wants a Shazam-style app that works offline. Offline means no network for recognition: the fingerprints of the songs it can recognize must already be on the phone. That fixes what the app can promise. A service such as Shazam holds a licensed catalog of tens of millions of songs on servers. This project has no such catalog and cannot ship one, so **it can recognize only songs whose fingerprints it has, and it cannot recognize "any song" offline.** Where the fingerprints come from, and how many fit on a phone, are the open questions below.
  The roadmap listed Shazam-style fingerprinting as not planned, because two performances of a song do not share the spectral peaks it relies on (docs/08, section 5). That reason does not apply when the same recording is played again: the same master has the same peaks every time, which is what the method was made for. It does not cover covers, live versions, the person singing, or sped-up and re-edited versions.
  Constraints: no copyrighted audio may be stored or built into the app, so only hashes computed on the device may be kept, never audio; the audio of another app must never be read directly (no playback capture); nothing in the repository names a streaming service; the voice shortcut must never open on a recording (ADR-10, ADR-18).
* **Considered Options:**
  *How to fingerprint:*
  1. *Landmark fingerprints (constellation hashes of spectral peak pairs, matched by offset voting):* The published method (Wang 2003). Robust to level, and to noise and a small speaker when peaks are taken from the band a phone speaker can reproduce. This is the proposal.
  2. *Chroma fingerprints (Chromaprint style):* Built to compare whole, clean files, and weak on short noisy microphone snippets. The library is C++ under the LGPL and would need native code.
  3. *A learned embedding:* More robust to noise and reverb, but it needs training data that is copyrighted or licensed, a model, and a way to check it that does not exist here. Later, only if a measured need appears.
  4. *A larger speech model, such as a Whisper-class one:* Better on sung lyrics, with cost in size and delay, still weak over a full band, and it cannot tell recordings apart. It complements this and does not replace it (roadmap item 8).
  5. *The playback capture API:* Reads other apps' audio digitally, which is ripping. Rejected.
  *Where the fingerprints come from (not decided; the matcher is the same for all):*
  6. *The person's own audio files on the phone,* decoded on the device and fingerprinted, the audio discarded. Needs no network and no listening, and uses only files the person already has. Does not reach songs that exist only as a stream.
  7. *Learning by listening:* the person plays the song near the microphone once. Reaches streamed songs, but only the parts that were heard, and many streaming terms forbid recording.
  8. *Catalog packs of openly licensed music,* fingerprinted by this project and downloaded once. Legal to build and share, but it is not the music people usually mean.
  9. *A licensed commercial catalog:* the only way to recognize commercial songs the person has not taught the app. Not available to this project, and a data and licensing decision for the owner, not an engineering one.
* **Decision (proposed):**
  Phase 1: a pure-Kotlin landmark matcher (`fingerprinting/landmarks`), written from the published method, and a generated test set that is not copyrighted, with thresholds chosen on one seed and reported once on another, plus the cost of a bigger library: storage per song, and how lookup time and false accepts change from 60 to 600 songs (docs/09, section 13). Later phases are proposed and not decided: the prints in their own Room database file, kept out of backup and out of Bluetooth sharing, because a few hundred kilobytes per song would otherwise push the songs database over the Auto Backup limit; a way to add songs (options 6 and 7, chosen by the owner); and print evidence feeding the candidate list only. The voice shortcut ignores print evidence, with a test.
* **Consequences:**
  * Offline recognition is limited to the fingerprints on the device. On generated music a four-minute song measured about 110 KB (78 hashes a second), so 1,000 songs are about 110 MB and 10,000 about 1.1 GB, and a lookup took 0.1 ms at 60 songs and 0.5 ms at 600 (docs/09, section 13). A catalog of millions is out of reach on a phone without a much sparser print.
  * Measured on generated music only: it finds a recording in noise (100% clean, 92% at 5 dB of white noise), but a playback 1% fast or slow leaves 14% or 4%, and a performance with a tempo 5% off leaves 1%, with new sounds and a drifting tempo 13%. The pre-registered false-accept target was not met (0.17% at the strictest rule against 0.1%), so no rule is chosen. It is not usable for a live band, and nothing here was measured on real music.
  * Only the same recording matches. A cover, a live version, the person's own singing and an edit will not, and the copy must say so.
  * Many streaming terms forbid recording. Listening to a whole song so as to learn it could be read that way. The hashes cannot be turned back into audio, but whether they count as a copy is a legal question and not one this ADR answers. The feature must not be advertised as working with any named service.
  * The landmark-hash patents are believed to have expired. That was not checked.
  * Not measured: real recordings, real speakers, rooms, or the phone's own audio processing (some phones apply speech noise suppression to the microphone input that can remove music). A generated corpus overstates accuracy, because real music repeats and songs of a style share structure.
* **Conditions before any app change:**
  1. The phase 1 result is reported as in docs/09 section 13, whatever it shows.
  2. The owner chooses where the fingerprints come from (options 6 to 9), knowing that without a licensed catalog commercial songs are recognized only if the app was taught them.
  3. The owner runs a real-phone protocol (phones, distances, rooms, songs in and not in the library, including the same artist), recording only per-trial results and never audio, and the result is reported with its intervals and how few trials it has.
  4. A decision, with Opus's review, on where prints live and how they are migrated (ADR-12).
  5. A test that a match on prints alone never opens the voice shortcut.
  6. The owner approves the change.


### ADR-20: Concert Mode, a Setlist Tracked Manual-First

* **Status:** Phase 1 (no recognition): Accepted, the owner approved the decisions below with the merge of PR #70, and ADR-20 authorizes the Phase 1 work in the order listed there. Every later phase is Proposed, not decided, and needs its own ADR (see the conditions below).
* **Context:**
  The owner's use is a concert with a poor network. The person has a library of songs with lyrics on the phone and wants to sing along. A live song is not the recording played again, and nothing in the repository has been measured on singing, a concert, a crowd or a real phone.
  What is known, each from its own source:
  * In a trial with two library songs and a commercial recording played into the emulator microphone, the speech model barely transcribed the sung vocals over the instruments, and one of the two songs was found (ADR-19, context). That is two songs on an emulator.
  * On read speech, the pitch matcher found 16 percent (19 of 116, 11 to 24) of recordings that were a piece of their own reference (docs/09, section 8).
  * The strictest speech decision rule decided on a song not in the library for 0 of 707 read recordings (upper end 0.5 percent) and named the right song for 70 percent (60 to 78) of read recordings in the library. It is not in the app and was not measured on singing (ADR-18, docs/09, section 12).
  * On generated music, a live-like re-performance of a library song matched 13 percent (9.7 to 17) by landmark fingerprints and a tempo 5 percent off matched 1 percent. No real band was measured (ADR-19, docs/09, section 13).
  So there is no measured way for the app to tell which song a live band is playing, and wrong lyrics on a screen at a concert cost the person the song.
  What the app already has: songs with lyrics, folders (`Folder`, `Song.folderId`) that group songs without an order, and lyric display. It has no ordered setlist.
  What a concert may give is a closed set. This is assumed and not measured: a setlist is often known in advance (perhaps 15 to 30 songs) and is mostly played in order, so choosing among a few songs would be easier than recognizing one among a library. The sequence simulation below is meant to test what that assumption is worth.
* **Considered Options:**
  1. *Open recognition across the whole library (the recognition screen as it is):* Unmeasured on live singing, and a wrong answer is costly at a concert.
  2. *A closed-set concert mode, manual first:* The person orders a setlist from the library, and the app shows the current song's lyrics, with one tap for the next song, a tap to jump to any song, and manual scrolling. If recognition is ever approved, it only suggests ("Is this X?") and never switches the song by itself. This is the proposal.
  3. *Automatic following:* Recognition switches songs and scrolls the lyrics line by line by itself. Not until it has been measured on real singing in a loud venue, which has not been done.
  4. *Landmark fingerprints:* They match a recording, and on generated music a live-like performance matched 13 percent (docs/09, section 13). Rejected for the concert (ADR-19).
  5. *Online song or setlist lookups:* The network is the thing that fails at a concert. Rejected.
* **Decision (proposed):**
  **Phase 1: concert mode with no recognition.**
  * A setlist is a named, ordered list of library song ids, kept on the device and separate from folders. A song may appear more than once. Deleting a library song removes its entry from every setlist.
  * The concert screen shows the current song's lyrics large and high-contrast and keeps the screen on. It has big controls for the next song, the previous song, jumping to any song of the setlist, and a blank screen, and the lyrics scroll by hand. "Next" on the last song and "previous" on the first do nothing. It does not auto-scroll from LRC timings, because a live tempo differs from the recording.
  * A song with empty lyrics shows a clear "No lyrics" placeholder and still advances.
  * If the artist plays a song not on the list, the person jumps to any library song or to the blank screen without changing the setlist. A wrong or reordered setlist is fixed by hand at the venue.
  * "No microphone" means the screen never starts the speech service, the lyric sync screen or any audio capture, and never asks for `RECORD_AUDIO`. The manifest does not change. Keeping the screen on uses the window flag only, with no wake-lock permission.
  * It works with no network.
  * Tests when built: ordering, next and previous at the ends, jumping, deleting a song in a setlist, a song twice in one setlist, the migration, and that no audio is started.
  **Phase 1 decisions, settled with a second opinion:**
  1. *Storage.* Two Room tables: `setlists(id, name)` and `setlist_entries(id, setlistId, songId, position)`, with indexes on `(setlistId, position)` and on `songId` (the foreign key column that a song delete cascades through), and foreign keys that cascade on deleting a setlist or a song. An entry has its own id because a song may appear twice. Ids are `Int` like the existing entities. `position` is not unique, because a reorder would break the constraint part way; entries are read in order of `position, id`, gaps are allowed (deleting a song cascades and leaves one), and a reorder renumbers the whole setlist in one transaction. The database goes from version 3 to 4 with the first `Migration(3, 4)`, whose SQL is copied exactly from the exported `4.json` (index names and foreign key clauses included) and which only adds the two tables and their indexes, so songs and folders are untouched. The migration is tested with `runMigrationsAndValidate` on a version 3 database (songs and folders survive, the new tables are empty), the `MIGRATIONS` comment ("Empty while version 3 is the first release") is updated, and `4.json` is committed. No non-Room store. A caution to keep: `SongDao.insert` uses `REPLACE`, and replacing an existing id deletes the row first, which would cascade and remove that song's setlist entries. Every insert uses id 0 today and edits use `@Update`; that must stay so, and a test covers it.
  2. *Backup scope (ADR-12).* The setlist tables live in the database, which Auto Backup and device transfer already include (the rules exclude only DataStore and downloaded models), so a setlist follows a restored database with its song ids intact. The encrypted library export carries songs and folders only, and an import gives songs new ids and renames a duplicate title (`VerseViewModel.importLibrary`, `addSongWithConflictResolution`), so a setlist in the export would need an id mapping that is not designed. The export is also encrypted with a key that stays in the device's keystore, so it is not readable on another device or after a reinstall in any case. Setlists are therefore **not** in the library export in Phase 1, and the settings text says so.
  3. *Pro or free (the owner's decision, changed by the owner during review).* Concert mode is **free**: making a setlist, editing it and the concert screen never check Pro or use a trial. Pro is unchanged and keeps its value for everyone else (the free library limit and Pro's unlimited library, folders and the rest). The number of songs is therefore limited as it is today: songs must be in the library, and a free person adds a song only while the library has fewer than 10 songs or a trial is left (`MainScreen`), so a setlist of 15 to 30 songs in practice needs a library that size. Because the concert screen reads no Pro status, a weak or missing connection at a show cannot lock anyone out of it, and the earlier plan for a cached Pro status as a prerequisite is dropped from this change. Caching the last known Pro status is still a worthwhile fix for the paid features that need it offline (adding songs beyond the limit); it is a separate issue and is not part of Phase 1.
  4. *App lock.* The concert screen does not change the lock (ADR-10): the app is unlocked once, and nothing locks it again on the screen turning off or the app stopping. But `isUnlocked` is a plain `remember` in `MainActivity` while the lock settings live in a ViewModel, and the manifest has no `configChanges`, so a rotation or any other configuration change recreates the activity and asks for the lock again. This follows from the code and was not run on a device (#69). A concert screen that re-prompts in the dark is not acceptable, so the fix comes first in the order of work: `isUnlocked` is kept in `SecurityViewModel`, which survives a configuration change and not process death, and not in `rememberSaveable`, which survives process death and would let the app skip the lock against ADR-10. The screen's orientation is not pinned. After process death the person unlocks, then returns to the same setlist, song and scroll position.
  5. *Not losing the place.* The current setlist, song and scroll position are saved on the device (DataStore) and restored after a rotation, a phone call or the process being killed.
  6. *Accidental taps.* The next, previous and jump controls are large (at least 56 dp), jump goes through a list so it takes two taps, and next and previous undo each other. Hardware volume keys are a possible later option and are not part of Phase 1.
  7. *Making a setlist fast.* Songs are added by searching the library, entries are reordered with drag and with move up and down buttons (so TalkBack works), and an entry is removed with an undo. Lyrics get into the library with the existing add and import flows beforehand. There is no online lyric or setlist lookup, which would break offline use and add licensing exposure.
  8. *Readability.* A dark high-contrast theme is the default and a light high-contrast theme is an option for a daytime show, with a text size setting. The app does not change the screen's brightness.
  9. *Permissions.* Concert mode requests no permission. It works with no network, so airplane mode may be suggested to save battery.
  10. *Order of work, one PR each:* this decision; the lock fix for a configuration change (#69); the data layer and its tests; the setlist editor and the concert screen. Then the owner uses it at home and at one show and reports what worked, with a note of the battery used.
  11. *Progress.* Built: the lock fix (#69), the data layer (version 4), the Resume card and the concert screen (#74), and now the editor: rename, move up and down, remove with an undo, and a Concerts step in the tour. The order of work was changed on a second opinion to build the concert screen first. The setlists live in a Setlists section of the Folders tab, so the main screen has only two tabs, Songs and Folders. Phase 1 is complete; the owner's trial at home and at one show is next, and its result decides what to change before any recognition is considered.
  **Later phases, proposed and not decided, each needing its own ADR with its own measured pass bar:** suggestions from heard words, restricted to the setlist, with an ordering prior (the same song continues, or the next one follows, with a small share kept for a song not on the list), shown as a question and never switching the song by itself; and a line follower that moves the lyrics by matching heard words to nearby lines, with manual scrolling as the fallback. Any recognition is new, and no concert suggestion ever opens the voice shortcut or the vocal lock (as ADR-19 requires for fingerprints).
  **Measured before any recognition is built:** the harness restricted to libraries the size of a setlist (for example 15, 20 and 30 songs); a simulator of setlist sequences with the ordering prior and its pass bar fixed before the run; and a trial by the owner at home with a live album they own through a speaker, recording only per-trial results (the song, the suggestion, the time to the right song, wrong switches) and never audio. At a concert the owner would tap the true song as ground truth, so no audio is recorded and bystanders are not captured.
* **Consequences:**
  * Phase 1 helps without any claim the repository cannot back, and works with no network.
  * The person has to tap. That is the cost of not claiming recognition.
  * A setlist must be made by the person from songs that have lyrics. Importing one is not designed, and a library export does not carry setlists.
  * Concert mode is free and checks no Pro status, so it works the same offline and for a lapsed subscriber. What limits a free person is the library size limit, which the owner keeps, not the concert feature. Pro's value is unchanged for people who do not use concert mode.
  * Pro status is still derived only from a live purchase query and is not cached. That affects only the paid features, and is a separate issue.
  * The owner has asked for a Shazam-style app and Phase 1 has no recognition by design. Approving the Phase 1 decisions includes agreeing that this is what is built next.
  * Lyrics are entered by the person, are shown only on their own phone, and are not shared or bundled by this feature.
  * Phase 1 does not use the microphone. If recognition is added, the app listens at a concert: the sound is analyzed on the device and not stored, the privacy text (docs/PRIVACY.md) must say so, and its statement that the app is not directed at children still holds.
  * Keeping the screen on and a bright display cost battery over a long show, and were not measured. A dark, high-contrast theme and large type are part of the design, not an option. Accessibility (TalkBack labels, minimum touch targets) is part of the design and is tested.
  * Not measured, and each is a condition below: singing at a concert, a crowd, a loud room, the ordering assumption, the speech model over a band, a real phone, battery over a show.
* **Conditions before recognition is added:**
  1. The harness results for libraries the size of a setlist and for setlist sequences are reported, with their intervals.
  2. The owner's live-album trial is reported with how few trials it has.
  3. A decision, with a second opinion, on whether a larger speech model is worth its size, delay and battery (roadmap item 8, #50).
  4. The privacy text is updated for listening at a concert.
  5. A separate ADR, with its own measured pass bar, is accepted before any later phase.
  6. The owner approves.

---

## 3. Amendments to Accepted ADRs

These corrections bring the records in line with the code as built.

* **ADR-01 (implementation status):** Only the shared 16 kHz sample rate is implemented. TarsosDSP (`AudioDispatcherFactory.fromDefaultMicrophone`) and Vosk (`SpeechService`) still open their own `AudioRecord`, so the "single shared stream" option was not built. Concurrent capture by one app is permitted on Android 10 and later, but it has not been verified on every device.
* **ADR-03 (implementation):** Tokenized matching exists only in `HybridRecognitionEngine`, which loads all songs and ranks them in memory. No multi-token query was added to `SongDao`. The unused exact-substring query `searchSongsByLyrics` has been removed so the code no longer contradicts this record.
* **ADR-03 (tokenization):** Matching now uses script-aware tokens, described in ADR-14, because the original rule broke Indic scripts, Arabic with diacritics, and Chinese and Japanese.
* **ADR-04 (distance rule):** Unvoiced frames are `NaN` in the relative contour and are skipped when either contour is unvoiced at that frame. Previously a frame silent in only one contour was compared against 0 and inflated the distance. A match also needs at least 8 frames voiced in both contours. Mean-centering still uses the whole live buffer and not only the compared window.
* **ADR-10 (enabling the lock):** The Enable App Lock switch refuses to turn on, and explains why, when the device has no screen lock or biometrics. Because the lock now fails closed, enabling it without a way to authenticate would lock the user out of their own library.
* **ADR-11 and ADR-12 (lifecycle):** The Bluetooth server tracks every listening socket and closes all of them after the first connection or on stop. Before, only the last socket was closed and the other accept thread blocked forever. The location permission is requested on the first-run language screen only, not on every app start.
* **Preferences storage (ADR-10, ADR-12):** The preferences classes take an injectable `DataStore<Preferences>`, and each view model accepts its preferences object. The default is unchanged, so the app still uses one process-wide DataStore. Tests use a private DataStore per test. The three DataStore-backed test classes previously shared one on-disk file through a mock context, so results depended on test order. They also reset the Main dispatcher before cancelling coroutines that still needed it. Tests run on a real single-thread Main dispatcher. Write paths are asserted on the preferences object, and view model state is asserted by creating the view model after the data is saved, as on a cold start.
* **ADR-05 (when speech is matched):** Speech used to be matched only on a fixed 5 second tick that fired together with the pitch tick, and the later trigger cancelled the earlier one. A sentence the speech model had heard in full could be replaced before a successful check. A real-audio run showed this in Korean, Russian, Dutch and Swedish. The engine now searches whenever the speech text changes, and on each pitch tick, and every search reads both the current speech text and the current pitch contour. Candidates found from speech are kept until recognition stops, except that a candidate whose song is deleted in the meantime is dropped.
* **ADR-07 (stopping speech recognition):** `VoskVoiceRecognition.stopListening` and `startListening` are synchronized, and stop takes ownership of the speech service and recognizer before freeing them. Vosk reports the final result on the main thread after a stop, and that callback calls stop again. An overlapping call from another thread freed the native recognizer twice and crashed the process.
* **ADR-10 (authenticators):** androidx.biometric rejects `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` on Android 9 and 10 (API 28 and 29). Checking availability reports "unsupported", and building a prompt with it throws. `appLockAuthenticators` returns `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` on those two versions and the strong pair elsewhere, and both the Settings switch and the unlock prompt use it. The original startup bypass had hidden this problem, because the prompt was never reached. It is tested for every Android version, and on emulators it was run on Android 9 (API 28), Android 10 (API 29) and Android 16 (API 36): the Settings switch is accepted, the system prompt appears, and the device PIN unlocks the app. Not yet run on a physical device (issue #8).
* **ADR-12 (corrupt settings):** A corrupt settings file used to crash the app on every launch, on the original code as well. The file is now replaced with empty settings through a corruption handler, and reads that fail with an I/O error report empty settings. After corruption every setting returns to its default, including the app lock, because the lock lives in the same file. Only someone who can already change the app's private files can cause this.
* **ADR-12 (migrations):** Raising the database version without a `Migration` crashes at launch for existing users. This is deliberate and documented in `VerseDatabase`. `VerseDatabaseMigrationTest` creates a database from the exported schema of version 3 and opens it with the current class. It was shown to fail with "A migration from 3 to 4 was required but not found" when the version was raised without a migration.
* **ADR-12 (first migration, ADR-20):** The database is at version 4. `MIGRATION_3_4` adds the `setlists` and `setlist_entries` tables and their two indexes and touches nothing else, so songs and folders are kept. Its SQL is copied from the exported `4.json`, and `VerseDatabaseMigrationTest` runs `runMigrationsAndValidate` on a version 3 database, which makes Room check the migrated database against that schema (TC-M-10). Setlist entries are deleted with their setlist or their song, so a song must never be written with `REPLACE` on an existing id; inserts use id 0 and edits use `@Update`, and a test covers it (TC-M-26). Setlists are in Auto Backup with the database and are not in the library export. Not run on a physical device (issue #8).
* **ADR-11 (transfer and unpacking):** The sender checks the payload size and tells the user how large the selection is and what the limit is. The unused resume code and the stored transfer progress were removed. A model archive is unpacked into a staging folder and swapped in only when every entry succeeded, so a failed re-download keeps a working model. A file that is not a zip reads as an archive with no entries, so an archive with no files now counts as a failure. Unpacking now runs in a WorkManager job (`ModelUnpackWorker`) that the completion receiver queues, because `goAsync` does not lift the time limit that broadcast receivers have. The job is stored, so it continues if the process is killed (it restarts by itself) and is safe to run twice. A 1.6 GB test model finished installing after the app process was killed halfway (TC-M-15).
* **ADR-08 (refresh):** Google sends no callback when a subscription expires, so purchases are re-checked each time the app returns to the foreground. `launchPurchaseFlow` returns false when the purchase screen cannot open, and the Tip Jar says so. The Tip Jar shows the subscription's price.
* **ADR-05 (search cost):** Searching on every speech change cost about 76 ms for 1,000 songs on a fast PC, because each search re-read the list and re-tokenized every lyric. The song list is now read once and follows Room changes, each song's tokens are cached while its lyrics are unchanged (about 0.11 ms per search), and pitch is re-scored only on its own tick. The speech recognizer and pitch interval are constructor parameters, so tests need neither reflection nor long waits.
* **ADR-04 (silence threshold):** A frame whose level is below -55 dB (RMS, relative to full scale) is given no pitch, whatever the detector would say, and is not run through it (`AudioFingerprinter`, `AudioLevel.SILENCE_DB`). Before, a faint steady hum, about -73 dB on the emulator microphone, was reported as a voiced 470 Hz tone and made up most of the voiced frames in a fingerprint. Speech is about -40 dB or louder, and on a real recording the threshold removed 1 of 51 voiced frames. Fingerprints saved earlier keep any hum they recorded, and re-syncing a song gives a clean one. The threshold has only been tried on an emulator.
* **ADR-03 (evidence for speech):** A song is a speech candidate only if the share of heard words found in its lyrics is at least 0.35 and at least two different heard words are found (all of them, if the lyrics have fewer than two different words). Before, the share alone decided, so one heard word that was in the lyrics scored 1.0 and matched. Chinese and Japanese are matched in overlapping pairs of characters, so two tokens means three characters. On the emulator, with one sentence per language, 15 of 21 languages passed with the new rule, and of the 6 that failed, 4 passed on the same day with the old rule (English, Korean, Portuguese and Italian). Most passes sit exactly at two words. Single runs on that setup are noisy, so treat this as a rough cost of about four languages out of 21. The engine also reports how strong each match was (`MatchEvidence`: the most different words found in one piece of speech, and the best pitch distance with the frames it used).
* **ADR-04 (pitch threshold):** The pitch match threshold is 2.5 semitones, down from 3.0 (3.0 for Sanskrit, down from 3.5). On two real recordings of different sentences, the old setting accepted a different clip in 5 of 12 comparisons and the new one in 1 of 12, while the same clip was accepted in 27 of 54 comparisons instead of 31 of 54. Those clips are short and spoken, so this is a small sample.
* **ADR-10 (vocal lock evidence):** The lock opens only when the target song has been heard with at least three different words of its lyrics, or the voice follows its pitch within 2.0 semitones over at least 30 frames sung in both (about two seconds). Recognition keeps the looser rules, because a wrong candidate there only shows a name, while the lock keeps casual access out. On the same two recordings no window of a different clip reached the lock rule.
* **ADR-03 (scoring and lead, issue #44):** Every song is scored against the heard words at once. Each different heard word found in a song's lyrics is weighted by how few songs in the library contain it, `ln(1 + songs / songs containing the word)`, so a word every song has counts for little. The engine reports that weight and the strongest other song's weight with each match (`MatchEvidence.speechWeight` and `rivalWeight`). Which songs are candidates is unchanged. A heard word within one letter of a lyric word (two for words of nine letters or more, and never for words under five letters) can be counted as that word (`SpeechRules.fuzzy`), but it is off by default, because nothing yet shows it helps. The reason for the change: in a trial of music played into the emulator microphone, two library songs tied on two common words and a song that was not in the library was offered. That trial was two songs and one run each on an unstable rig, so it shows a failure and not a rate.
* **ADR-10 (lead over other songs, issue #44):** The words path of the lock also needs the target song to lead: its weight at least 25 percent above the strongest other song's (`SpeechMatcher.LEAD_MARGIN`). Two songs tied on the heard words, or close, keep the lock closed for both, and the user falls back to the device lock after two attempts. A library with two songs that share a chorus will therefore not open on the words of that chorus. The pitch path is unchanged. The 25 percent is provisional: the evaluation seed cannot choose it, and it is to be set from the real evaluation set (`Sweep`, docs/09).
* **ADR-10 (name and copy, issue #48):** The feature is shown to people as the voice shortcut, never as authentication or identity, and its words are in one place (`LockCopy`) and tested (TC-U-47). The settings option says in its own description that anyone who knows the words can open it, and the device lock stays as the fallback and the only protection. This widens the consequence above: the shortcut is satisfied not only by a recording of the song but by anyone who knows or can find the words, and the lock screen names the song. The stored lock type is still `VOCAL`. The threat model is in docs/10-voice-shortcut-threat-model.md. The false-accept rate on a negative set of television, music, other voices and silence has not been measured, and no number is claimed.
* **ADR-10 (attempts and the device lock, issue #60):** An attempt is one stretch of listening. It ends when the person taps the microphone again or after 10 seconds, and if it did not open the shortcut it counts as failed, including one in which nothing was heard. After two failed attempts the device lock takes over (`VoiceShortcutAttempts`, `VocalLockPolicy.MAX_ATTEMPTS`). The "Use device lock" button opens the device prompt at once and is not an attempt. After either, the shortcut is not offered again until the app restarts, and a dismissed prompt shows the existing locked screen with its Unlock button. A match opens the shortcut only while it is listening, because the recognizer gives a last result after it is stopped. The microphone is stopped on every way out of listening and when the screen is left. The count of failed attempts is saved across a rotation. Before this, the only thing that counted an attempt was the device lock button, so its first tap did not open the prompt, and the microphone stayed on after the shortcut opened. Still open: rotation locks the app again and picks another song to say ([#63](https://github.com/Sriram-Codes-SW/VerseSync/issues/63)), the microphone and recognizer outlive the view model ([#62](https://github.com/Sriram-Codes-SW/VerseSync/issues/62)), and the screen loads no speech model ([#64](https://github.com/Sriram-Codes-SW/VerseSync/issues/64)). The screen wiring was run on an emulator (`VoiceShortcutScreenInstrumentedTest`), not the whole app flow with a real device prompt.
* **ADR-10 (rotation, issues #63 and #69):** The unlocked flag is kept in `SecurityViewModel` and not in the composition, so a rotation or any other configuration change no longer asks for the lock again. Before this, `isUnlocked` was a plain `remember` and the manifest has no `configChanges`, so rotating the phone after unlocking recreated the activity and showed the device prompt again, which was reproduced on an Android 16 (API 36) emulator. The flag is deliberately not `rememberSaveable`, which survives the process being killed and would let the app skip the lock: a new view model, as after a cold start, is locked (TC-U-51, TC-M-25). Still open in #63: for the voice shortcut, `targetSong` is still a plain `remember`, so rotating while the shortcut is showing picks another song to say. Not run on a physical device (issue #8).
* **ADR-10 (the microphone stops with the screen, issue #62):** `HybridRecognitionViewModel` is shared by the whole activity and had no `onCleared`, so the microphone and the recognizer could outlive the screen, and the Recognize Song screen stopped listening only when its button was pressed. Now the engine and the speech wrapper have a `release()` that the view model calls when cleared; the Recognize Song screen stops listening when it is left and when the app goes to the background; and the voice shortcut stops when the app goes to the background through `VoiceShortcutAttempts.interrupted()`, which returns it to idle without counting a failed attempt. The stop on going to the background was seen in the audio service log on an emulator. Not run on a physical device (issue #8).

---

## 4. Known Open Items

* **Transfer integrity (TC-U-10):** The transfer protocol has no checksum. Integrity relies on the announced length and JSON parsing. Adding a checksum needs a new protocol version. ([#10](https://github.com/Sriram-Codes-SW/VerseSync/issues/10))
* **Licensing:** Resolved by ADR-13, which licenses the project under GPL-3.0 because the bundled TarsosDSP jar is GPL-3.0. Revisit if TarsosDSP is replaced.
* **Final Pro products:** Only the `pro_subscription` product exists, as a deliberate stand-in (ADR-08). The one-time Pro purchase and the Pro+ tier described in the README are planned, and the free and Pro split above is the basic differentiation until they are set up. ([#12](https://github.com/Sriram-Codes-SW/VerseSync/issues/12))
* **Resumable transfer:** Not supported until partial data is persisted (ADR-11). ([#10](https://github.com/Sriram-Codes-SW/VerseSync/issues/10))
* **State flow after a live write:** In unit tests, a view model state flow occasionally did not show a value written just after the view model started, even though the DataStore held the correct value. It happened about 1 in 10 times under test dispatchers and was not seen in 100 runs with a real Main thread. DataStore 1.3.0 reduced it but did not remove it. A later diagnostic showed the write itself succeeded: the stored value was correct and a fresh read returned it, but a collector that was already waiting never received it. That points at DataStore 1.1.1, so DataStore was updated to 1.3.0, after which 54 runs of the four DataStore-backed test classes passed, against 3 failing runs out of 26 on 1.1.1. The cause is still not identified, and the behavior on a device has not been checked. An emulator run (TC-M-06, TC-M-07) showed the trial counter, the lock switch and the lock type label updating immediately, so this has not been seen in the app. ([#19](https://github.com/Sriram-Codes-SW/VerseSync/issues/19))
* **Real-audio coverage:** Sanskrit was not tested with real audio, and every other language was tested with one sentence only (see the results section of the test strategy). ([#11](https://github.com/Sriram-Codes-SW/VerseSync/issues/11))
* **Pitch detection independence from TarsosDSP:** Phases 0 and 1 of the plan are done: TarsosDSP is used in one adapter file behind interfaces. Phase 2 (one shared audio capture), phase 3 (an in-house detector) and phase 4 (removing the library) remain, see [07-pitch-detection-design.md](07-pitch-detection-design.md) ([#13](https://github.com/Sriram-Codes-SW/VerseSync/issues/13)). It would also finish what ADR-01 intended.
* **Real devices:** Bluetooth sharing between two phones and the biometric prompt have never run, because the emulator has neither ([#8](https://github.com/Sriram-Codes-SW/VerseSync/issues/8)).
* **Matching enhancements, not yet needed:** Elastic (DTW) pitch matching for tempo variation ([#16](https://github.com/Sriram-Codes-SW/VerseSync/issues/16)) and an FTS5 index for very large libraries ([#17](https://github.com/Sriram-Codes-SW/VerseSync/issues/17)).
* **First-release checklist:** Play Console setup, the license decision, the privacy policy and release signing ([#18](https://github.com/Sriram-Codes-SW/VerseSync/issues/18)).
* **Matching evidence:** The speech and pitch rules were tightened and the lock asks for more, including a lead over the other songs (ADR-03, ADR-04, ADR-10, issues #41 and #44), after finding that one heard word, or a pitch score of 2.8 against 3.0, could open it. The cost is recall: pitch matching of short spoken clips was already weak and is weaker, and speech needs a second correctly heard word. The thresholds were set from two real recordings and need more data, and elastic matching might recover recall ([#41](/41), [#16](/16), [#33](/33)). The stricter rule has only been measured on an emulator. Measurements on read speech against libraries of 92 and 155 songs (docs/09, sections 8 and 10) found that the current speech rule decides on a song for 38 percent, and on the larger held-out corpus for 30 percent (26 to 35), of recordings of songs not in the library, and that pitch finds 16 percent of recordings that are pieces of their own reference. A decision rule chosen on other recordings and reported once on a fresh corpus (at least 4 words, at least 80 percent of heard words, a rarity gap of 1.0 times ln(1 + songs)) decided on a song not in the library for 0 of 707 read-speech recordings, but names the right song less often (70 percent against 90) and is not in the app; adopting it needs an ADR and a check on real singing (docs/09, section 12). The rules are not yet changed.
* **Biometric library is a beta:** The release build uses `androidx.biometric` 1.4.0-beta01, because there is no stable version newer than 1.1.0 from 2020 (ADR-17). Revisit when 1.4 reaches a stable release.
* **Downloads that finish while the app is closed:** The receiver that notices a finished download only exists while the app is open, so a model that finishes downloading with the app closed is never unpacked ([#36](https://github.com/Sriram-Codes-SW/VerseSync/issues/36)).
