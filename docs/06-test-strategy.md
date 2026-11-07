| Field | Value |
|---|---|
| Document | VerseSync Test Strategy & Verification Matrix |
| Version | 0.5 |
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
