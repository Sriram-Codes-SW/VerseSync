| Field | Value |
|---|---|
| Document | VerseSync Evaluation Harness |
| Version | 0.3 |
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
| decides right | The strongest song by speech evidence leads the runner-up by the margin, and it is the right one. The rule of issue #44: accept the best only if it beats the runner-up. |
| decides wrong song | The same, but it names a different song than the one sung, over songs in the library. |
| decides on unknown | The same, for a recording of a song that is not in the library. A confident wrong answer. |
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

The report is written to `app/build/eval/report.md` and `report.json` unless an output directory is given. Add `-Pversesync.eval.sweep=true` to also write `sweep.md` (section 9). Neither holds a date, so the same corpus and the same code give the same files, and two runs can be compared with an ordinary diff. Without `-Pversesync.eval.corpus` the run is skipped, and the normal test run does not need a corpus.

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

## 8. The same-language FLEURS corpus

The seed corpus (section 7) could not choose anything: one song per language and no shared words. `FleursCorpusBuilder` makes a harder corpus from the first part of Google FLEURS test archives (CC BY 4.0, read speech), with a fixed seed, so the same input gives the same corpus byte for byte.

```
bash tools/audio-test/fetch_fleurs_more.sh 40 en-us hi es de fr            # ~40 MB of each archive, into tools/audio-test/fleursbig (ignored by git)
./gradlew :app:testDebugUnitTest --tests "*BuildFleursCorpus*" -Pversesync.eval.fleurs=<fleursbig dir> -Pversesync.eval.corpus=<corpus dir>
bash tools/audio-test/capture_transcripts.sh <corpus dir>                  # needs the models in tools/audio-test/models
./gradlew :app:testDebugUnitTest --tests "*EvaluationRun*" -Pversesync.eval.corpus=<corpus dir> -Pversesync.eval.sweep=true
```

How it is built, per language: the recordings are shuffled, about a third go to the library and their sentences become songs of four sentences each, so songs share common words. The rest are recordings of sentences that are in no song. More songs are made from sentences with no recording, with no pitch contour, so the library is larger than what is heard. Every recording is cut to a 3, 5 or 10 second snippet from a random start. The archives are in file-name order, so a prefix is a random-looking sample, but only a few sentences have both readings in it, which is why only 16 recordings are by another speaker than the reference.

The corpus used for the numbers below has 353 recordings in five languages (en-us 91, hi 67, es 64, de 56, fr 75): 132 of songs in the library (116 are the reference recording itself, 16 are by another speaker) and 221 of songs that are not, with 92 songs in the library, 32 of them with a pitch contour. That is fewer recordings of unknown songs than the 300 aimed for, because the 40 MB prefixes held only 353 clips. Speech was captured with the Vosk small models on an Android emulator by feeding frames straight into the recognizer.

**These are read sentences, not singing, and four-sentence "songs", not real lyrics.** They say how the rules behave when many songs share common words, which the seed could not. They do not say how well the app recognizes singing.

### Result at the current rules

| Path | found | top-1 | decides right | decides wrong song | candidate for a song not in the library | decides on a song not in the library | median latency |
|---|---|---|---|---|---|---|---|
| speech | 130/132 = 98% (95 to 100) | 129/132 (94 to 99) | 127/132 = 96% (91 to 98) | 0/132 (0 to 2.8) | **166/221 = 75% (69 to 80)** | **83/221 = 38% (31 to 44)** | 1.6 s |
| pitch | 20/132 = 15% (10 to 22) | 15/132 | not used | not used | 118/221 = 53% (47 to 60) | not used | 5.0 s |

