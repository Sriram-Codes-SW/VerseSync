| Field | Value |
|---|---|
| Document | VerseSync SSDLC Document Index |
| Version | 0.5 |
| Date | 2026-11-08 |
| Author | Sriram-Codes-SW |
| Status | Approved |

# VerseSync Documentation

This directory contains the system architecture, security, design records (ADRs), and test specifications for VerseSync.

## Document Index

| Document | Purpose | Current Version | Status |
|---|---|---|---|
| [docs/03-design.md](03-design.md) | System Architecture & Architecture Decision Records (ADRs) | 0.5 | Active |
| [docs/06-test-strategy.md](06-test-strategy.md) | Test Strategy, Verification Matrix & Test Cases (TC-U-xx, TC-M-xx) | 0.5 | Active |

## Revision History

| Version | Date | Author | Description |
|---|---|---|---|
| 0.1 | 2026-11-08 | Sriram-Codes-SW | Initial architectural audit and ADRs for audio capture, recognition engine, and matching pipelines. |
| 0.2 | 2026-11-08 | Claude Code | ADR-07 to ADR-12 for release hardening, billing entitlement, inbound data integrity, app-lock startup, untrusted input and data durability. Test cases TC-U-11 to TC-U-14 and TC-M-01 to TC-M-03. |
| 0.3 | 2026-11-08 | Claude Code | Amendments to ADR-01, ADR-03, ADR-04, ADR-11 and ADR-12 to match the code, a known open items section, TC-U-15, and a corrected TC-U-10. |
| 0.4 | 2026-11-08 | Claude Code | Preferences classes take an injectable DataStore. DataStore-backed tests are isolated and run on a real Main thread. Removed the test isolation open item. |
| 0.5 | 2026-11-08 | Claude Code | Enabling the app lock requires a device screen lock or biometrics. Added manual test cases TC-M-04 to TC-M-07, run on an emulator. |
