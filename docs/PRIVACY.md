# VerseSync Privacy Policy

**Status: draft for the owner's review.** It describes what the app does today, checked against the source code. Publish it at a stable public address (for example GitHub Pages) before the first Play Store release, and have it reviewed by someone qualified if the app will be offered where privacy law applies to you.

Last updated: 2026-11-08

## Summary

VerseSync has no accounts, no advertising and no analytics. Your songs stay on your device. The app does not upload your recordings, lyrics or songs to any server run by the developer.

Three features use the network or other companies' services. They are listed under "What leaves your device".

## What the app stores on your device

- **Songs and folders:** title, artist, lyrics and the folders you create.
- **Pitch fingerprints:** a list of numbers describing the pitch of a song as you sang or played it while syncing. It is not audio, and the recording cannot be rebuilt from it.
- **Settings:** your languages, whether the app lock is on and its type, and the number of free recognition trials used.
- **Speech models:** the language files you choose to download.

## Microphone

The app listens only while you use lyric sync, recognition or the vocal lock. The sound is analysed on the device as it arrives. It is not saved and it is not sent anywhere.

## What leaves your device

1. **Speech model downloads.** When you pick a language, the app asks the Android download service to fetch a speech model from `alphacephei.com`, the site of the Vosk project. That site can see your network address and which model you requested. No other data is sent.
2. **Language suggestion at first start.** If you allow the approximate location permission, the app asks Google's location service for your approximate position and converts it to a country with Android's geocoder, to suggest languages. On most phones the geocoder sends the coordinates to Google. VerseSync keeps only the country code and does not store the position. You can refuse the permission and choose languages yourself.
3. **Purchases.** Pro and tips are handled by Google Play. The app receives whether a purchase is active. It never sees payment details.

## Sharing you start yourself

- **Bluetooth sharing** sends the songs you select to the nearby device you choose, over a direct Bluetooth connection. Received songs are added to the library as new songs.
- **Library export** creates a file, encrypted with a key that stays on the device, and hands it to the app you choose. The file can be read back only on the same install.

## Permissions

| Permission | Why |
|---|---|
| Microphone | Lyric sync, recognition and the vocal lock |
| Bluetooth (scan, connect, advertise) | Sharing songs with a nearby device. The scan permission is declared as not used to find your location. |
| Precise location, Android 10 and older only | Those versions require it for Bluetooth scanning. It is not read. |
| Approximate location | Suggesting languages at first start, as described above |
| Google Play billing | Pro and tips |

The app lock uses Android's biometric prompt or your device PIN. The app never receives your fingerprint or face data.

## Backups

Android Auto Backup may copy your songs and folders to your Google account if you have device backup turned on. Settings, the app lock and downloaded speech models are excluded.

## Children

VerseSync is not directed at children and collects no personal information from anyone.

## Deleting your data

Uninstalling the app deletes everything it stored on the device. To delete a backup copy, remove it from your Google account's device backups.

## Changes and contact

If this policy changes, the date above changes and the previous text stays in the repository history. Questions can be raised as an issue at https://github.com/Sriram-Codes-SW/VerseSync/issues.
