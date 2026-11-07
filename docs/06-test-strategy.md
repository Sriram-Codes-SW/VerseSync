| Field | Value |
|---|---|
| Document | VerseSync Test Strategy & Verification Matrix |
| Version | 0.1 |
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
| **TC-U-10** | Bluetooth / Transfer | Data transfer service chunk verification | All bytes transferred with valid checksum and progress updates | N/A |
