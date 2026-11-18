| Field | Value |
|---|---|
| Document | VerseSync Evaluation Harness |
| Version | 0.16 |
| Date | 2026-11-18 |
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

The first corpus built, whose numbers are below, has 353 recordings in five languages (en-us 91, hi 67, es 64, de 56, fr 75): 132 of songs in the library (116 are the reference recording itself, 16 are by another speaker) and 221 of songs that are not, with 92 songs in the library, 32 of them with a pitch contour. That is fewer recordings of unknown songs than the 300 aimed for, because the 40 MB prefixes held only 353 clips. Speech was captured with the Vosk small models on an Android emulator by feeding frames straight into the recognizer.

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

These numbers took the rival only among candidates, which distorts them (see the end of section 10). The trade is steep: the settings that keep a confident wrong decision under a few percent also stop deciding for a quarter to a third of recordings that are in the library. **No setting is applied in this change.** Choosing a row from this table and reporting it on the same recordings would be optimistic, and a fixed number of words is a poor rule for a 3 second snippet, which cannot contain six words. The next step is to pick the rule on part of the data and report it on recordings that were not used to pick it, and to try a rule that scales with how much speech was heard. Pick the row that keeps false decisions at or under the target with the best decides-right rate, and when several rows are the same within their intervals, keep the simpler one.

## 10. Choosing a decision rule on held-out data

Section 9 chose nothing, because choosing among 120 settings on the recordings they are reported on flatters the best one. This section sets the procedure and the criterion, and it was written and committed **before** any result on the held-out recordings existed.

**Corpora.** The FLEURS dev split gives the selection corpus and the FLEURS test split gives the report corpus. Their sentences are different, so no song, sentence or recording is on both sides. Both are built with `FleursCorpusBuilder` and the same seed, the same languages and the same library recipe (section 8). The dev split is fetched with `fetch_fleurs_more.sh -s dev`.

**What is chosen.** A decision rule, kept apart from the candidate rule. The candidate rule (share and minimum words) builds the list a person chooses from, where a wrong candidate is harmless. A decision acts without a person choosing, as the vocal lock does, so it is judged separately, and it sees every song, not only candidates. Section 9's sweep took the rival only among candidates, so a stricter candidate rule removed the rivals and made the leader look clear; that distorted part of its table. `DecisionRule` takes the rival from every song that has a heard word, counts a tie as no decision, and has four settings: the leader needs at least `minWords` different heard words found (or all of them if its lyrics have fewer), at least `minShare` of the heard words found, and an absolute gap in rarity weight over the strongest other song, `gap`, that must be exceeded. The grid is minWords 1, 2, 3, 4, 5, 6, 8; minShare 0.35, 0.5, 0.65, 0.8; gap 0, 0.5, 1, 1.5, 2, 3 (168 rules). An absolute gap is used rather than evidence per heard word, because dividing by the number of heard words is nearly the share again and lets a 3 second snippet decide as easily as a 10 second one.

**When it acts.** The rule is evaluated after every piece of heard text, on the evidence so far (for each song, the update with the most weight, so words, share and weight come from one update). "Acts first" counts the rule as acting the first time it fires, which is what the app would do if it acted on the first time the rule holds. "At end" looks only at the final evidence, and is shown for comparison.

**Criterion, fixed before the run.** On the selection corpus, acting first, a rule is eligible if it decides on a song that is not in the library for at most 2 percent of those recordings with the upper end of the 95 percent interval at most 5 percent, and names a different song than the one sung for at most 1 percent of recordings of songs in the library. Of the eligible rules, the one that names the right song most often is chosen, and a tie goes to fewer words, then lower share, then smaller gap. If no rule is eligible, none is chosen and the report says so. The targets are not claims about what is safe: a rate under 1 percent needs about a thousand recordings of unknown songs to show, and this corpus has a few hundred, so the interval, not the point estimate, is what the upper bound limits.

**Report.** The chosen rule and two baselines (the current speech rule and the current lock words rule, both in the same form) are reported once on the report corpus with 95 percent Wilson intervals, by language and by snippet length, with the selection-side numbers beside them to show the gap. A leave-one-language-out table chooses the rule without one language and reports it on that language, as a check that it carries over to a language it was not tuned on. Run it with:

```
./gradlew :app:testDebugUnitTest --tests "*HeldOutRun*" -Pversesync.eval.select=<selection corpus> -Pversesync.eval.corpus=<report corpus>
```

The app is not changed by this. `SpeechRules` and the lock policy keep their defaults, and a decision rule, if one ships later, is its own layer with an ADR amendment and a check on singing.

### Result of the run
**Correction (section 11):** these figures count decisions made on the last update of each transcript, which a live recognizer would not have given yet. With those not counted the chosen rule names the right song for 78% (73 to 83) and decides on an unknown song for 6.9% (4.7 to 10) on the report corpus, and the current speech rule for 50% (45 to 56). The conclusions below are unchanged.

One run, on 470 selection recordings (257 of songs not in the library) and 606 report recordings (361 of songs not in the library, short of the 400 aimed for). The rule chosen on the selection corpus was **`words>=4 share>=0.65 gap>3.0`**. Acting the first time the rule fires:

| Rule | Corpus | right song | wrong song | decides on a song not in the library |
|---|---|---|---|---|
| chosen | selection | 182/213 = 85% (80 to 90) | 0/213 = 0% (0 to 1.8) | 5/257 = 1.9% (0.8 to 4.5) |
| chosen | **report** | 211/245 = 86% (81 to 90) | 1/245 = 0.4% (0.1 to 2.3) | **28/361 = 7.8% (5.4 to 11)** |
| current speech rule | report | 213/245 = 87% (82 to 91) | 24/245 = 9.8% (6.7 to 14) | 193/361 = 53% (48 to 59) |
| current lock words rule | report | 221/245 = 90% (86 to 93) | 8/245 = 3.3% (1.7 to 6.3) | 142/361 = 39% (34 to 44) |

At the end of the recording instead of the first firing, on the report corpus: the chosen rule decides on a song not in the library for 8/361 = 2.2% (1.1 to 4.3) and names the right song for 207/245 = 84%; the current speech rule for 74/361 = 20% (17 to 25) and 234/245 = 96%.

What it shows:

