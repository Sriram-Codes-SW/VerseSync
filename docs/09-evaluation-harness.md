| Field | Value |
|---|---|
| Document | VerseSync Evaluation Harness |
| Version | 0.2 |
| Date | 2026-11-17 |
| Author | Claude Code |
| Status | Active |

# Evaluation Harness

Item 1 of the [quality roadmap](08-quality-roadmap.md), issue [#43](https://github.com/Sriram-Codes-SW/VerseSync/issues/43).

## 1. What it is for

Until now recognition was checked by playing one sentence per language into an emulator and seeing whether it passed. That is noisy (the same build gives different results between runs) and says nothing about how often a wrong song is accepted. Every later item in the roadmap is judged by numbers from this harness, so it has to give the same numbers every time.

The idea is to record once and replay many times. Recordings are replayed on the JVM, with no phone and no microphone, through the same rules the app uses. Only what the speech recognizer heard has to be captured on a device, once per recording.

## 2. What is measured

For one group of recordings (a slice) and one evidence path (speech, pitch or both combined):

| Measure | Meaning |
|---|---|
| found | The right song is among the candidates. Recognition shows candidates and a person picks. |
| top-1 | The right song is strictly the strongest candidate. A tie counts as a miss. More heard words is stronger, then a closer pitch. |
| false accept | A recording of a song that is not in the library still produced a candidate. This is the number that matters most for trust. |
| lock opens | The vocal lock would open for the right song, using the real `VocalLockPolicy`. |
| lock false | The vocal lock would open for a song that was not the one sung. Each wrong song of each recording is one trial, so the trials are not independent and the interval is optimistic. |
| latency | Median time until the right song first appeared, among recordings where it was found. |

Every rate is shown as `hits/total = percent (low to high)`, a 95 percent Wilson interval. With few recordings the interval is wide, which is the point. Knowing a 1 percent false-accept rate to within half a point takes about 1,500 trials per condition, and to within one point about 400. A small corpus can show that a rate is bad but cannot show that it is good.

Slices are by language, mode (sing or speak), condition, device and speaker group.

## 3. How it replays the app

| Part | How |
|---|---|
| Pitch | The recording is read, resampled to 16 kHz and cut into 1,024-sample frames. Each frame goes through the app's `AudioFingerprinter` and `TarsosPitchDetector`. Every 5 seconds the contour so far is scored against each song with `PitchMatcher`, and the best three under the threshold are kept, as in `HybridRecognitionEngine`. |
| Speech | The speech recognizer cannot run on a JVM. What it heard on a device is captured once per recording as a list of text and the frame it was reported at, and replayed through `SpeechMatcher`. Candidates and the most different words per song accumulate, as in the engine. |
| Decision rules | `SpeechMatcher` and `PitchMatcher` are the code the engine itself calls (they were extracted from it, with no change in behavior), so a measurement is a measurement of the rules that ship. |

Where it differs from the running app, so the numbers are not read as more than they are:

- The pitch tick counts from the start of the recording, not from the button press. A recording shorter than 5 seconds is never scored on pitch, which is also what the app does.
- Transcripts are captured by feeding 1,024-sample frames straight into Vosk, faster than real time, instead of through the app's speech service. The text may differ slightly from a live run.
- The library does not change during a recording.
- The app compares a stored contour made when a user syncs a song. Here the reference is a recording named in `library.json`.

## 4. Corpus layout

```
<corpus>/library.json      the songs the app would hold
<corpus>/queries.json      one entry per recording to recognize
<corpus>/recordings/       16-bit PCM or 32-bit float WAV, any sample rate, mono or stereo
<corpus>/transcripts/      <query id>.json, written by the capture step
<corpus>/corpus.json       optional: {"name": "...", "caution": "..."}; the caution is printed on the report
```

`library.json`: a list of `{id, title, language, lyrics, reference}`. `reference` is a recording the stored contour is made from, or absent for a song with no pitch fingerprint.

`queries.json`: a list of `{id, file, song, mode, language, condition, device, speaker, speakerGroup}`. `song` is the id of the song being sung or said, or `null` when it is not in the library. The rest only slice the report. `language` selects the speech model and the pitch threshold.

Recordings are people's voices. Keep the corpus outside the repository (`/eval-corpus/` is ignored by git as a safeguard), and never publish it.

## 5. Running it

```
# 1. Capture what the speech model hears (needs the debug app and test APK installed, and the models in tools/audio-test/models)
bash tools/audio-test/capture_transcripts.sh <corpus dir> [language codes]

# 2. Replay and report, on the JVM
./gradlew :app:testDebugUnitTest --tests "*EvaluationRun*" -Pversesync.eval.corpus=<corpus dir> [-Pversesync.eval.label=<name>] [-Pversesync.eval.out=<dir>]
```

The report is written to `app/build/eval/report.md` and `report.json` unless an output directory is given. Add `-Pversesync.eval.sweep=true` to also write `sweep.md` (section 8). Neither holds a date, so the same corpus and the same code give the same files, and two runs can be compared with an ordinary diff. Without `-Pversesync.eval.corpus` the run is skipped, and the normal test run does not need a corpus.

Step 1 is only needed once per recording and speech model. Changing the matching rules and rerunning step 2 takes seconds.

The harness itself is tested on a small generated corpus with known answers (TC-U-40).

## 6. Recording protocol for the real set

This is for the people-and-phones collection in section 4.1 of the roadmap. It is a task for the owner, not for code.

- **Devices:** three or four real phones, at low, mid and high price levels. Record on each phone's own microphone and note the model in `device`.
- **Speakers:** 20 to 30, adults and, with a guardian's consent, children. Give each a `speaker` code and a `speakerGroup` (adult or child). Keep names and the code key out of the corpus.
- **Consent:** written, before recording, saying that recordings are used only to measure recognition, stay private, and can be deleted on request. For children, the guardian consents. If children use the app, the families policy and children's privacy rules apply (roadmap, section 7).
- **Material:** singing and speaking, in the five or six languages that will be officially supported. Songs must be ones the speaker may sing; lyrics are copyrighted, so the library holds only what the owner is entitled to keep.
- **Snippets:** 3, 5 and 10 seconds from random start points, cut offline with a recorded random seed, one file per snippet. Keep the 3 second snippets even though the pitch path ignores them; that is a finding.
- **Conditions:** quiet, television in the background, street and car, each recorded as such (`condition`).
- **Songs not in the library:** record at least as many of these as songs that are, so the false-accept rate has enough trials. These are the most valuable recordings.
- **Split:** decide before looking at results which speakers are for tuning thresholds and which are only for reporting, and do not tune on the reporting ones.

## 7. The seed corpus

`tools/audio-test/make_seed_corpus.ps1` makes a corpus from the FLEURS clips already used by the language tests: 21 languages, one spoken sentence each, every third language held out of the library to give recordings of songs that are not in it. It exists to show the harness running end to end and to give a first baseline. It is not an evaluation set:

- one speaker per language, spoken, studio quality;
- a library clip is its own pitch reference, so pitch numbers for songs in the library mean nothing. Read the speech numbers and the false-accept numbers only;
- 14 in the library and 7 not, so every interval is wide.

Result of the first run (21 recordings, 14 songs in the library, speech captured on an Android emulator with the real Vosk models, replayed with the rules as of this commit):

| Path | found | top-1 | false accept (7 not in library) | vocal lock opens for the right song | vocal lock opens for a wrong song | median latency |
|---|---|---|---|---|---|---|
| speech | 14/14 = 100% (78 to 100) | 14/14 | 0/7 = 0% (0 to 35) | 14/14 | 0/280 = 0% (0 to 1) | 2.3 s |
| pitch | 12/14 = 86% (60 to 96) | 12/14 | **5/7 = 71% (36 to 92)** | 6/14 = 43% (21 to 67) | 2/280 = 1% (0 to 3) | 5.0 s |
| combined | 14/14 = 100% (78 to 100) | 14/14 | 5/7 = 71% (36 to 92) | 14/14 | 2/280 = 1% (0 to 3) | 2.3 s |

What this does and does not show:

- **Speech:** with the recording fed straight into the recognizer, the stricter rule (two different heard words) found every sentence and accepted none of the seven that are not in the library. The 21 sentences passed only 15 times through the emulator microphone, so most of that loss was probably the audio rig, not the rule. With seven recordings the false-accept interval still reaches 35 percent, so this does not show the rule is safe.
- **Pitch on speech:** a spoken clip that is not in the library was given a pitch candidate 5 times out of 7. Spoken sentences have no stable melody, so pitch cannot tell them apart, which is the reason the roadmap plans pitch for singing only (item 4). This is the first measurement of that, and it is on speech, not singing.
- **Pitch on the library clips** is not meaningful here, because each clip is its own reference. Two of the 14 were still missed, which was not investigated.
- **The vocal lock** opened for a wrong song in 2 of 280 trials, through pitch. Those trials are not independent, so the interval is optimistic.

## 8. Choosing the speech rules

Issue [#44](https://github.com/Sriram-Codes-SW/VerseSync/issues/44) asks for the minimum evidence and the margin to be chosen from the evaluation set and not by hand. `-Pversesync.eval.sweep=true` replays the corpus under 54 settings of the speech rules (minimum different words 1 to 3, share of heard words 0.25 to 0.5, fuzzy matching on or off, lead margin 0 to 0.5) and writes `sweep.md` beside the report. It sweeps the speech path alone, because pitch does not depend on these settings and, on spoken recordings, accepts so many wrong songs that it hides any speech effect.

On the seed corpus the sweep is flat: all 54 settings, including the loosest (one word, share 0.25, fuzzy on), give 14 of 14 found and 0 of 7 false accepts. So the seed cannot choose any of them, and the defaults stay (two words, share 0.35, fuzzy off, margin 0.25). The reasons are in the corpus. Each library song is in a different language, so no two songs share words, and the clips are clean speech with no music or singing, so the garbled transcripts that cause ties and false accepts never occur. The settings are to be chosen when the real set exists, with songs in the same language and singing in noisy conditions. Pick the row that keeps false accepts at or under the target with the best found and top-1 rates, and when several rows are the same within their intervals, keep the default.

## 9. Not verified

- That the transcripts captured by feeding frames into Vosk match what the app hears from a live microphone. Both use the same recognizer and model, but the buffer sizes differ.
- Anything on singing, children, or real phones: nothing of that has been recorded yet.
- The harness has not been compared against a person listening to the same recordings.
