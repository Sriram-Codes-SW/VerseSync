# Real-audio language test

Checks, for each supported language, that real human speech played into the emulator's
microphone is recognized as the right song by the real engine and the real Vosk model.

It is Windows only, because it plays audio through a virtual audio cable.

## One-time setup

1. Install a virtual audio cable and make it the Windows **default recording and playback
   device** for every role. The scripts send audio to the output called `Line 1`.
2. Start the emulator with host audio allowed, then switch on **Extended controls, Microphone,
   Virtual microphone uses host audio input**. Restart the emulator after changing the Windows
   default device, because it opens the device when it starts.

   ```
   emulator -avd <name> -allow-host-audio
   ```
3. Install the debug app and the test APK, and grant the microphone to the app:

   ```
   ./gradlew assembleDebug assembleDebugAndroidTest
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
   adb shell pm grant dev.sriramcodessw.versesync android.permission.RECORD_AUDIO
   ```
4. Run the scripts from Git Bash, with `adb` on the path or in the `ADB` variable.

## Run

```
bash tools/audio-test/fetch_fleurs.sh          # one recording and transcript per language
bash tools/audio-test/run_lang.sh en-us        # one language
bash tools/audio-test/runall.sh hi ko ru       # several, results go to results.txt
```

`run_lang.sh` downloads that language's Vosk model, installs it in the app, starts
`LanguageRecognitionInstrumentedTest`, plays the recording, and prints `passed=1` when the song
was recognized. `MicrophoneLevelInstrumentedTest` reports the microphone's peak level per second,
which is the quickest way to check that audio reaches the emulator.

## Data and limits

- Recordings and transcripts come from Google FLEURS (CC BY 4.0). Sanskrit is not in FLEURS.
- Each language uses one sentence, so a failure can be a model miss, not a bug. Rerun with a
  different sentence before concluding.
- Languages whose model link is empty in `LanguageViewModel` have no Vosk model and are skipped.
- Downloaded models and recordings are kept next to the scripts and are ignored by git.