- **The chosen rule did not meet the criterion on held-out data.** It decided on an unknown song for 7.8% (5.4 to 11) against a target of at most 2% with the upper end at most 5%, where on the selection corpus it was 1.9%. That gap is the selection optimism this procedure exists to expose. The rule is not adopted and nothing in the app changes.
- **It is still far better than the current rules.** It cuts deciding on an unknown song from 53% (speech rule) or 39% (lock words rule) to 7.8%, and wrong-song decisions from 9.8% or 3.3% to 0.4%, while naming the right song as often (86% against 87% and 90%).
- **The trouble is acting early.** At the end of the recording the same rule is at 2.2%, which would pass. A garbled early piece of text can fire the rule and later text cannot take it back. A rule that waits for the evidence to settle, for example the same leader for several updates or a minimum listening time, is the thing to try next. It can be measured with the per-update evidence the replay now keeps; it was not tried here, and trying it on the report corpus now would spoil it as held-out data.
- **The top of the ranking is flat and the exact numbers do not carry over.** Many rules are the same within noise on the selection corpus, and leaving a language out changes the rule chosen (gap 0 or 3, share 0.65 or 0.8). On a language left out the rule decided on an unknown song for between 0% and 12% of recordings (de 12%, en-us 9.8%, es 0%, fr 6.3%, hi 1.6%) and named the right song for 65% to 90%. What is stable is the family: at least four different words and at least 65% of the heard words in the lyrics. The 3.0 gap is not.
- **Short snippets decide less, and not wrongly.** The chosen rule named the right song for 50 of 70 recordings (71%, 60 to 81) of 3 seconds and 86 of 89 (97%, 91 to 99) of 10 seconds, with no wrong-song decision at 3 or 10 seconds.
- **"Decides on a song not in the library" means naming any of the 155 songs.** The vocal lock opens for one target song, so its rate for a given target is much lower (83 of 93,685 pairs of recording and song, 0.1%, at the current rules). Do not read these rates as the chance that the lock opens for a stranger.

The first sweep in section 9 took the rival only among candidates, so a stricter candidate rule removed the rivals. With the rival taken from every song, on the report corpus and at the end of each recording, deciding on an unknown song falls from 109/361 = 30% (26 to 35) to 74/361 = 20% (17 to 25) at the current rule (two words, share 0.35), and from 30/361 = 8.3% (5.9 to 12) to 8/361 = 2.2% (1.1 to 4.3) at four words and share 0.65. The earlier table therefore overstated false decisions somewhat. Its conclusion, that the current rule is far too permissive, holds on the larger corpus.

## 11. Why the held-out rule failed: diagnostics

Before building a rule that waits, the held-out result was checked against data that already exists (`DiagnosticsRun`: `-Pversesync.eval.select=<dev> -Pversesync.eval.corpus=<test>`). Nothing here chooses anything. Read speech, not singing.

**A flaw in the earlier measurement, now corrected.** Each captured transcript ends with the recognizer's final result, which a live recognizer only gives when it is stopped, not while someone is still singing. Section 10 counted decisions made on that last update. Updates at the last reported frame are no longer counted. The rule chosen in section 10 is the same, and on the report corpus the corrected figures are:

| Rule (report corpus, acting first) | right song | wrong song | decides on a song not in the library |
|---|---|---|---|
| chosen `words>=4 share>=0.65 gap>3.0` | 192/245 = 78% (73 to 83) | 1/245 = 0.4% (0.1 to 2.3) | 25/361 = 6.9% (4.7 to 10) |
| current speech rule | 207/245 = 84% (79 to 88) | 24/245 = 9.8% (6.7 to 14) | 182/361 = 50% (45 to 56) |
| current lock words rule | 214/245 = 87% (83 to 91) | 8/245 = 3.3% (1.7 to 6.3) | 131/361 = 36% (31 to 41) |

So the chosen rule named the right song for 78% of recordings, not 86%: 19 of its right answers had been made on the last update. The numbers "at the end of the recording" in section 10 are unchanged. On the selection corpus the chosen rule is 81% right (75 to 86) and 1.9% false (0.8 to 4.5) after the correction.

What the checks show:

1. **Early firing explains most of the false decisions, but not all of the gap.** Of the 25 recordings of songs not in the library that fired the rule, only 5 still decided on that song at the end. In 10 another song had overtaken it, in 9 the share of heard words in the lyrics had fallen (the rule fired on an update with few heard words and most of them found, and later updates heard more words), and in 1 a rival had caught up. The rule fired at a median of 2.4 s (1.2 to 4.5 s), roughly halfway through the recordings.
2. **The test corpus is harder than the dev corpus even at the end.** At the end of the recording the same rule decides on an unknown song for 0.4% (0.1 to 2.2) on dev and 2.2% (1.1 to 4.3) on test. By language it is 0 of 51 on dev and 9 of 80 on test for French, and 2 of 61 and 9 of 92 for English; the intervals do not overlap for French. So part of the 1.9% to 6.9% difference is a difference between the two corpora, and part is choosing the minimum of 168 rules on dev.
3. **By listening time.** False decisions on test rise from 2.2% by 2 s to 4.7% by 3 s and 6.9% by 5 s, then stop. On dev they are 0.4% by 2 s and 1.9% by 4 s. Recordings of songs in the library were right 50% (test) and 58% (dev) by 3 s, 76% and 77% by 5 s. A hold of about a second or two would remove firings in the first seconds, at the cost of those right answers arriving later.
4. **Library size is not the explanation.** A word's weight is `ln(1 + songs / songs containing it)`, so the same gap means more in a smaller library. The gap was chosen with 110 songs and the test library has 155. Scaling it to 3.22 changes the false rate from 6.9% to 6.6%.

What this means for the next step: the evidence in the replay only ever grows (the update with the most weight per song is kept), so a rule that waits can only be waiting for rivals to grow. Evidence that can be taken back is a different and more realistic idea: the best of the finished utterances, plus only the current partial, so a later partial replaces an earlier one. The capture now marks finished results (`"final": true`) so that this can be replayed. Transcripts captured before this change have no marks, and replay with each update standing alone.

## 12. Settle rules: the plan, fixed before any run

This section was written and committed before any rule that waits had been evaluated, and before the fresh report corpus existed. It follows from a review of section 10 and the checks above.

**What is built (this change).** `Settle(holdFrames, retractable)` and `RuleMetrics.firing`: a rule acts only when the same song has been decided continuously for the hold, measured in frames of audio, and only if the hold completes before the last reported frame. Evidence is either the sticky kind of sections 9 and 10 or the retractable kind above. `RuleSlice` reports the median and 90th percentile seconds until the first right answer. A number of songs per language can be fixed in the builder (`-Pversesync.eval.recordedSongs`, `-Pversesync.eval.distractorSongs`) so that libraries are the same size in every corpus, and `fetch_fleurs_more.sh -s train` fetches the FLEURS train split. None of this has been run on a held-out corpus.

**What is run next, not in this change.**

