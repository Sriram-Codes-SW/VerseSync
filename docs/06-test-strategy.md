| Field | Value |
|---|---|
| Document | VerseSync Test Strategy & Verification Matrix |
| Version | 0.9 |
| Date | 2026-11-08 |
| Author | Sriram-Codes-SW |
| Status | Active |

# VerseSync Test Strategy & Verification Matrix

## 1. Scope & Objectives

This document establishes the verification strategy for audio capture, pitch fingerprinting, offline speech-to-text integration, and song matching in VerseSync.

## 2. Test Cases Matrix

| Test ID | Area | Description | Expected Outcome | ADR Trace |
|---|---|---|---|---|
| **TC-U-01** | Database / Converters | Convert null and empty string to float list | Returns `null` or empty list without throwing `NumberFormatException` | ADR-06 |
| **TC-U-02** | Database / Converters | Convert valid comma-separated floats to list and back | Round-trip identity preserved (`[100.5, 200.0]`) | ADR-06 |
| **TC-U-03** | Engine / Matching | Relative semitone conversion on standard musical frequencies (A4 = 440 Hz, C4 = 261.63 Hz) | Accurate MIDI semitone numbers (A4 = 69, C4 ≈ 60) | ADR-04 |
| **TC-U-04** | Engine / Matching | Key transposition invariance between two identical melodies sung in different keys | Score reflects strong match despite constant key offset | ADR-04 |
| **TC-U-05** | Engine / Matching | Silence resilience: matching pitch sequences with intermittent 0 Hz silence frames | Distance calculation filters unvoiced frames rather than corrupting alignment | ADR-04 |
| **TC-U-06** | Engine / STT | Tokenized lyrics search with punctuation and newline differences | Spoken query `"amazing grace how sweet"` matches formatted lyrics `"Amazing Grace! How sweet..."` | ADR-03 |
| **TC-U-07** | Engine / Lifecycle | Start recognition when Vosk model is not downloaded | Engine starts pitch processing and does not crash or exit silently | ADR-02 |
| **TC-U-08** | Engine / State | Consecutive recognition triggers retain previously found candidates | Candidates do not get cleared when alternate heartbeat fires | ADR-05 |
| **TC-U-09** | Engine / Capture | Stop recognition cleans up dispatcher and recording threads | Audio resources released, threads terminated cleanly | ADR-01 |
| **TC-U-10** | Bluetooth / Transfer | Data transfer service chunk verification | All announced bytes are transferred and decoded and progress is reported. No checksum is implemented (see section 4 of the design document) | N/A |
| **TC-U-11** | Model Download / Extraction | Archive entries with `..` segments, or resolving to a sibling directory with the same name prefix, are rejected; nested and redundant `.` paths inside the target are allowed | `resolveInside` returns null for escaping paths and a canonical path otherwise | ADR-11 |
| **TC-U-12** | Bluetooth / Transfer | Oversized or negative announced size; stored resume offset; sender receives a refusal | Receiver returns null and replies `-1` without allocating; always restarts at offset 0; sender returns false | ADR-11 |
| **TC-U-13** | Library / Inbound Songs | Incoming song carries a foreign id and folder id that collide with local data | Inserted with a new id and the supplied local folder id; the local song is untouched; duplicate titles are renamed | ADR-09 |
| **TC-U-14** | Security / Startup | Lock configuration before saved settings have loaded | `lockConfig` is null, so the UI cannot treat the default as "lock disabled" | ADR-10 |
| **TC-M-01** | Release Build / Vosk | Install a minified release build with a model present and open the recognition screen | App does not crash and the model loads | ADR-07 |
| **TC-M-02** | Release Build / Serialization | Inspect the R8 mapping for `Song`, `Folder` and `com.sun.jna.Structure` | Class and field names are unchanged | ADR-07 |
| **TC-M-03** | Models / Download | Download a language model on a device or emulator | Archive unpacks off the main thread to `files/models/model-<code>` | ADR-11 |
| **TC-U-15** | Engine / Matching | Live take with too little voiced material shared with the reference, or entirely silent | Match score is no match (`Float.MAX_VALUE`) | ADR-04 |
| **TC-M-04** | Security / Cold start | Enable the app lock, force-stop the app and start it five times on an emulator | The library is never shown; the locked screen with an Unlock button appears each time | ADR-10 |
| **TC-M-05** | Security / Settings | On a device with no screen lock, tap Enable App Lock | The switch stays off and a message explains that a screen lock is needed | ADR-10 |
| **TC-M-06** | Trials / Live update | In a non-Pro build, use a trial from the Folders tab | The trial badge and the dialog count decrease immediately (3, 2, 1) | ADR-08 |
| **TC-M-07** | Settings / Live update | Enable the lock and change the lock type in Settings | The switch and the lock type label change immediately | ADR-10 |
| **TC-U-16** | Engine / Matching | A spoken phrase and the saved lyrics of the same phrase in each of the 29 supported languages, with punctuation, diacritics and word spacing as each source would produce | Score is at or above the 0.35 threshold in every language | ADR-14 |
| **TC-U-17** | Engine / Matching | Speech from one language scored against the lyrics of the next language in the table | Score is below the threshold, so there are no false matches | ADR-14 |
| **TC-U-18** | Engine / Matching | Speech in decomposed Unicode form against composed lyrics, and a partial phrase | Still matches | ADR-14 |
| **TC-U-19** | Languages / Coverage | Language list compared with the sample phrase table | Every configured language has a sample, codes are unique and model URLs use HTTPS | ADR-14 |
| **TC-U-20** | Engine / Matching | Speech heard for under a second and replaced before the next pitch tick, with realistic database latency | The song is still recognized | ADR-05 |
| **TC-U-21** | Languages / Models | Languages without a published model, and downloads for them | Exactly seven languages have no model, they do not count as missing one, and their download does nothing | ADR-15 |
| **TC-M-08** | Engine / Real audio | Run `tools/audio-test/run_lang.sh <code>` for each language with real speech played into the emulator microphone | The Target song is recognized and the Decoy is not (results in section 3) | ADR-14, ADR-15 |
| **TC-M-09** | Emulator / Audio path | Run `MicrophoneLevelInstrumentedTest` while a recording plays into the cable | The reported peak level rises well above the idle level | ADR-15 |

