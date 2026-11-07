# Third-Party Notices

VerseSync is licensed under the GNU General Public License, version 3 only (see `LICENSE`). It includes or downloads the components below, which keep their own licenses.

| Component | Use | License |
|---|---|---|
| [TarsosDSP](https://github.com/JorenSix/TarsosDSP) (`app/libs/TarsosDSP-Android-latest.jar`, version 2.4, built 2016-12-08) | Pitch detection and audio capture | GPL-3.0 |
| [Vosk](https://github.com/alphacep/vosk-api) (`com.alphacephei:vosk-android`) | Offline speech recognition | Apache-2.0 |
| [JNA](https://github.com/java-native-access/jna) (pulled in by Vosk) | Native library access | LGPL-2.1-or-later or Apache-2.0 |
| [Gson](https://github.com/google/gson) | JSON serialization | Apache-2.0 |
| AndroidX libraries (Compose, Room, DataStore, Biometric, Navigation, Lifecycle, Glance) | App framework | Apache-2.0 |
| Google Play Billing and Play Services Location | Purchases and region detection | Google's own terms |

## Speech models

Vosk language models are not part of this repository. The app downloads them from `https://alphacephei.com/vosk/models/` when the user asks. Each model has its own license, stated on that page, so check it before redistributing a model.

## Why GPL-3.0

TarsosDSP is released under GPL-3.0, and the app links it directly. Distributing the app therefore requires making its complete source available under a compatible license. The Apache-2.0 components above are compatible with GPL-3.0. See ADR-13 in `docs/03-design.md`.