1. **Corpora.** Rebuild the dev and test corpora with five recorded and eleven unrecorded songs per language (80 songs in all; fifteen was planned, but the Hindi dev split has only 46 spare sentences, enough for eleven songs, so eleven is used in every corpus, decided before any evaluation) and recapture their transcripts with the final marks. Together they are the selection corpus; test is no longer clean after section 10. The fresh report corpus is a prefix of the FLEURS train split, about 90 MB per language for en-us, hi, es, de and fr, built the same way, with at least 600 recordings of songs not in the library. Before it is used, check that its sentences are not in dev or test.
2. **Grid (64 rules).** At least 4 different words found; share 0.65 or 0.8; gap 0.4, 0.6, 0.8 or 1.0 times `ln(1 + songs)` (about 1.8, 2.6, 3.5 or 4.4 for 80 songs); hold 0, 0.5, 1 or 2 s; evidence sticky or retractable.
3. **Criterion.** On the selection corpus, acting the first time the rule fires, a rule is eligible if it decides on a song not in the library for at most 1% of those recordings with the upper end of the 95% interval at most 3%, and names a different song than the one sung for at most 1% of recordings of songs in the library. Of the eligible rules, the one that names the right song most often is chosen. A tie goes to the shorter hold, then sticky evidence, then the lower share, then the smaller gap. If no rule is eligible, none is chosen and the report says so.
4. **Pass on the report corpus,** reported once: the chosen rule decides on a song not in the library with the upper end of the interval at most 5%, and names a different song than the one sung for at most 1%. The right-song rate and the median and 90th percentile time to the first right answer are reported with it, by snippet length, because a hold costs the short snippets most, and a recording on which the rule never acts is a cost and not a success. A leave-one-language-out table is reported with it.
5. **Not claimed.** One rate under 1% needs about a thousand recordings of unknown songs to show, which this corpus does not have. These are read sentences, so nothing here says how Vosk's partial results flicker on singing, where a hold may fail safe and never decide. The lock opens for one target, so its rate per target is measured per pair of recording and target song and not by "decides on any of the songs". The app is not changed by any of this, and if a rule is ever adopted it is its own layer, with an ADR amendment and a check on singing.

### Result of the run

Run once, after the plan above was committed. The corpora were built with the same seed and the fixed library of 80 songs, with finished results marked in every transcript. Selection: the dev and test corpora together, 1,076 recordings (266 of songs in the library, 810 of songs not). Report: the FLEURS train corpus, 812 recordings (105 of songs in the library, 707 not). The train corpus's sentences and clip files do not occur in dev or test (checked for all five languages). Whether its speakers are disjoint from theirs was not checked, because FLEURS does not give speaker ids in the file lists. Read speech, not singing. Updates at the last reported frame are not counted.

**The rule chosen on the selection corpora** is `words>=4 share>=0.8 gap>1.0·ln(1+N) hold 0 ms, retractable` (gap about 4.4 for 80 songs). Acting the first time it fires:

| Rule | Corpus | right song | wrong song | decides on a song not in the library | median / 90th percentile seconds to the right answer |
|---|---|---|---|---|---|
| chosen | selection | 197/266 = 74% (68 to 79) | 0/266 (0 to 1.4) | 5/810 = 0.6% (0.3 to 1.4) | 2.6 / 4.3 |
| chosen | **fresh (train)** | 73/105 = 70% (60 to 78) | 0/105 (0 to 3.5) | **0/707 = 0% (0 to 0.5)** | 2.6 / 4.0 |
| current speech rule | fresh | 94/105 = 90% (82 to 94) | 2/105 = 1.9% (0.5 to 6.7) | 326/707 = 46% (42 to 50) | 1.6 / 2.8 |
| current lock words rule | fresh | 89/105 = 85% (77 to 90) | 1/105 = 1.0% (0.2 to 5.2) | 226/707 = 32% (29 to 35) | 2.1 / 3.5 |

**The pre-registered pass bar was met:** the upper end of the interval for deciding on a song not in the library is 0.5%, at most 5%, and a wrong song was named for 0 of 105, at most 1%. The selection estimate (0.6%) and the fresh one (0%) agree, which last time they did not.

What it shows:

- **The difference came from stricter thresholds, not from waiting.** The chosen rule has no hold. At the chosen share and gap, a hold of 0.5 s lowered right answers from 74% to 67% and moved false decisions from 0.6% to 0.5%, which is within noise; 1 s took right answers to 56% and 2 s to 37%. Evidence that can be taken back made no measurable difference to either (197 against 196 right, 5 against 5 false). The rule chosen last time had a share of 0.65 and a gap of about 0.64·ln(1+N), which was too lax; the stricter share and gap leave almost no false firings at all (5 of 810 on the selection corpora). The idea that early firing was the main problem, and that waiting would fix it, was not what the data supported.
- **It costs right answers.** The chosen rule names the right song for 70% (60 to 78) of recordings in the library, against 90% for the current speech rule, and 32 of the 105 never reached a decision. By snippet length it is 51% (36 to 67) at 3 s, 84% (68 to 93) at 5 s and 75% (59 to 86) at 10 s, and by language 65% to 77%. It acts at a median of 2.6 s, against 1.6 s. In every language and at every snippet length it decided on no unknown song (upper ends 1.4% to 2.9%).
- **The settings carry over, roughly.** Leaving one language out and choosing without it gave rules that differ (share 0.8 mostly, gap 0.6 to 1.0, hold 0 or 0.5 s), and on the language left out the rule decided on an unknown song for 0% to 1.3% of recordings (de 0.7%, es 1.3%, en-us, fr and hi 0%) and named the right song for 62% to 73%.
- **A decision on a song not in the library also means no target song opens.** The lock opens for one target, and the chosen rule named no song at all for any of the 707 unknown recordings, so it opened none.

What it does not show:

- **Wrong songs.** 0 of 105 has an upper end of 3.5%, so a wrong-song rate under 1% is not shown, only that none occurred. The pre-registered bar was a point estimate for this.
- **Singing, real lyrics, or the live app.** These are read sentences, four-sentence "songs" that share vocabulary the way Wikipedia text does, an 80-song library, and transcripts fed to Vosk faster than real time on an emulator. Real lyrics repeat words in choruses (a word counts once), sung speech is recognized worse and its partial results flicker more, and a live recognizer's timing differs from this replay.
- **Anything about the app.** Nothing is adopted. A decision rule, if it ships, is its own layer on top of the unchanged candidate list, with an ADR and a check on real singing, and the lock would use it for its one target song.

## 13. Recorded-music fingerprints: the plan, fixed before the run

ADR-19 (Proposed). This section was written and committed before the matcher was run on any generated corpus. It is a different input from everything above: a recording playing near the phone, matched against fingerprints made from the same recording, with no speech and no pitch. Nothing here uses or stores copyrighted audio. The test music is generated from a seed at test time and is never committed.

**What is built (phase 1, no app change).** A pure-Kotlin landmark matcher in `fingerprinting/landmarks`, written from the published method (Wang 2003): framing and a Hann window, a radix-2 FFT, spectral peaks from 250 to 4000 Hz (the band a phone speaker can reproduce), pairs of peaks as 22-bit hashes, and offset voting. It starts from these fixed parameters, which are not tuned on any corpus: 16 kHz mono; FFT 1024 (15.6 Hz bins); hop 256 (62.5 frames per second); a peak is a local maximum within 10 bins and 8 frames, at most 30 per second; each anchor pairs with its 5 strongest peaks 2 to 63 frames ahead and within 64 bins; hash = anchor bin (9 bits), bin difference (7 bits), frame difference (6 bits); stored per hash: the hash and the anchor frame (6 bytes packed). A match needs a count of votes at one offset (summed over three adjacent frames) of at least `minVotes`, and at least `ratio` times the votes of the best other song.

