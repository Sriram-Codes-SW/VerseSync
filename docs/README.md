| Field | Value |
|---|---|
| Document | VerseSync SSDLC Document Index |
| Version | 0.31 |
| Date | 2026-11-08 |
| Author | Sriram-Codes-SW |
| Status | Approved |

# VerseSync Documentation

This directory contains the system architecture, security, design records (ADRs), and test specifications for VerseSync.

## Document Index

| Document | Purpose | Current Version | Status |
|---|---|---|---|
| [docs/03-design.md](03-design.md) | System Architecture & Architecture Decision Records (ADRs) | 0.28 | Active |
| [docs/06-test-strategy.md](06-test-strategy.md) | Test Strategy, Verification Matrix & Test Cases (TC-U-xx, TC-M-xx) | 0.29 | Active |
| [docs/07-pitch-detection-design.md](07-pitch-detection-design.md) | Pitch Detection Design & Refactor Plan (independence from TarsosDSP) | 0.1 | Proposed |
| [docs/PRIVACY.md](PRIVACY.md) | Privacy Policy (draft for the owner's review) | 0.1 | Draft |
| [docs/RELEASING.md](RELEASING.md) | Release checklist, signing and the GPL-3.0 source offer | 0.1 | Active |
| [docs/08-quality-roadmap.md](08-quality-roadmap.md) | Recognition Quality Roadmap: where it stands and what to build next | 0.7 | Proposed |
| [docs/09-evaluation-harness.md](09-evaluation-harness.md) | Evaluation Harness: replay recordings, measure accuracy and false accepts, recording protocol | 0.7 | Active |

## Revision History

| Version | Date | Author | Description |
|---|---|---|---|
| 0.1 | 2026-11-08 | Sriram-Codes-SW | Initial architectural audit and ADRs for audio capture, recognition engine, and matching pipelines. |
| 0.2 | 2026-11-08 | Claude Code | ADR-07 to ADR-12 for release hardening, billing entitlement, inbound data integrity, app-lock startup, untrusted input and data durability. Test cases TC-U-11 to TC-U-14 and TC-M-01 to TC-M-03. |
| 0.3 | 2026-11-08 | Claude Code | Amendments to ADR-01, ADR-03, ADR-04, ADR-11 and ADR-12 to match the code, a known open items section, TC-U-15, and a corrected TC-U-10. |
| 0.4 | 2026-11-08 | Claude Code | Preferences classes take an injectable DataStore. DataStore-backed tests are isolated and run on a real Main thread. Removed the test isolation open item. |
| 0.5 | 2026-11-08 | Claude Code | Enabling the app lock requires a device screen lock or biometrics. Added manual test cases TC-M-04 to TC-M-07, run on an emulator. |
| 0.6 | 2026-11-08 | Claude Code | ADR-13 licenses the project under GPL-3.0. Added LICENSE and THIRD_PARTY_NOTICES.md, corrected the README, and updated the open items. |
| 0.7 | 2026-11-08 | Claude Code | ADR-14 script-aware lyric tokenization, test cases TC-U-16 to TC-U-19 covering all 29 languages. |
| 0.8 | 2026-11-08 | Claude Code | ADR-15 speech model availability, amendments to ADR-05 and ADR-07, real-audio results for 28 languages, test cases TC-U-20, TC-U-21, TC-M-08 and TC-M-09. |
| 0.9 | 2026-11-08 | Claude Code | Changes from an external review: authenticators on Android 9 and 10, corrupt settings, search cost, size check, safe model install, billing refresh, migration test. Test cases TC-U-22 to TC-U-27 and TC-M-10, and two open items. |
| 0.10 | 2026-11-08 | Claude Code | ADR-16 application id change with a written migration note, and test case TC-M-11. |
| 0.11 | 2026-11-08 | Claude Code | Added 07-pitch-detection-design.md (Proposed), the plan for making the pitch logic independent of TarsosDSP. New open items for CI, end-to-end sync, real devices, the level meter, the unused view model, matching enhancements and the release checklist, each linked to its issue. |
| 0.12 | 2026-11-08 | Claude Code | Added PRIVACY.md (draft) and RELEASING.md for the first release (issue #18). The README no longer claims everything happens on the device, because language suggestion and model downloads use the network. |
| 0.13 | 2026-11-08 | Claude Code | CI and formatting resolved: the CI workflow and Spotless are in place and the `build` check is required on `main`. Removed that open item. |
| 0.14 | 2026-11-08 | Claude Code | Follow-ups from a review: the CI workflow also builds the release APK, PRIVACY.md corrected to Android 11 and older, key files ignored by git, new open items for pre-release dependencies and the migration test. The document header version now matches this table. |
| 0.15 | 2026-11-08 | Claude Code | The `Protect main` ruleset now also requires branches to be up to date before merging. RELEASING.md updated. |
| 0.16 | 2026-11-08 | Claude Code | Fixed the level meter (it showed pitch confidence, now loudness), removed the unused FingerprintingViewModel, and offered pitch-only languages on the recognition screen (issues #14, #15, #5). Test cases TC-U-28 to TC-U-30 and TC-M-12. Three open items removed. |
| 0.17 | 2026-11-08 | Claude Code | Phase 0 of the pitch design is done: characterization tests for the current detector (TC-U-31) and a progress note in 07-pitch-detection-design.md. |
| 0.18 | 2026-11-08 | Claude Code | Real-audio end-to-end test of lyric sync, recognition and the vocal lock (TC-M-13, issue #9), the vocal lock attempt rule extracted and tested (TC-U-32), and a new open item for background noise being fingerprinted (#33). |
| 0.19 | 2026-11-08 | Claude Code | ADR-17: Room 2.8.5 and biometric 1.4.0-beta01 replace the alpha versions. The app lock was run on Android 9, 10 and 16 emulators (TC-M-14, issue #7). Open items for Android 9 and 10 and for the pre-release dependencies replaced by one for the biometric beta. |
| 0.20 | 2026-11-08 | Claude Code | Model unpacking moved from a broadcast receiver thread to a WorkManager job (issue #6), with TC-U-33 and TC-M-15. New open item for downloads that finish while the app is closed (#36). |
| 0.21 | 2026-11-08 | Claude Code | Phase 1 of the pitch design: audio capture and pitch detection are behind interfaces, TarsosDSP is confined to one adapter, and the tests no longer use reflection. Test cases TC-U-34 and TC-U-35 (issue #13). |
| 0.22 | 2026-11-08 | Claude Code | A silence threshold keeps a faint hum out of fingerprints (ADR-04 amendment, TC-U-37, issue #33). The open item now says the lock can still open on the wrong audio for another reason. The end-to-end script can play a hum (HUM=1). |
| 0.23 | 2026-11-08 | Claude Code | Matching needs more evidence (issue #41): two different heard words for speech, a 2.5 semitone pitch threshold, and a stricter rule for the vocal lock. ADR-03, ADR-04 and ADR-10 amendments, TC-U-38 and TC-U-39. |
| 0.24 | 2026-11-17 | Claude Code | Added 08-quality-roadmap.md (Proposed), the plan for raising recognition quality, tracked in issue #51 with items #43 to #50. |
| 0.25 | 2026-11-17 | Claude Code | Added the evaluation harness (09-evaluation-harness.md, issue #43): the matching rules are shared functions, recordings are replayed on the JVM with transcripts captured on a device, and a report gives accuracy and false-accept rates with intervals. TC-U-40 and TC-M-17, and a first seed run. |
| 0.26 | 2026-11-17 | Claude Code | Speech scoring (issue #44): words weighted by rarity, a lead over the other songs required for the vocal lock to open on words, optional fuzzy matching (off), and a parameter sweep in the harness. ADR-03 and ADR-10 amendments, TC-U-41. The seed corpus cannot choose the settings, so the defaults stay and #44 stays open. |
| 0.27 | 2026-11-17 | Claude Code | A same-language FLEURS corpus (read speech, 353 recordings, 5 languages) with a fixed-seed builder, a fetch script, decision measures and a 120-setting sweep in the harness. At the current rules the speech path decides on a wrong song for 38 percent of recordings of songs not in the library, and pitch finds 16 percent of recordings that are pieces of their own reference. No rule is changed. TC-U-42 and TC-M-18. |
| 0.28 | 2026-11-17 | Claude Code | Held-out choice of a speech decision rule (issue #44): FLEURS dev and test corpora (470 and 606 recordings), a decision rule that sees every song and is judged acting first, a criterion fixed in the docs before the run, and a leave-one-language-out table. The rule chosen on dev missed its target on test (7.8 percent against at most 2 percent) and is not adopted; the rival fix shows the first sweep overstated false decisions somewhat. No app change. TC-U-43 and TC-M-19. |
| 0.29 | 2026-11-17 | Claude Code | Diagnostics of the held-out decision-rule result (issue #44): the end-of-file result no longer counts as a decision (the chosen rule's right-song rate corrects from 86 to 78 percent), 20 of 25 false decisions did not survive to the end, and the test corpus is harder than dev. Adds settle rules (hold in frames, evidence that can be taken back), time to the right answer, a fixed-size library option, a train split fetch, and the plan and criterion for the next evaluation, fixed before it is run. No app change. TC-U-44, TC-U-45 and TC-M-20. |
| 0.30 | 2026-11-17 | Claude Code | The pre-registered evaluation of settle rules (issue #44): dev and test (1,076 recordings) to select and the FLEURS train split (812 recordings, 80-song libraries) to report once. The chosen rule (4 words, share 0.8, gap 1.0 times ln(1 + songs), no hold) decided on a song not in the library for 0 of 707 (upper end 0.5 percent) against 46 percent at the current speech rule, and named the right song for 70 percent against 90 percent. Waiting before acting did not help; stricter thresholds did. Read speech only; not in the app. TC-U-46 and TC-M-21. |
| 0.31 | 2026-11-17 | Claude Code | ADR-18 (Proposed): the speech decision rule that passed the fresh-corpus bar as its own decision layer for the vocal lock. It states the evidence, the exact rule, what was not measured, and five conditions before any code changes. A proposal only; nothing in the app changes. |