## 3. Real-Audio Results (2026-11-08)

Method: a real recording of one sentence per language, from Google FLEURS (CC BY 4.0), was played into an emulator's microphone through a virtual audio cable (`tools/audio-test`). The real recognition engine and the real small Vosk model for that language ran in the app. The sentence was the lyrics of a "Target" song, and an unrelated "Decoy" song was also in the library. A language passes when the Target is recognized and the Decoy is not.

| Result | Languages |
|---|---|
| Passed (21) | English, Hindi, Telugu, Gujarati, Arabic, Korean, French, Spanish, German, Russian, Chinese, Japanese, Portuguese, Turkish, Vietnamese, Italian, Dutch, Polish, Swedish, Ukrainian, Farsi |
| No speech model exists (7) | Tamil, Marathi, Bengali, Kannada, Malayalam, Punjabi, Greek |
| Not tested (1) | Sanskrit, which uses the Hindi model and has no recording in FLEURS |

Notes:

* The first run passed 15 languages. Five of the six failures were the engine matching speech only on a fixed tick (ADR-05 amendment), and they passed after the fix. The sixth, Vietnamese, heard the sentence wrongly. It passed with a different sentence, so the first sentence was a model miss and not a bug.
* One sentence per language is thin evidence. The small models also transcribe imperfectly, and several passed with an inexact transcript, because the match threshold is 0.35.
* Chinese and Japanese pass through character-pair matching (ADR-14), which could match common pairs by chance.
* Swedish uses the 303 MB model and Ukrainian the 78 MB model from ADR-15. Both were exercised by this run.
| **TC-U-22** | Settings / Corruption | A truncated settings file, writing afterwards, an I/O error while reading, and a non-I/O error | The file is replaced and settings default; writes work; I/O errors report defaults; other errors are not hidden | ADR-12 |
| **TC-U-23** | Security / Authenticators | The authenticators chosen for API 26 to 36 | API 28 and 29 use the weak pair, all others the strong pair | ADR-10 |
| **TC-U-24** | Models / Install | Install over an existing model with a good, malicious and corrupt archive | A good archive replaces it; a bad one leaves the old model working | ADR-11 |
| **TC-U-25** | Bluetooth / Size check | A small selection, a selection over 8 MiB, and the limit itself | Only a selection over the limit is reported as too large | ADR-11 |
| **TC-U-26** | Engine / Matching | Speech of one language against the lyrics of every other language that shares its script | No pair reaches the match threshold | ADR-14 |
| **TC-M-10** | Database / Migration | Run `VerseDatabaseMigrationTest` on a device or emulator, and again after raising the version without a migration | Passes at version 3; fails with a missing-migration error when the version is raised alone | ADR-12 |
