| Field | Value |
|---|---|
| Document | VerseSync Voice Shortcut Threat Model |
| Version | 0.2 |
| Date | 2026-11-17 |
| Author | Claude Code |
| Status | Active |

# Voice Shortcut Threat Model

Issue [#48](https://github.com/Sriram-Codes-SW/VerseSync/issues/48). The voice shortcut was called the vocal lock before this document, and the internal class names (`VocalLockScreen`, `VocalLockPolicy`, `LockType.VOCAL`), the older ADR titles and the older test names keep that name. The stored setting is unchanged (ADR-12). Only the words shown to a person changed, and they all come from `LockCopy`, which is tested (TC-U-47).

## 1. What it is

A way to open the app by saying or singing a few lines of a song that has been synced into the library. It is a shortcut with the device lock still in place. It checks the words that were heard (and, in the current rule of ADR-10, the pitch), never who is speaking.

## 2. What it protects

The song library and lyrics in the app, from someone who opens the app and does not know the words of any synced song.

## 3. What it does not stop

- **Someone who hears or records the verse,** or plays a recording of it back.
- **Anyone holding the phone who can find the lyrics.** The screen names the song and its artist, so it tells a person what to say.
- **A television, radio or speaker playing the song.** A trial with music played into the microphone, with two songs and one run each on an unstable test setup, produced false candidates. It is a failure that was seen, and not a rate (ADR-03 amendment for issue #44, docs/03-design.md).
- **Children in the household,** and anyone else who knows the song.
- **A song with few different words, or a chorus shared by two songs.** The current rule lets a song whose lyrics have fewer than three different words open on all of them.
- **Anything that needs a speaker's identity.** The shortcut does not know who is speaking.

## 4. The device lock

The fallback is the device lock (the system PIN, pattern, password or biometrics), unchanged by this document (ADR-10). It is the only actual protection. The voice shortcut adds convenience and does not replace it. The device prompt opens at once when "Use device lock" is tapped, and after two failed attempts, where an attempt is one stretch of listening that ends, by a tap or after 10 seconds, without opening (ADR-10, issue #60). The microphone stops when listening ends or the screen is left. After either, the shortcut is not offered again until the app restarts.

## 5. What has been measured, and what has not

Measured, on read speech and not singing, by replaying recordings through the app's own matching rules (docs/09):

- With the rule as it stands, on 707 recordings of songs that are not in the library, the words rule of the lock decided on some song for 32 percent (29 to 35) of them, with the strongest other song taken from every song (the app as built takes it among candidates only, which is laxer, so the app itself was not measured exactly). "Some song" means any of 80 songs; the lock opens for one target.
- The rate per pair of recording and target song was measured for earlier corpora and the rules of that time: 57 of 32,344 (0.2 percent) and 83 of 93,685 (0.1 percent).
- A stricter rule proposed in ADR-18 decided on none of those 707 recordings, but it is only proposed and it is not in the app.

**Not measured:** the false-accept rate on a negative set of television, music, other voices and silence; the live app on a phone; singing; children's voices; languages other than English, Hindi, Spanish, German and French. The read-speech figures measure the words rule on recordings and not the app. There is no number for the false-accept rate of the voice shortcut as people will meet it, and none is claimed anywhere in the app.

## 6. What is not claimed

- That it identifies a person, or that it is secure. Claiming that would need a speaker model, enrollment across sessions, liveness checks and measured error rates across many speakers, including children, and even then it would not be the strongest class of Android biometric.
- That it keeps out anyone who has heard the song.
- Any rate. The wording in the app is "Anyone who knows the words can open it."

## 7. Open

- The false-accept measurement on the negative set, which needs recordings that do not exist yet (issue #48 stays open for it, and #43 for the recordings).
- Whether the stricter rule of ADR-18 ever ships.
- Rotating the phone locks the app again and picks another song to say ([#63](https://github.com/Sriram-Codes-SW/VerseSync/issues/63)); the microphone and recognizer outlive the view model ([#62](https://github.com/Sriram-Codes-SW/VerseSync/issues/62)); the screen loads no speech model, so on a cold start only the pitch path can open it ([#64](https://github.com/Sriram-Codes-SW/VerseSync/issues/64)).
- "Strict Biometric", the name of another lock option in settings, makes a claim of its own and should be checked separately.