**Generated corpora.** Two seeds (A, for choosing, and B, for reporting once), each with 60 library songs of 90 seconds and 40 songs of 90 seconds that are not in the library. A song is made of harmonic notes in a key, a chord progression, a melody, a bass line and noise percussion, in sections that repeat (a chorus returns), and songs share tempo (90, 110 or 128), key and progression families, so wrong songs look alike. Each library song is fingerprinted from its clean audio. Queries are 3, 5 or 10 seconds from random starts: 5 per library song (300) and 1,000 from songs not in the library, the same query set for every condition.

**Conditions** (each damages the same queries): clean; white noise at 20, 10, 5, 0 and −5 dB signal to noise; pink noise and speech-shaped noise at 5 dB; level +20 dB with clipping and −20 dB; a speaker band of 250 to 4000 Hz; a made-up room echo (24 taps over 0.4 s, decay about 0.45 s); speed +1 and −1 percent; and a **headline condition**: 5 second queries through the speaker band, the echo and pink noise at 5 dB together.

**Choosing (seed A only).** A grid of `minVotes` 8, 10, 12, 15, 20, 25 and `ratio` 1.5, 2, 3 (18 rules). A rule is eligible if, pooled over every condition, it accepts a song for at most 0.1% of recordings of songs not in the library with the upper end of the 95% interval at most 0.5%. Of the eligible rules the one with the best pooled top-1 on library queries is chosen; ties go to more votes, then the larger ratio. If none is eligible, none is chosen and the report says so.

**Pass bar (seed B, reported once).** The chosen rule accepts a wrong song for pooled upper end at most 1%, and its top-1 at the headline condition is at least 80%. Everything else is reported with 95% Wilson intervals and no bar: top-1 and false accepts for every condition, by snippet length.

**Cost of a bigger library, reported with no bar.** Bytes per song, and, on seed B with a library of 60 songs and again with 600 songs of 45 seconds, the median lookup time per query and the false-accept rate, because offline recognition is limited by the fingerprints that fit on the phone.

**Addendum, committed before any run: re-performance conditions, and what this is for.** The owner's use is a live concert, where the band plays the song live and does not replay the recording. Landmark fingerprints of a recording are not expected to match that, and this run measures how far from the recording a performance can drift before they stop matching. It does not make the fingerprints the concert path; ADR-19 covers recorded playback (a backing track, a lip-synced number, walk-in music), and a separate proposal covers the concert.

The same generated song is played again as a different performance of the same score, and a snippet of that performance is matched against the print of the original. Five conditions, none with added noise, all with 3, 5 and 10 second snippets, on the same query starts: **different sound** (new instrument sounds, a new percussion part, the same tempo and timing); **tempo +3%**, **+5%** and **+8%** (the whole performance faster, with the same sounds as the original); and **a live-like performance** (new sounds and percussion, tempo +5% with a drift of up to ±2% over the song, and each note starting up to 30 ms early or late). They are reported with 95% Wilson intervals and no bar, in the same table as the other conditions and not pooled with them, and they play no part in choosing the thresholds. What is expected, and not measured, is that matches fall to near zero as soon as the sounds and the timing change, and that this is a limit of the method and not a defect of this matcher.

