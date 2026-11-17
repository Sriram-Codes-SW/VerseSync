| Field | Value |
|---|---|
| Document | VerseSync Recognition Quality Roadmap |
| Version | 0.12 |
| Date | 2026-11-17 |
| Author | Claude Code |
| Status | Proposed |

# Recognition Quality Roadmap

Tracked in issue [#51](https://github.com/Sriram-Codes-SW/VerseSync/issues/51).

## 1. What this is for

VerseSync identifies a song from a few seconds of someone singing or saying its lyrics, and has an optional voice shortcut (called the vocal lock before issue #48). This plan says what to build, in what order, to make that work as well as it can offline.

**Which comparison is fair.** The right comparison is a hum-to-search product, such as Google's or SoundHound's. It is not Shazam, which matches exact studio recordings, and not a general speech recognizer, which VerseSync is not. Those products search millions of songs. VerseSync searches the user's own tens or hundreds, which is a much easier problem. So today's accuracy is lower than a small closed library should give.

## 2. Where it stands

Measured on an emulator, one sentence per language. These numbers are noisy: the same build gives different results between runs, and nothing has been tried on a real phone or on singing.

| Area | State |
|---|---|
| Speech | Vosk small offline models, 21 languages. The text is often garbled, for example "maybe was on think about" for a sentence about dinosaurs. A song matches when at least 0.35 of the heard words and at least two different heard words are in its lyrics. With that rule 15 of 21 languages passed, and four of the six that failed passed under the old one-word rule. |
| Pitch | One Hz value per 64 ms frame. On two short spoken clips, the same clip was accepted about half the time and a different clip in 1 of 12 comparisons at the current setting. |
| Pitch defect | The stored contour is centered on its whole mean and a short live snippet on its own mean, so a perfect fragment from the middle of a song is shifted by a constant. Frames are compared one to one with a coarse 5 frame offset search, and there is no tempo warping. |
| Voice shortcut | Opens on three different heard words or a close pitch over about two seconds (ADR-10). It checks the words, not the speaker. |
| Capture | Two microphone recorders still run at once. The single shared capture exists as draft #39 with inconclusive evidence. |
| Tests | 376 unit tests, but no evaluation set, no real phones, no singing recordings. |

## 3. How this plan was made

It combines two assessments: my own, from the measurements above, and a second model's review (Claude Opus) of the same briefing. They agreed on the main points. A review by the Fable models was attempted twice and could not run because of a usage limit. The second model's claims are advice, not measurement, and the ones I could not verify are in section 6.

## 4. The roadmap

Order matters. Everything is judged by the evaluation set, so it comes first. Thresholds should stop being set from a handful of clips.

| # | Item | Issue | Effort | Success is | Depends on |
|---|---|---|---|---|---|
| 1 | Evaluation set and replay harness | [#43](https://github.com/Sriram-Codes-SW/VerseSync/issues/43) | 3 to 4 weeks | One command gives the same numbers every time, with a baseline and intervals | none |
| 2 | Score every song, require a margin, fuzzy word matching | [#44](https://github.com/Sriram-Codes-SW/VerseSync/issues/44) | 1 to 2 weeks | Better top-1 accuracy at a fixed 1 percent false-accept rate | 1 |
| 3 | Restrict the recognizer to the library's words | [#45](https://github.com/Sriram-Codes-SW/VerseSync/issues/45) | about 1 week | Better word hit rate, and no worse false accepts on songs not in the library | 1, 2 |
| 4 | Rebuild pitch matching for singing | [#46](https://github.com/Sriram-Codes-SW/VerseSync/issues/46) | 2 to 3 weeks | Better equal error rate on singing, including plus or minus 15 percent tempo | 1 |
| 5 | Combine text and pitch scores | [#47](https://github.com/Sriram-Codes-SW/VerseSync/issues/47) | about 1 week | Beats the better single path | 2, 4 |
| 6 | Voice shortcut: honest naming and false-accept measurement | [#48](https://github.com/Sriram-Codes-SW/VerseSync/issues/48) | a few days | Honest copy, and a measured false-accept rate with an interval | 1 |
| 7 | Shared capture on real phones | [#49](https://github.com/Sriram-Codes-SW/VerseSync/issues/49) | about 1 week | Identical frames, no dropouts, no lower pass rate | 1 |
| 8 | Whisper-class model spike | [#50](https://github.com/Sriram-Codes-SW/VerseSync/issues/50) | about 2 weeks | Accuracy gain weighed against delay, battery and size | 1 |

### 4.1 The evaluation set

The set has to be good enough to trust, because the current setup is not.

- Three or four real phones, at low, mid and high price levels.
- About 20 to 30 speakers, adults plus children with consent. Recordings stay private.
- Singing and speaking, in the five or six languages that will be officially supported.
- Snippets of 3, 5 and 10 seconds from random start points, in quiet, with television in the background, in the street and in a car.
- Queries for songs that are not in the library, to measure false accepts.
- Record once, replay deterministically in JVM tests.
- Report top-1 accuracy, false-accept rate and latency per language with confidence intervals. Estimating a 1 percent false-accept rate takes hundreds of trials per condition.

### 4.2 The pitch rebuild

Smooth the contour and fix octave jumps, search over key shifts instead of centering on the mean, and use subsequence DTW or note-interval contours so a short sung piece can match anywhere in a stored one at a different tempo. Use pitch matching for singing only. This supersedes the plan in #16.

## 5. Not planned

- **Shazam-style fingerprinting of different performances** (covers, live versions, the person singing). Two performances of a song do not share the spectral peaks it relies on. Fingerprints of the SAME recording playing again are proposed in ADR-19, offline, for songs whose fingerprints are on the phone; a catalog of every song is not possible offline without a licensed one.
- **Pitch matching of spoken input.** A spoken sentence has no stable melody.
- **Any speaker-identity or "secure" claim for the voice shortcut.** It would need a speaker model, enrollment across sessions, liveness checks and measured error rates across many speakers including children. Even then it would not be the strongest class of Android biometric.
- **Replacing TarsosDSP for now.** GPL-3.0 is accepted (ADR-13), so there is no license reason. Phases 3 and 4 of the pitch design wait.
- **Bluetooth protocol v2 (#10) and full-text search (#17)** until there is a reason.
- **A claim of 21 supported languages.** Label most of them experimental until the evaluation set shows otherwise.

## 6. Not verified

- That Vosk's small models can take a runtime grammar that restricts them to the library's words. Check before relying on it.
- On-device speed, size and quality of a Whisper-class model.
- The license of every Vosk model, language by language.
- The history of Google's "trusted voice" feature, mentioned in the second review as a reason not to rely on voice unlock. The reviewer was not sure of it either.

## 7. Risks

- **Google Play.** Explain the microphone permission and answer the Data safety form to match the privacy policy. Background microphone limits and accessibility-service rules do not apply, because the lock is an in-app screen that listens only after a button press. Every accuracy claim in the listing must be backed by the benchmark. Pro purchases must use Play Billing.
- **Children.** If children use the app, the families policy and children's privacy rules for voice data apply.
- **GPL-3.0.** Anyone may rebuild Pro for free. Sell convenience and support, not a license check. Every release needs its matching source.
- **Lyrics are copyrighted.** Do not bundle or scrape them. Store pitch contours, not raw audio.
- **False comfort.** A false accept on the lock gives a false sense of security.

## 8. Progress

- **Item 1, evaluation set and harness ([#43](https://github.com/Sriram-Codes-SW/VerseSync/issues/43)):** the harness is built and a seed baseline is recorded (docs/09). The real recordings are still to be made.
- **Item 2, scoring and margin ([#44](https://github.com/Sriram-Codes-SW/VerseSync/issues/44)):** partly done. Words are weighted by rarity in the library, each match carries the strongest other song's score, and the vocal lock no longer opens on words when another song is tied or within 25 percent. Fuzzy word matching is built but off, and the margin and minimum evidence are not yet chosen from data, because the seed corpus cannot tell any setting from another (docs/09, section 8). It stays open for that.
- **A larger public corpus (follow-up to #43):** a same-language corpus of read speech from FLEURS is built and measured (docs/09, sections 8 and 9). It showed that the current speech rule decides on a wrong song for 38 percent of recordings of songs not in the library, which the seed corpus hid, and that pitch finds only 16 percent of recordings that are pieces of their own reference. Neither is changed yet. Choosing the speech rule needs a held-out part of the data, and the pitch result is the evidence for item 4.
- **Held-out choice of a speech decision rule (#44):** a rule chosen on the FLEURS dev split missed its pre-set target on the test split (7.8 percent of unknown-song recordings against at most 2 percent), though it cut that rate from 53 percent and naming the right song stayed at 86 percent. Nothing is adopted. The next thing to try is a rule that waits for the evidence to settle before acting, and the real recordings are still needed (docs/09, section 10).
- **Why the held-out rule failed, and the plan for a rule that waits (#44):** the end-of-file result had been counted as a decision, which overstated the right-song rate (86 percent, corrected to 78 percent); 20 of the 25 false decisions did not survive to the end of the recording, and the test corpus is also harder than dev (fr and en-us), so early firing is part of the explanation and not all of it. A rule that holds a decision before acting, with evidence that can be taken back, is built and tested on generated data and its evaluation is fixed in advance on a fresh corpus (docs/09, sections 11 and 12). Nothing in the app changes.
- **A speech decision rule passed its pre-registered bar on a fresh corpus (#44):** at least 4 different words, at least 80 percent of the heard words in the lyrics, and a rarity-weighted gap over the runner-up of 1.0 times `ln(1 + songs)`. On 812 recordings of read speech from the FLEURS train split it decided on a song not in the library for 0 of 707 (upper end 0.5 percent), against 46 percent for the current speech rule, and named the right song for 70 percent of recordings in the library against 90 percent. Waiting before acting did not help; the stricter thresholds did. It is read speech only, is not in the app, and needs an ADR and a check on real singing before it could be (docs/09, section 12).
- **ADR-18 proposes the speech decision rule as its own layer for the vocal lock (#44):** it records the evidence, the options considered, the exact rule, what was not measured, and five conditions that must be met before any code changes, including a check on real singing and on a real phone. It is a proposal only and authorizes nothing (docs/03-design.md).
- **The vocal lock is now the voice shortcut (#48), in the words a person reads only:** the name, the settings description ("Anyone who knows the words can open it") and the lock screen no longer suggest authentication or identity, the wording is in one tested place, and a threat model is written (docs/10). The false-accept rate on a negative set of television, music, other voices and silence is still not measured; #48 stays open for it.
- **Recognizing recorded music offline (ADR-19, Proposed):** the owner wants a Shazam-style app that works offline. A recording playing near the phone is a different input from singing, and speech and pitch both fail on it. Landmark fingerprints of the same recording are proposed, limited to songs whose fingerprints are on the device. Phase 1 is a matcher and a generated test set, with its plan and pass bar fixed in advance (docs/09, section 13).
- **Result of the recorded-music fingerprint run (ADR-19, generated music):** no rule met the pre-registered false-accept target, so none is chosen. The matcher finds a recording in noise but is very sensitive to playback speed (a 1 percent error leaves 14 percent or 4 percent) and does not follow a performance whose tempo is a few percent off, so it is not the concert path. Cost is about 110 KB per four-minute song. Next, per a second opinion: a Proposed ADR for a closed-set concert mode (a setlist, manual first), a harness option that restricts the library to the size of a setlist (for example 15, 20 and 30 songs), and a concert mode without recognition. Nothing real has been measured (docs/09, section 13).
- **ADR-20 proposes a concert mode, manual first (Proposed, #66):** the owner's use is a concert with a poor network. Nothing measured shows the app can tell which song a live band is playing, so the proposal is a setlist the person orders, one-tap next, previous and jump, and hand scrolling, with no recognition and no microphone. Any later recognition would only suggest, would restrict itself to the setlist, and needs its own ADR with a measured pass bar. The assumption that a setlist is known and mostly played in order is not measured. It authorizes no code (docs/03-design.md).
- **ADR-20 Phase 1 decisions (Proposed, #66):** an Opus second opinion put the concert-mode setlist ahead of the harness work, because it helps without any recognition claim. The decisions cover storage, backup scope, concert mode free of any Pro check (the library limit and Pro unchanged), the app lock across a rotation (#69), and the order of work. The harness at setlist size, the sequence simulator and the live-album protocol follow it, and generated concert noise is deferred until real recordings exist (#43).
