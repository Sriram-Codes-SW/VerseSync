| Field | Value |
|---|---|
| Document | VerseSync Pitch Detection Design and Refactor Plan |
| Version | 0.1 |
| Date | 2026-11-08 |
| Author | Claude Code |
| Status | Proposed |

# Pitch Detection Design and Refactor Plan

Tracked in issue [#13](https://github.com/Sriram-Codes-SW/VerseSync/issues/13).

## 1. Why

Pitch detection and the audio capture it depends on are tied to one third-party library, and that causes four problems today.

1. **Licensing.** TarsosDSP is GPL-3.0. While it is bundled, the app has to be GPL-3.0 too (ADR-13, pull request #2). If the owner later wants a closed-source or differently licensed app, the library has to go, and today that would mean rewriting several classes at once.
2. **Testability.** The engine and the fingerprint view model create an audio dispatcher and a thread themselves. Tests have to mock library classes, set private fields by reflection, or use a real microphone.
3. **Two recorders.** TarsosDSP opens one `AudioRecord` and Vosk's `SpeechService` opens another. ADR-01 chose a shared sample rate, not a shared stream, so two capture sessions still run at once.
4. **Small defects found while mapping this.** The level meter shows pitch confidence and not loudness (#14), and `FingerprintingViewModel` is unused dead code at a conflicting sample rate (#15).

The goal is that the matching and fingerprinting logic does not know which pitch detector, or which audio library, is underneath.

## 2. How it works today

```
Microphone
  |-- AudioRecord #1 (TarsosDSP AudioDispatcher, 16 kHz, 1024 samples)
  |      -> PitchProcessor (YIN) -> AudioFingerprinter -> fingerprint, instantPitch, "amplitude"
  |
  |-- AudioRecord #2 (Vosk SpeechService, 16 kHz)
         -> Recognizer -> speech text
```

Where TarsosDSP is used (all under `app/src/main/java/dev/sriramcodessw/versesync`):

| File | What it does with the library |
|---|---|
| `engine/HybridRecognitionEngine.kt` | Builds the `AudioDispatcher`, starts its thread, attaches the pitch processor when the user is Pro |
| `fingerprinting/AudioFingerprinter.kt` | Wraps `PitchProcessor` with the YIN algorithm and turns each result into a fingerprint frame |
| `fingerprinting/FingerprintCreationViewModel.kt` | Builds its own dispatcher and thread for lyric sync, and re-publishes pitch and "amplitude" |
| `fingerprinting/FingerprintingViewModel.kt` | Unused. Opens a 22,050 Hz dispatcher |

Already independent of the library: `hzToSemitone`, `toRelativeSemitoneContour`, and the whole matcher (`calculateMatchScore`, the distance rule, the 3.0 semitone threshold). The refactor moves only the capture and detection edge.

**The stored data.** A song's fingerprint is a list of floats in Hz, one per frame of 1,024 samples at 16 kHz, which is 64 ms and about 15.6 frames a second. An unvoiced frame is stored as 0. The matcher compares windows of equal length, so it relies on that frame rate. Anything that replaces the detector has to keep it.

## 3. Goals and non-goals

Goals:

* The domain logic (contours, matching, fingerprints) has no Android and no third-party audio types.
* Any pitch detector can be swapped in behind one small interface, with a test that proves it matches the old one.
* One microphone capture feeds pitch, speech and the level meter.
* Engine and view-model tests need no microphone, no reflection and no real-time waits.
* Existing users' stored fingerprints keep working.

Non-goals:

* Changing the matching algorithm or its thresholds. That is a separate decision (see #16 for elastic matching).
* Changing the frame rate of stored fingerprints, unless a versioned migration is added (section 6).
* Choosing a license. This design makes the choice reversible, and ADR-13 stays as it is.

## 4. Target design

Three layers, with arrows pointing inward. The domain knows nothing about the other two.

```
                +-----------------------------------------------+
                | Domain (plain Kotlin, JVM tests)              |
                |  PitchFrame, Fingerprint, SemitoneContour     |
                |  ContourMatcher, lyric matching               |
                +-----------------------------------------------+
                        ^                          ^
                        | uses                     | uses
        +-------------------------------+   +------------------------------+
        | Pipelines                     |   | Ports (interfaces)           |
        |  PitchPipeline                |-->|  AudioFrameSource            |
        |  SpeechPipeline               |   |  PitchDetector               |
        |  LevelMeter                   |   +------------------------------+
        +-------------------------------+            ^            ^
                        ^                            | implements | implements
                        | collects                   |            |
        +-------------------------------+   +-----------------+  +--------------------+
        | Engine, view models, UI       |   | Android adapter |  | Detector adapters  |
        +-------------------------------+   | AudioRecord     |  |  TarsosYin (temp)  |
                                            | frame source    |  |  InHouseYin        |
                                            +-----------------+  |  SyntheticSource   |
                                                                 +--------------------+
```

### 4.1 Types and ports

```kotlin
// Domain
class AudioFrame(val samples: ShortArray, val sampleRate: Int, val index: Long)
data class PitchFrame(val index: Long, val hz: Float, val confidence: Float) // hz 0 = unvoiced
class Fingerprint(val hz: List<Float>, val frameSamples: Int, val sampleRate: Int)

// Ports
interface AudioFrameSource {
    val frames: Flow<AudioFrame>      // started and stopped by collecting
}
interface PitchDetector {
    val frameSamples: Int             // 1024 today
    fun detect(frame: AudioFrame): PitchFrame
}
```

`PitchDetector.detect` is a plain function on a block of samples. That is what makes it easy to test. TarsosDSP can be called this way too: its jar contains `be.tarsos.dsp.pitch.Yin`, with a `getPitch(float[])` method, so the temporary adapter needs no `AudioDispatcher`.

### 4.2 One capture, several consumers

A single `AudioRecord`-based `AudioFrameSource` at 16 kHz fans frames out to:

* `PitchPipeline`: runs the `PitchDetector`, exposes the latest `PitchFrame` and accumulates the `Fingerprint`.
* `SpeechPipeline`: feeds the same samples to Vosk with `Recognizer.acceptWaveForm`, which `SpeechService` already uses internally, and turns `getPartialResult` and `getResult` into the existing text flow. This replaces `SpeechService`, which is the second recorder.
* `LevelMeter`: computes loudness (RMS) from the samples, which fixes #14.

This completes the intent of ADR-01. Recognition then has one capture session, and the lyric-sync screen reuses the same pipeline instead of building its own.

### 4.3 What changes for callers

* `HybridRecognitionEngine` takes a `PitchPipeline` and a `SpeechPipeline`. It no longer creates a dispatcher or a thread.
* `FingerprintCreationViewModel` takes a `PitchPipeline` and publishes its flows.
* `AudioFingerprinter` becomes a pure accumulator of `PitchFrame` values.
* Tests pass a `SyntheticToneSource`, which produces sine tones, vibrato, silence and noise from a fixed seed. No microphone and no waiting.

## 5. Options for the detector

| Option | License | Effort | Accuracy risk | Notes |
|---|---|---|---|---|
| A. In-house YIN | Ours | Medium | Low to medium, and measurable | YIN is a published algorithm (de Cheveigne and Kawahara, 2002). At 16 kHz, for 80 to 1,000 Hz, the lags are 16 to 200 samples, so a frame costs about 200,000 multiplications, which is negligible at 15.6 frames a second |
| B. Another library | Must be permissive, such as Apache-2.0, MIT or BSD | Low to medium | Unknown until tried | Check license, Android support and upkeep before adopting. No candidate has been evaluated |
| C. Commercial license for TarsosDSP | Negotiated | Low | None | Depends on the author. The bundled jar has no recorded source or version beyond a 2016 snapshot |
| D. Keep TarsosDSP under GPL-3.0 | GPL-3.0 | None | None | The current state (ADR-13) |

Recommendation: A, behind the `PitchDetector` port, with B only if A cannot reach parity. Because of the port, the choice can be made late and changed again. D stays valid until then.

## 6. Data compatibility

* Keep the frame size (1,024 samples), the sample rate (16 kHz) and the "0 means unvoiced" rule. Then existing fingerprints and the matcher keep working with no migration.
* If a future detector needs a different frame rate, add a `fingerprintFrameSamples` column through a Room migration (see `VerseDatabase`), treat songs without it as the current rate, and offer to re-sync old songs. That is outside this plan.

## 7. Phases

Each phase can ship by itself and leaves the app working.

| Phase | Work | Size | Done when |
|---|---|---|---|
| 0 | Characterization tests for the current behavior, and delete the dead `FingerprintingViewModel` (#15) | Small | Tests describe today's output for tones, silence, vibrato and octave jumps |
| 1 | Add the domain types and ports, wrap TarsosDSP as an adapter, and move the engine and lyric sync to the ports. No behavior change | Medium | No class outside the adapter imports `be.tarsos`. Tests use the synthetic source |
| 2 | One shared capture. `SpeechPipeline` replaces `SpeechService`, and the level meter uses RMS (#14) | Medium | One `AudioRecord` is open during recognition, and the real-audio language test still passes |
| 3 | In-house YIN with parity tests against the TarsosDSP adapter, selectable by a build setting | Medium to large | The parity criteria below are met |
| 4 | Remove the TarsosDSP jar and update the notices. Then revisit ADR-13 | Small | The build has no TarsosDSP, and the notices are current |

Parity criteria for phase 3, proposed and to be tuned on real data:

* Voiced or unvoiced agreement on at least 95 percent of frames.
* On frames both call voiced: median error at most 20 cents and 95th percentile at most 50 cents.
* Octave errors on at most 2 percent of voiced frames.
* End to end: for the test pairs, the matcher's distance differs by at most 0.3 semitone between the two detectors.

## 8. Testing

* **Domain:** JVM unit tests, as for the matcher today.
* **Detector:** synthetic signals with known pitch (sine, vibrato, glides, noise, silence, octave jumps) and a set of recorded sung clips. The same frames go to both detectors, which is possible because both are `PitchDetector`.
* **Pipelines and engine:** the synthetic source, with a short interval in place of real-time waits, as the engine tests already do.
* **Real audio:** extend `tools/audio-test` with a sung-melody clip played into the emulator microphone, so the whole chain is checked after each phase.
* **Performance:** a budget of a few milliseconds per frame on a mid-range device.

## 9. Risks

| Risk | Mitigation |
|---|---|
| The new detector is less accurate on real singing | Parity tests on recorded clips before switching, and the old adapter stays available until phase 4 |
| Feeding Vosk directly loses behavior `SpeechService` provides, such as its threading and result callbacks | Phase 2 reuses the existing text flow, and the per-language real-audio test must still pass |
| Latency or dropped frames from sharing one capture | Bounded buffers, a frame counter in tests, and a check for overruns in the real-audio test |
| Matching thresholds need retuning after a detector change | The parity criteria include end-to-end distance, and the thresholds stay in one place |
| The work is larger than it looks | Each phase ships alone, and only phase 3 carries accuracy risk |

## 10. Open questions

* Should the library choice wait until the license decision (#2) is made, or should phases 0 to 2 start now? They are worth doing either way, because they fix the testability and capture problems.
* Is plain YIN enough for singing, or is a probabilistic variant needed for noisy rooms?
* What frame rate would the matcher prefer if stored fingerprints could be regenerated?
* Does Vosk accept the same 16 kHz samples for every language model, or do some need different handling?

## 11. Effect on existing records

* ADR-01 is completed by phase 2.
* ADR-04 (semitone contours and the distance rule) is unchanged.
* ADR-13 is revisited at the end of phase 4.