**Correction to the generated songs, committed before any evaluation run.** Two defects in the generated music were found by a small test on 6 songs, before any corpus was run. First, the random seeds of different songs collided (a melody seed was a sum, so song 7 reused pieces of song 5's melody); seeds are now mixed with a hash. Second, songs in one family shared their tempo exactly (90, 110 or 128 BPM) and their tuning exactly, so their bars and chord tones lined up frame for frame, and 4 of 20 queries for songs not in the library were accepted at the rule 15 votes and ratio 2. Real songs in one style do not share a tempo to a fraction of a percent or the same tuning. So within a family each song's tempo now differs by up to ±4% and its tuning by up to ±25 cents. The key and progression families, the repeating sections, the sounds, and every parameter of the matcher are unchanged. **The exact-family variant is kept as a stress test:** its clean-condition false accepts are reported for it separately, for the same library size, so the cost of identical tempo and tuning is visible and not hidden by the change.

**What it will not show.** Real recordings, real speakers and rooms, the phone's own audio processing, any cover, live version or edit, or the person singing. Generated music repeats less than real music and overstates accuracy, so a pass here is not a claim about real songs.

### Result of the run

Run once on generated music (seed A 43 to choose, seed B 44 to report), after the plan, both addenda and the code were committed. Two earlier attempts at the run failed before producing a result: the first ran out of memory (the test now gets a 6 GB heap, and re-performed songs are no longer cached for the whole run), the second hit a bug in my timing code (a five second window that could run past the end of a 45 second song). Neither result was seen or used.

**No rule met the criterion.** On seed A, pooled over every condition except re-performance (15,000 queries for songs not in the library and 4,500 for songs in it), no rule of the grid accepted a song not in the library for at most 0.1% with the upper end at most 0.5%. The strictest, 25 votes and ratio 3, accepted 25 of 15,000 (0.17%, 0.1 to 0.2) and named the right song for 3,165 of 4,500 (70%, 69 to 72). So nothing is chosen, the pass bar of seed B is not applied, and seed B's other conditions are not reported. The trade on seed A, pooled:

| rule | top-1 | songs not in the library accepted |
|---|---|---|
| 8 votes, ratio 1.5 | 91% (90 to 92) | 17% (17 to 18) |
| 15 votes, ratio 3 | 81% (80 to 82) | 0.6% (0.5 to 0.7) |
| 20 votes, ratio 3 | 76% (74 to 77) | 0.3% (0.2 to 0.4) |
| 25 votes, ratio 3 | 70% (69 to 72) | 0.17% (0.1 to 0.2) |

The descriptive results below were never used to choose anything, and are at the strictest rule. Seed A by condition (top-1 of 300 library queries; accepted songs not in the library out of 1,000):

| condition | top-1 | songs not in the library accepted |
|---|---|---|
| clean | 100% (99 to 100) | 4 (0.4%) |
| white noise 20 / 10 / 5 / 0 / −5 dB | 100% / 100% / 92% / 70% / 31% | 7 / 1 / 0 / 0 / 0 |
| pink noise 5 dB | 95% (92 to 97) | 2 |
| speech-shaped noise 5 dB | 77% (72 to 81) | 0 |
| level +20 dB, clipped | 51% (45 to 57) | 0 |
| level −20 dB | 99% (98 to 100) | 2 |
| speaker band 250 Hz to 4 kHz | 100% (99 to 100) | 4 |
| room echo | 74% (69 to 79) | 0 |
| speed +1% | 14% (11 to 19) | 0 |
| speed −1% | 3.7% (2.1 to 6.4) | 5 |
| headline: 5 s, speaker band, echo and pink noise 5 dB | 48% (42 to 54) | 0 |

Re-performance, seed B (300 library queries, 1,000 for songs not in the library, no bar): the same score played with **different sounds** 99% (98 to 100); **tempo +3%** 46% (40 to 51); **+5%** 1.0% (0.3 to 2.9); **+8%** 0% (0 to 1.3); **live-like** (new sounds, +5% drifting, notes up to 30 ms early or late) 13% (9.7 to 17). Songs not in the library were accepted for 4, 1, 5, 0 and 0 of 1,000.

What it shows:

- **The false-accept target was not met on generated music.** The accepted songs are in the clean and mild conditions (4, 7, 4 and 5 of 1,000 for clean, white noise at 20 dB, the speaker band and speed −1%) and almost none in the noisy ones, which suggests they come from the songs themselves, which share key and chord tones within a family, and not from noise. That was not diagnosed further, and the criterion is not changed after seeing it. A rule that accepts fewer would cost more right answers (70% at the strictest).
- **It finds a recording in noise but is very sensitive to speed.** Clean it finds 100%, and 92% at 5 dB of white noise. But playing the same recording 1% fast or slow leaves 14% or 4%, and clipping cuts it to 51%. The headline condition, 5 second snippets through a speaker band, an echo and noise together, is 48%.
- **It does not follow a performance that is not the recording.** With the same notes at the same times and only the sounds changed it still matches (99%), because the long notes keep their pitches. A tempo 3% off leaves 46%, 5% off 1%, and the live-like performance 13%. A live band plays other notes, at other times, at a tempo that is rarely within a few percent of the record, and these generated variants are milder than that. This is consistent with ADR-19: it is not the concert path, and nothing here was measured on a real band.
- **The cost is small.** The prints hold about 78 hashes a second, so a four-minute song is about 112 KB (not the 220 KB first assumed), 1,000 songs about 110 MB. Looking up a 5 second query took a median of 0.1 ms against 60 songs and 0.5 ms against 600, on one thread. Songs not in the library were accepted for 0.5% of clean queries at 60 songs and 0.1% at 600, so false accepts did not grow with the library in this range. Extrapolating, 10,000 songs would be about 1.1 GB, which was not measured.
- **The stress variant** (songs of a family sharing tempo and tuning exactly), clean queries: top-1 100% and 0.1% of songs not in the library accepted, no worse than the main corpora at this rule. The earlier 6-song test shows that with a lenient rule identical tempo and tuning does make songs confusable.

What it does not show: real recordings, real speakers, rooms or phones; the phone's own audio processing; any real live performance; or that the false-accept target is reachable on real music. A generated corpus repeats less than real music, and these songs share more structure than real songs of one style may.

## 14. Not verified

- That the transcripts captured by feeding frames into Vosk match what the app hears from a live microphone. Both use the same recognizer and model, but the buffer sizes differ.
- Anything on singing, children, or real phones: nothing of that has been recorded yet.
- The harness has not been compared against a person listening to the same recordings.

## 15. Songs in their own languages, end to end: the plan, fixed before any run

Why: the corpora so far are people reading sentences (section 8) and generated music (section 13). The owner asked for each language to be tried with a song of its own, with its lyrics, in a normal version and a live version where one exists. This section fixes what is done and what is reported before anything is run. It was written before the capture and the replay.

**Material.** Recordings of anthems and traditional songs whose words are in the public domain, with a free licence on Wikimedia Commons (public domain, CC0, CC BY or CC BY-SA), fetched from there and never from a streaming service. 22 recordings in 17 languages were fetched: English (2: a studio and a live one), Spanish, German, Italian, Dutch (2: a choir and the first recording of 1899), Polish, Portuguese, Turkish, Greek, Japanese, Korean (a live performance), Chinese, Tamil, Ukrainian, Swedish, Hindi, Bengali (2: a studio and a crowd singing) and Telugu (2 film songs of 1935). The licence and source page of each are in the corpus manifest, which is kept with the audio outside the repository. A licence is what the uploader claimed; it was not verified, and some files are marked for review on Commons.

**Words.** Fetched by script from Wikisource for 15 languages and not typed. They are the whole text of the anthem, which may be longer than what the recording sings. Hindi, Bengali and Telugu had no usable page, so those 5 recordings are used only as **songs that are not in the library**.

**Library and queries.** The library holds the 15 songs that have words. The queries are the 22 recordings: 17 of songs in the library and 5 of songs that are not. A recording is the whole song, decoded to 16 kHz mono on the device and fed to the real speech recognizer frame by frame, as in section 5, with no microphone: the emulator's microphone goes silent after a few seconds, so a live test through it cannot last a song (measured). The condition of a query is its version: studio, live, choir, crowd or historic.

**Pitch reference.** Each song's reference for the pitch path is its own studio recording where there is one. For the English and Dutch songs that makes the live and the first recording a test across versions. Songs with one recording only are tested against themselves, which is an upper bound and is labelled as such.

**What is measured**, with the harness as it is and no change to any rule: per recording and per version, what the speech recognizer heard (the share of the lyrics' words found), the rank of the right song and whether it is a candidate, what the current speech rule and the lock words rule would decide and when, and what the proposed rule of ADR-18 would decide; for the 5 songs not in the library, whether any rule decides on a song; for the pitch path, the same where a reference exists. Languages with no speech model on the device (Greek, Tamil, Bengali) have only the pitch path.

**Reporting.** Counts per recording and per language, not rates: with one or two recordings per language no interval is meaningful, and none is computed. No pass bar is set and no threshold or rule is tuned on these recordings. The report says which languages have a speech model and which do not.

**Not claimed.** That anything here shows how the app will do with a person singing, a band, a phone or a room; that a language is "supported"; or that a licence label is correct. These are recordings played as files, mostly old or official, in clean conditions, and sung by trained voices.

### Result of the run

Run on 2026-11-18 on the emulator (no microphone; each recording fed to the real Vosk small model of its language, then replayed on the JVM with the harness unchanged and no rule tuned). 22 recordings played; 18 had a speech transcript. The 4 without are Bengali (2), Greek and Tamil, which have no speech model on the device, as planned. The 5 songs not in the library are Hindi, Bengali (2) and Telugu (2).

One setup fault, corrected before the numbers below: for 7 languages (Chinese, German, Hindi, Japanese, Dutch, Polish, Telugu) the local copy of the speech model was missing the file `ivector/online_cmvn.conf`, so the first capture could not load them and wrote nothing. The file holds one comment line in every model that has it, and the official archives of the app's download contain it (checked for German, Dutch, Polish and English from the archive directory). It was restored in the local, git-ignored copy and the 7 languages were captured again. The result does not depend on it, and the app's download is not affected.

**Speech, per recording.** "Distinct words heard" is the number of different words the recognizer reported over the whole recording; "of them in the lyrics" is how many of those are words of the stored lyrics. Both are counts, not a rate.

| Recording | Distinct words heard | Of them in the lyrics | Speech: right song a candidate / decides on it |
|---|---|---|---|
| English, studio | 35 | 11 | yes / yes |
| English, live | 42 | 11 | yes / yes |
| German | 34 | 4 | no / no |
| Polish | 15 | 3 | no / no |
| Korean, live | 12 | 0 | no / no |
| Japanese, 1930 | 6 | 0 | no / no |
| Ukrainian | 5 | 0 | no / no |
| Spanish | 3 | 1 | no / no |
| Swedish, 1938 | 3 | 0 | no / no |
| Dutch, choir | 2 | 0 | no / no |
| Dutch, 1899 | 1 | 0 | no / no |
| Portuguese | 1 | 1 | no / no |
| Italian | 0 | 0 | no / no |
| Chinese | 0 | 0 | no / no |
| Turkish | 0 | 0 | no / no |
| Greek, Tamil | no speech model | n/a | n/a |

On the 5 songs not in the library, speech decided on a song 0 times of 5 (Hindi 1 distinct word heard, Telugu 2 and 2, Bengali no model). Over all 17 recordings of songs in the library, the speech rule decided on the right song for 2 (both English), on a wrong song for 0.

**Pitch, per recording.** For 15 of the 17 recordings the reference of the pitch path is the same recording, so a find there only shows that a contour matches itself and is an upper bound, not recognition across versions. Of those 15, the right song was a candidate for 14; Turkish was not, even against itself (not investigated). The two recordings that are a real cross-version test, the live English one against the studio reference and the 1899 Dutch one against the choir reference, were **not found** by the pitch path in either case. The right song was never a pitch candidate for a recording that was not also its own reference. Greek and Korean each also had the other as a candidate (two candidates each), which is a candidate, not a decision.

**Combined.** The right song was a candidate for 15 of 17 (the 14 self-matches plus the live English one, through speech) and the combined rule decided on it for 2 of 17 (both English). The lock opened on the right song for 13 of 17 on the pitch path alone; it did not open for 4: Korean (found, but the lock stayed shut), English live, Dutch 1899 and Turkish. No recording of a song not in the library led to a decision (0 of 5).

**What it shows, and what it does not.**
- With the models the app ships (Vosk small, about 40 MB a language), speech recognition on whole sung anthems is, in these recordings, close to useless outside English: of the 13 non-English recordings of library songs that have a transcript, 9 produced no word of the lyrics and 4 produced between 1 and 4 (German 4, Polish 3, Spanish 1, Portuguese 1), and the speech rule decided on a song for none. English worked in both versions, with 11 lyric words heard in each. This is a count over one or two recordings per language and does not say how any language works for a person singing.
- Where there is no cross-version evidence, the pitch path looks good only because it was given its own recording. The only two genuine cross-version tests failed.
- No false decision was made on the songs that are not in the library, but 5 recordings are too few to say anything about false accepts.
- This is a result for the current rules and models. It supports the roadmap item to try larger speech models (issue #50) and a pitch reference that is not the query itself; neither has been tried here. Nothing was changed because of these numbers.

## 16. Larger speech models (Whisper-class), stage 1: the plan, fixed before any run

Why: section 15 showed that the shipped small Vosk models hear almost none of the words of a sung anthem outside English. The owner asked for larger models to be tried (roadmap item 8, issue [#50](https://github.com/Sriram-Codes-SW/VerseSync/issues/50)). This section was reviewed by a second model before anything was run and fixes what is done and reported first. It was written before the capture and the replay.

**Question.** Does a Whisper-class model, run on the same 22 recordings and scored by the same replay, hear enough of the lyrics for the speech rule to decide on the right song where the small Vosk models do not? Stage 1 measures accuracy only, on the development PC, with no app code changed. It does not measure speed or battery on a phone.

**Material and tools.** The 22 recordings, the library and the queries of section 15, unchanged. Recognizer: whisper.cpp, prebuilt Windows release tag `b5454` (2026-10-06, the build of the same day as v1.9.5), file `whisper-bin-x64.zip`, sha256 `6ba69e3482d7826214f90a6a9c84ca07782aec1e1d0c6a7c30c994fd5d816ccb`. Models, the quantised files a phone would hold, from the official ggml model repository, with the checksums the repository lists (checked after download):

| Model file | Size | sha256 |
|---|---|---|
| `ggml-tiny-q5_1.bin` | 30.7 MiB | `818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7` |
| `ggml-base-q5_1.bin` | 56.9 MiB | `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898` |
| `ggml-small-q5_1.bin` | 181.3 MiB | `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb` |
| `ggml-large-v3-turbo-q5_0.bin` | 547.4 MiB | `394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2` |

The last one is a **control**, not a candidate: at about 550 MiB it is too large to be the default download. If even it hears few lyric words, the limit is singing and recordings, not model size. The read-speech results of section 8 are the other control (the same recognizers on people speaking). The whisper.cpp licence and the model licences are MIT; the notice would go on the app's licences screen if any is ever shipped.

**Settings, all fixed now.** One call per recording and model: `whisper-cli -m <model> -f <recording> -l <language> -mc 0 -oj -of <out> -t 8 -np`. The language is the recording's own (the app knows the language the user chose); the app's code is passed as it is except Chinese (`cn` becomes `zh`). No prompt of any kind, and in particular none that contains words of the lyrics or of the word "lyrics". No voice-activity detection, no suppression flags, no grammar. The decoder keeps the program's own defaults, which are: beam size 5, best-of 5, entropy threshold 2.40, log-probability threshold -1.00, no-speech threshold 0.60, temperature 0.00 raised by 0.20 on failure, flash attention on. `-mc 0` turns off the carried text context, which is closer to how short clips would be used and is the main cause of repeated loops. The whole recording is given to the program, which cuts it into its own 30 second windows. The threads are fixed at 8 and the wall-clock seconds per recording are written down; a PC is not a phone and these are not on-device speeds. The runner is `tools/audio-test/run_whisper.ps1`.

**Conversion.** `WhisperTranscript` (committed with its unit test TC-U-58 before the run): one transcript entry per segment exactly as emitted, no merging or splitting; the frame is the segment's end time divided by 64 ms, rounded, because the text exists only once the segment is decoded; `final` is true; text is trimmed and otherwise kept as written; segments with no text are dropped. The replay scores each entry on its own, so how text is cut into entries changes the result: Vosk gives many growing partials and Whisper gives one segment per line or so. The table reports the number of entries and words per recording for both so that this can be read. Time to a decision is **not** a stage 1 result.

**Words heard.** A table per model, made by committed code (`WhisperRun`) with the app's own word rules (`spokenMatchTokens`): entries, words, distinct words, distinct words that are words of the stored lyrics, and whether a loop is flagged (one sequence of 3 words appearing 3 or more times; a repeated chorus does that too, so it is a flag to look at, nothing is filtered). For the 5 songs not in the library, the total words and any decision.

**Reported.** All 22 recordings and all 4 models, none dropped, with the Vosk table of section 15 beside them (frozen at the merge of PR #84). Greek and Tamil are included, with no Vosk baseline to compare, and reported on their own lines. The replay is the unchanged `EvaluationRun`, so pitch columns are the same for every model and are not repeated.

**Stage 2 trigger, fixed now.** A second stage (building whisper.cpp into the app behind the speech interface and measuring speed, battery, size and decision time on a device) is proposed **only if** one model of 200 MB or less (tiny, base or small) makes the speech rule decide on the right song for at least **5 of the 13** non-English recordings of library songs that had a Vosk transcript (German, Polish, Korean, Japanese, Ukrainian, Spanish, Swedish, Dutch twice, Portuguese, Italian, Chinese, Turkish), with **0** decisions on a wrong song and **0** decisions on the 5 songs not in the library. If this is not met the result is reported and stage 2 is not started; the decision on #50 is then taken from the numbers, with a second opinion. Greek and Tamil do not count towards the 5. The bar is the author's and can be argued with, but not after the run.

**Known ways the numbers can be off, written down now.**
- Scripts: the Chinese lyrics are in traditional characters and Whisper may write simplified ones; the Japanese lyrics mix kanji and kana and Whisper may choose a different mix. The app matches characters, so these two rows can be low for a reason that is not hearing. Nothing is converted in stage 1; the rows are flagged.
- Korean words of two syllables are dropped by the app's 3-character minimum, for Vosk and Whisper alike. Not changed.
- Numbers are written as digits by Whisper and as words in lyrics. Small effect, not corrected.
- Repeats count: a loop of a lyric line raises the share and a loop of junk lowers it. Nothing is filtered.
- Anthems are probably in the training data of these models, so a model may recall them. That makes the result an upper bound for ordinary songs, and it cannot show that a song nobody has written down would be heard.

**Not claimed.** Anything about speed, battery or size on a phone; anything about a person singing, a band or a room; that a language is supported; or a decision about shipping a model. This is a count over one or two recordings per language, on files, on a PC.

### Result of stage 1

Run on 2026-11-18 on the development PC, as planned in this section: all 22 recordings and all 4 models, none dropped, with the unchanged replay. Three things went wrong in the runner and were corrected, none of them a recognition setting: (1) the runner stopped on the program's progress text under Windows PowerShell 5.1 and wrote nothing, so the first start produced no result; (2) the plan's language table passed the app's code `en-us` to Whisper, which only knows `en`, so both English recordings failed for every model until the table mapped it and only the missing files were run again (a language-code translation, the same as `cn` to `zh`); (3) the runner gained `-SkipExisting` to do that. No recording was run twice with different settings.

**Speech rule, over the 17 recordings of songs in the library and the 5 that are not.**

| | Vosk small (section 15) | tiny q5_1 (31 MiB) | base q5_1 (57 MiB) | small q5_1 (181 MiB) | large-v3-turbo q5_0 (547 MiB, control) |
|---|---|---|---|---|---|
| Decides on the right song | 2 of 17 | 6 of 17 | 7 of 17 | 7 of 17 | 7 of 17 |
| Decides on a wrong song | 0 | 0 | 0 | 0 | 0 |
| Decides on a song for the 5 not in the library | 0 | 1 (a Bengali recording) | 0 | 0 | 0 |
| Of the 13 non-English recordings that count for the trigger, decided right | 0 | 4 | **5** | **5** | 5 |

Which ones: English twice for every model. German and Spanish for every model. Polish for base, small and turbo; Swedish for tiny, base and turbo; Dutch for one of its two recordings for tiny, base and small and for neither with turbo; Italian for small only; Japanese for turbo only. Nobody decided on Chinese, Korean, Portuguese, Turkish or Ukrainian. Greek and Tamil (no Vosk baseline, not counted for the trigger) were never decided.

**Distinct words heard, and how many of them are words of the stored lyrics** (a count over the whole recording; `*` means a loop is flagged, which a repeated chorus also does; `-` no Vosk model):

| Recording | Vosk | tiny | base | small | turbo |
|---|---|---|---|---|---|
| English, studio | 35 / 11 | 48 / 9 | 52 / 14 | 55 / 14 | 53 / 13 |
| English, live | 42 / 11 | 53 / 15 | 54 / 15 | 53 / 14 | 53 / 14 |
| German | 34 / 4 | 27 / 6 * | 68 / 9 * | 73 / 32 | 63 / 33 * |
| Spanish | 3 / 1 | 40 / 10 * | 48 / 12 * | 54 / 18 * | 51 / 24 * |
| Polish | 15 / 3 | 40 / 3 | 40 / 9 | 40 / 13 * | 37 / 17 * |
| Swedish, 1938 | 3 / 0 | 14 / 3 * | 22 / 7 * | 36 / 2 * | 39 / 7 * |
| Dutch, choir | 2 / 0 | 19 / 9 * | 15 / 6 * | 31 / 15 | 2 / 1 |
| Dutch, 1899 | 1 / 0 | 6 / 1 | 3 / 1 | 3 / 1 | 4 / 1 |
| Italian | 0 / 0 | 32 / 1 | 37 / 2 * | 43 / 6 * | 11 / 2 |
| Japanese, 1930 | 6 / 0 | 27 / 0 | 17 / 0 * | 18 / 1 | 25 / 4 |
| Turkish | 0 / 0 | 4 / 0 | 5 / 0 | 24 / 2 | 5 / 0 |
| Portuguese | 1 / 1 | 1 / 0 | 1 / 0 | 3 / 0 | 6 / 1 |
| Ukrainian | 5 / 0 | 24 / 0 * | 11 / 0 * | 63 / 0 | 8 / 0 * |
| Chinese | 0 / 0 | 7 / 0 * | 1 / 0 | 2 / 0 | 28 / 0 |
| Korean, live | 12 / 0 | 1 / 0 | 1 / 0 | 2 / 0 | 5 / 0 |
| Greek | - | 5 / 0 * | 13 / 1 * | 10 / 1 * | 2 / 0 |
| Tamil | - | 34 / 0 * | 47 / 1 * | 47 / 3 * | 46 / 2 * |

On the 5 songs not in the library the models wrote 496 (tiny), 402 (base), 260 (small) and 396 (turbo) words in total, and the speech rule decided on a song once, with tiny. Loops were flagged in 11, 13, 7 and 10 of the 22 recordings; they were not looked into.

**Against the trigger fixed above.** At least 5 of the 13, no wrong decision, no decision on an unknown song, with a model of 200 MB or less: tiny does not meet it (4 of 13, and one decision on an unknown song). **Base and small each meet it, with exactly 5 of 13** and with different recordings, so it is met by the bar and not by a margin. By the rule written before the run, stage 2 is proposed; it is not started by this document, and the decision on #50 needs a second opinion.

**What it shows.**
- The small Vosk models heard almost nothing of these anthems and Whisper-class models hear many more of the lyrics' words: the speech rule goes from deciding on the right song for 2 of 17 recordings to 7 of 17, with no wrong-song decision. German, Spanish and Polish went from nothing to decided.
- The 547 MiB model, the control, did not do better than the 181 MiB one (7 of 17 for both, 5 of 13), and it was worse on Dutch. For these recordings, more model size past about 60 to 180 MiB bought nothing. What stops the rest is not shown here by any experiment: for Korean, Chinese and Japanese the app's own word rules (a 3-character minimum, characters matched as written, traditional against simplified script) can hide what was heard, and the other languages with no decision hear 0 to 3 lyric words in every model.
- Speed on this PC, 8 threads, CPU only, one run: 0.09 (tiny), 0.10 (base), 0.23 (small) and 0.51 (turbo) seconds of wall-clock time per second of audio. A phone is not this PC and none of this is a phone number. The time to a decision is not a stage 1 result, because the text of a segment exists only when the segment has been decoded.

**Not claimed.** That anything here holds for a person singing, a band, a room or a phone's microphone; that a model can be shipped (size, battery, delay, the licences screen and the download flow are all open); that a language is supported; or that the count of 5 of 13 will hold on other songs. These are anthems, probably in the training data of these models, on files, with one or two recordings per language. Greek and Tamil have only one recording each.

## 17. Larger speech models, stage 2: the plan, fixed before any live measurement

Why: stage 1 (section 16) ran Whisper on whole recordings at once. The app listens live, and Whisper is not a streaming recognizer, so it has to decode windows of the last few seconds again and again. That changes the result in ways stage 1 cannot show: far more decodes per song, so far more chances for a made-up word to make a wrong song a candidate; and a different decoder setting for speed. This section was reviewed by a second model and is fixed before any live measurement. It does not decide to ship anything.

**What stage 2 can and cannot show.** There is an x86_64 emulator and a PC, and no phone. Allowed: accuracy and decision time in seconds of audio from a PC simulation of the live windows; an end-to-end run on the emulator with files fed in place of the microphone; exact model and library sizes; native memory and its return to the baseline after repeated start, stop and release; language switching. **Not allowed without a phone:** any claim about phone speed, battery, heat or real-time operation. An emulator timing on this PC is a PC timing. Stage 2 therefore stops at "works end to end on the emulator", and the phone measurements are listed as open and handed to the owner with a fixed protocol.

**Live decoding, fixed now.**
- Model: `ggml-base-q5_1.bin` only (it met the stage 1 trigger and is 2.3 times faster than small).
- A window of the last **10 seconds**, a hop of **5 seconds**, at most **one decode in flight**; when a decode ends, the newest window is taken and nothing is queued.
- Kept from stage 1: the language is forced (with the app-to-Whisper table tested: `en-us` to `en`, `cn` to `zh`, `vn` to `vi`, others as they are), no carried text context (`-mc 0`), no prompt of any kind, no voice-activity detection.
- Changed for speed, so stage 1 is not the same measurement: greedy decoding (beam 1, best-of 1), temperature 0 with no fallback, no timestamps, non-speech tokens suppressed, 4 threads. The program's own no-speech threshold stays at its default 0.60.
- Silence and music: a window whose loudness (RMS of samples from -1 to 1) is below **0.005** is not decoded (silence measured on the emulator microphone is about 0.00002; the value was not chosen from any Whisper result); a window whose text contains one sequence of 3 words three or more times is dropped (the stage 1 loop flag, now a filter).
- One entry per window: the window's whole text as one string, never blank, one value per window. In the simulation the entry's frame is the window's end.
- Control arm, PC simulation only: a **30 second** window with a **10 second** hop, in case 10/5 is too short. The device uses 10/5 unless 10/5 fails gate A and 30/10 passes it, which is decided by the numbers and nothing else.
- Pitch path and Vosk are untouched. When the Whisper path is on and its model is present, it **replaces** Vosk for every language; the two never run together, because the engine keeps the best evidence per song over all entries and two recognizers would raise false accepts and mix up what is measured.

**Gate A, the PC simulation (first and blocking).** The 22 recordings of section 15 are cut into those windows and run through the real program with those settings, the entries go into the unchanged replay. Passes only if all hold: at least **5 of the 13** non-English recordings decided right; **0** wrong-song decisions; **0** decisions on the 5 songs not in the library; and **0** decisions on a new negative set of at least 10 clips of 60 seconds with free licences (applause or crowd noise, instrumental music, speech), fetched and listed in the manifest before the run. The report also counts false decisions and candidates **per session-minute** for the negatives and for the songs not in the library, because evidence in the engine is kept until the session stops and never decays, so the chances for a false accept grow with the number of decodes. If gate A fails, work stops there and the result is reported.

**Gate B, the emulator.** The same decisions as gate A on at least **20 of 22** recordings, 0 wrong, with the recordings fed through a fake audio source, and the lifecycle tests green (release during a decode, 20 start-stop-release cycles with memory back to the baseline, language switching).

**Gate C, the owner's phone, named in the result.** The 90th percentile of decode time at or below the 5 second hop; battery per hour of listening against a Vosk-only run on the same phone. The Whisper path stays **off by default and in debug builds only** until gate C is passed.

**Build, fixed now.** whisper.cpp through CMake and JNI, from the same release as stage 1 (`b5454`), source vendored under `third_party/whisper.cpp` with its licence and a version file, in a separate Gradle module used by debug builds only. ABIs `arm64-v8a` and `x86_64` set in the module's CMake filters, not globally (the Vosk library ships 32-bit libraries that a global filter would drop); a 32-bit device gets "Whisper unavailable". Linked for 16 KB pages, with a CI check of the alignment of every native library, including Vosk's. The model is downloaded data, never bundled: one file, sha256 checked, a path of its own (the existing zip path checks no checksum), hosted for any release on the project's own release page with its hash. The sherpa-onnx route was not chosen: the evidence is on the ggml model, its own model export would need stage 1 again, and its runtime would add more native code. The toolchain (command-line tools, NDK, CMake) is not installed on the development PC and is a large download that needs the owner's yes before it is fetched.

**Order of work, each step a small pull request that passes CI.** (0) this plan; (1) the speech interface extracted from the engine with no change of behaviour; (2) the simulation harness and the gate A result, stopping there if it fails; (3) the native module, the CI toolchain step and the alignment check, debug builds only; (4) the Whisper recognizer, the frame fan-out (the engine's microphone frames, copied because the source reuses its buffer, go to both the fingerprinter and the recognizer) and the verified download, behind a debug-only setting that is off; (5) the gate B result; (6) the phone protocol and the request to the owner. Before any release: the MIT notices of whisper.cpp, ggml and the model weights on the licences screen, and the optional download (about 57 MiB, one file for all languages) stated on the store listing. Whether Whisper is a Pro feature is a product question left to the owner.

**Not claimed.** Anything about a phone; that gate A predicts a live result; that 10/5 is the best window; a decision to ship. Anthems are probably in the training data of the model.
