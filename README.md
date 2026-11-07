# VerseSync: The Offline Lyric Creation & Recognition Studio

VerseSync is an offline-first mobile application that empowers users to create perfectly synced, karaoke-style lyrics for any song and recognize music using a powerful, on-device hybrid engine.

## Core Features

-   **Interactive Lyric Syncing:** Manually create timestamps for any lyrics using an intuitive "karaoke-style" tapping interface. Your song, your sync.
-   **Hybrid Song Recognition (Pro):** A unique, on-device engine that combines **Audio Fingerprinting** and **Speech-to-Text** to accurately identify songs from your personal library, even in noisy environments like concerts.
-   **Offline First:** All your data, syncing, and recognition happens 100% on your device. No internet connection required for core features.
-   **Multi-Language Support:** Create, manage, and recognize songs and lyrics in multiple languages.
-   **Peer-to-Peer Sharing:** Share your synced lyric files directly with other VerseSync users via Bluetooth.

## Monetization Model

We believe in providing core value for free while offering powerful enhancements to our dedicated users. Our model is designed for transparency and sustainability.

1.  **Freemium:**
    *   **Free Tier:** Users can experience the core functionality of the app, including creating and saving up to **10 synced songs**.
    *   **Pro (One-Time Purchase):** A one-time upgrade unlocks **unlimited song storage**.

2.  **Tip Jar (Early Stages):**
    *   For our first 50,000 users, we offer a "Tip Jar" for those who wish to show extra support for development. This will be discontinued as our community grows to streamline our business model.

3.  **Advanced Feature Subscription (VerseSync Pro+):**
    *   An optional yearly subscription that grants access to our most advanced, cutting-edge features, including the **Hybrid Recognition Engine** for concert-level identification. This subscription supports our ongoing R&D.

> **Current implementation:** the app defines one Google Play product, the `pro_subscription` subscription, which unlocks all Pro features. The one-time Pro purchase and the separate Pro+ tier described above are not implemented yet.

## Technology Stack

-   **Platform:** Native Android (Kotlin) & iOS (Swift).
-   **UI:** Jetpack Compose (Android) & SwiftUI (iOS).
-   **Architecture:** MVVM with a Repository Pattern.
-   **Database:** Room (Android) & Core Data (iOS) with Full-Text Search capabilities.
-   **Recognition v1:** Vosk Offline Speech Recognition.
-   **Recognition v2:** Proprietary Hybrid Engine combining open-source Audio Fingerprinting and Vosk STT.
-   **Connectivity:** Native Bluetooth APIs.

---

## The Official Development Plan & Roadmap

(The detailed, 4-phase plan remains here as previously defined)

### **Phase 1: The Foundation (Android v1 MVP)**
*   **Goal:** Launch a functional, **accessible**, and useful product to validate the core idea.
*   **Timeline:** 4 Months
*   **Strategic Focus:** **Product & Accessibility.**

### **Phase 2: Validation & Refinement**
*   **Goal:** Grow the initial user base, gather feedback, and prepare for professional expansion.
*   **Timeline:** Months 5-7
*   **Strategic Focus:** **Community, Design, & Inclusion.**

### **Phase 3: Cross-Platform Expansion & R&D**
*   **Goal:** Capture the iOS market and begin work on the next-generation recognition engine.
*   **Timeline:** Months 8-13
*   **Strategic Focus:** **Growth, Future Tech, & Parity.**

### **Phase 4: The Scale-Up (v2 Launch & Team Growth)**
*   **Goal:** Launch the advanced Hybrid Recognition Engine and structure the team for sustainable growth.
*   **Timeline:** Months 14-20
*   **Strategic Focus:** **Monetization, Delegation, & Leadership.**

---

## Engineering Best Practices & Security Protocol

This section outlines the professional standards adopted for the VerseSync project to ensure security, maintainability, and protection of intellectual property.

### 1. Project & Repository Setup
*   **Visibility and license:** The repository is public and licensed under the GNU General Public License v3 (see `LICENSE` and `THIRD_PARTY_NOTICES.md`). Release builds are still obfuscated with R8, but the source is open by design; see ADR-13.
*   **Branching Strategy:** We will use a `GitFlow`-like strategy:
    *   `main`: Contains stable, production-ready code only.
    *   `develop`: The integration branch for features currently in development.
    *   `feat/feature-name`: Specific branches for new features (e.g., `feat/bluetooth-sharing`).
*   **Commits:** Adherence to **Conventional Commits** (`feat:`, `fix:`, `docs:`) will be mandatory to enable automated changelogs and assist CI/CD processes.

### 2. Managing Secrets (API Keys & Credentials)
To prevent hardcoding sensitive keys, the project will use the **Secrets Gradle Plugin**. All keys will be stored in the root `local.properties` file, which is excluded from Git by default.

### 3. R8 Obfuscation & Code Protection
To protect the app's proprietary logic from reverse engineering, R8 will be enabled for all release builds.
*   **Configuration:** `isMinifyEnabled = true` and `isShrinkResources = true` will be set in the `release` build type.
*   **Rules:** Custom ProGuard rules will be maintained in `proguard-rules.pro` to ensure full functionality of Jetpack Compose and other essential libraries after obfuscation.

### 4. Privacy & Legal Compliance
*   **Privacy Policy:** A clear and concise privacy policy will be publicly available at a stable URL (hosted via GitHub Pages). It will state that all data is stored locally on the user's device and will list the purpose of all requested permissions.
*   **Developer Verification:** The Google Play Console account will be kept up-to-date with all required developer verification details to comply with Google's 2025/2026 policies.

### 5. Production Security & Distribution Integrity
*   **Play Integrity API:** The Play Integrity API will be integrated to verify that the app is an unmodified version running on a genuine Android device, protecting against piracy and tampering.
*   **Play Licensing Library:** The Licensing Library will be used to verify that users have a legitimate purchase record for any paid features (Pro version or subscriptions).

### 6. App Architecture
The following diagram illustrates the intended offline-first architecture for the VerseSync application.


## License

VerseSync is free software under the **GNU General Public License, version 3 only**. See [LICENSE](LICENSE). Third-party components and their licenses are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Copyright (C) 2026 Sriram-Codes-SW
