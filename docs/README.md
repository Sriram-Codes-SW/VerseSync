| Field | Value |
|---|---|
| Document | VerseSync SSDLC Document Index |
| Version | 0.10 |
| Date | 2026-11-08 |
| Author | Sriram-Codes-SW |
| Status | Approved |

# VerseSync Documentation

This directory contains the system architecture, security, design records (ADRs), and test specifications for VerseSync.

## Document Index

| Document | Purpose | Current Version | Status |
|---|---|---|---|
| [docs/03-design.md](03-design.md) | System Architecture & Architecture Decision Records (ADRs) | 0.10 | Active |
| [docs/06-test-strategy.md](06-test-strategy.md) | Test Strategy, Verification Matrix & Test Cases (TC-U-xx, TC-M-xx) | 0.10 | Active |

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