"Decides" means the strongest song leads the runner-up by the margin (issue #44: accept the best only if it beats the runner-up). The vocal lock opened for the right song for 125/132 recordings (95%) and for a wrong song in 57 of 32,344 pairs of recording and song (0.2%; the pairs are not independent).

What it shows:

- **The speech rule lets in a wrong song far more often than the seed suggested.** With 92 songs that share common words, three quarters of recordings of songs that are not in the library still produce a candidate, and more than a third produce a confident decision. The seed's 0 of 7 came from a library where no two songs shared a word. This agrees with the music trial that ties and false candidates appear when words are shared.
- **Fuzzy matching does not help.** In the first sweep it raised candidates for unknown songs (166 to 180 of 221 at the defaults) and left found unchanged (130/132), so it stays off.
- **Pitch is weak even on its own reference.** For the 116 recordings that are a piece of their own reference recording, pitch found only 19 (16%, 11 to 24). A piece of the exact recording should match. This is consistent with the known contour defect (docs/07, docs/08 section 4.2), where the snippet is centered on its own mean and the reference on its whole mean, and with the app looking at pitch only every 5 seconds, so a 3 second snippet is never scored. How much each contributes was not separated. It also gave a candidate for 118 of 221 recordings of songs that are not in the library (53%). These two numbers are the measured reason for item 4 of the roadmap and for keeping pitch out of anything that must be exact.
- **Pitch on a song in the library by another speaker** was found for 1 of 16. That is a small number and not a rate.

## 9. Choosing the speech rules

Issue [#44](https://github.com/Sriram-Codes-SW/VerseSync/issues/44) asks for the minimum evidence and the margin to be chosen from the evaluation set and not by hand. `-Pversesync.eval.sweep=true` replays the corpus under 120 settings of the speech rules (minimum different words 1 to 6, share of heard words 0.35 to 0.8, fuzzy matching on or off, lead margin 0 to 0.5) and writes `sweep.md` beside the report. It sweeps the speech path alone, because pitch does not depend on these settings and, on spoken recordings, accepts so many wrong songs that it hides any speech effect.

On the seed corpus the sweep was flat, so it could not choose. On the FLEURS corpus it is not. With fuzzy matching off and the margin at 0.25:

| Minimum words | Share | Decides right | Decides wrong song | Decides on a song not in the library |
|---|---|---|---|---|
| 1 | 0.35 | 127/132 = 96% (91 to 98) | 0/132 | 95/221 = 43% (37 to 50) |
| 2 (current) | 0.35 | 127/132 = 96% (91 to 98) | 0/132 | 83/221 = 38% (31 to 44) |
| 3 | 0.5 | 123/132 = 93% (88 to 96) | 0/132 | 39/221 = 18% (13 to 23) |
| 4 | 0.5 | 114/132 = 86% (79 to 91) | 0/132 | 19/221 = 8.6% (5.6 to 13) |
| 4 | 0.65 | 114/132 = 86% (79 to 91) | 0/132 | 11/221 = 5.0% (2.8 to 8.7) |
| 4 | 0.8 | 104/132 = 79% (71 to 85) | 1/132 | 6/221 = 2.7% (1.3 to 5.8) |
| 6 | 0.65 | 94/132 = 71% (63 to 78) | 0/132 | 1/221 = 0.5% (0.1 to 2.5) |
| 6 | 0.8 | 82/132 = 62% (54 to 70) | 0/132 | 0/221 = 0% (0 to 1.7) |

The trade is steep: the settings that keep a confident wrong decision under a few percent also stop deciding for a quarter to a third of recordings that are in the library. **No setting is applied in this change.** Choosing a row from this table and reporting it on the same recordings would be optimistic, and a fixed number of words is a poor rule for a 3 second snippet, which cannot contain six words. The next step is to pick the rule on part of the data and report it on recordings that were not used to pick it, and to try a rule that scales with how much speech was heard. Pick the row that keeps false decisions at or under the target with the best decides-right rate, and when several rows are the same within their intervals, keep the simpler one.

## 10. Not verified

- That the transcripts captured by feeding frames into Vosk match what the app hears from a live microphone. Both use the same recognizer and model, but the buffer sizes differ.
- Anything on singing, children, or real phones: nothing of that has been recorded yet.
- The harness has not been compared against a person listening to the same recordings.
