#!/bin/bash
# Usage: run_e2e.sh [clip A] [clip B]    (language codes of recordings in fleurs/, default en-us and hi)
#
# End to end check of lyric sync, recognition and the vocal lock with real audio. It installs the
# speech model for clip A's language, starts RealAudioEndToEndInstrumentedTest on the emulator and,
# each time the app logs "READY step=N", plays clip A (steps 1, 3 and 4) or clip B (steps 2 and 5)
# into the microphone through the virtual cable. See the test for what each step checks.
#
# Needs the one-time setup in README.md, the debug app and the test APK installed, and the
# recordings fetched with fetch_fleurs.sh.
export MSYS_NO_PATHCONV=1
HERE="$(cd "$(dirname "$0")" && pwd)"
ADB=${ADB:-adb}
APP=dev.sriramcodessw.versesync
TEST=$APP.test
A=${1:-en-us}
B=${2:-hi}
STEPS=5
CLIPS=("" "$A" "$B" "$A" "$A" "$B")
# A third argument, "silent", plays nothing in step 5, to see whether background noise alone opens the lock.
[ "$3" = "silent" ] && CLIPS[5]=""
# STRICT=1 also fails the run when the vocal lock opens for the wrong audio (issue 33).
STRICT_ARGS=""
[ "$STRICT" = "1" ] && STRICT_ARGS="-e strict 1"
URLS=$HERE/../../app/src/main/java/dev/sriramcodessw/versesync/data/LanguageViewModel.kt
win() { cygpath -w "$1"; }

for c in "$A" "$B"; do
  [ -f "$HERE/fleurs/$c/sample.wav" ] || { echo "No recording for $c. Run: bash tools/audio-test/fetch_fleurs.sh $c"; exit 2; }
done
URL=$(grep -o "\"$A\", \"https://[^\"]*\"" "$URLS" | head -1 | sed -E 's/.*"(https[^"]*)"/\1/')
[ -z "$URL" ] && { echo "$A has no speech model, so it cannot be clip A"; exit 2; }

# Speech model for clip A, and the sentences of both clips.
mkdir -p "$HERE/models"
if [ ! -d "$HERE/models/$A/u" ] || [ -z "$(find "$HERE/models/$A/u" -type f | head -1)" ]; then
  rm -rf "$HERE/models/$A"
  env -u MSYS_NO_PATHCONV curl -sL -o "$HERE/models/$A.zip" "$URL" && mkdir -p "$HERE/models/$A/u" && unzip -q -o "$HERE/models/$A.zip" -d "$HERE/models/$A/u"
  rm -f "$HERE/models/$A.zip"
fi
ROOT=$(ls -d "$HERE/models/$A/u"/*/ | head -1)
$ADB shell "rm -rf /data/local/tmp/m-$A" >/dev/null
$ADB push "$(win "$ROOT")" /data/local/tmp/m-$A >/dev/null 2>&1
$ADB shell "run-as $APP rm -rf files/models/model-$A"
$ADB shell "run-as $APP mkdir -p files/models files/lang"
$ADB shell "run-as $APP cp -r /data/local/tmp/m-$A files/models/model-$A"
$ADB shell "rm -rf /data/local/tmp/m-$A"
for c in "$A" "$B"; do
  $ADB push "$(win "$HERE/fleurs/$c/lyrics.txt")" /data/local/tmp/lyrics-$c.txt >/dev/null 2>&1
  $ADB shell "run-as $APP cp /data/local/tmp/lyrics-$c.txt files/lang/$c.txt"
  $ADB shell "rm -f /data/local/tmp/lyrics-$c.txt"
done

$ADB shell pm grant $APP android.permission.RECORD_AUDIO
$ADB emu avd hostmicon >/dev/null 2>&1

# HUM=1 plays a faint steady 470 Hz tone (about -73 dB) for the whole run, like the hum the
# emulator's microphone sometimes has (issue 33). The player must not raise its level.
HUM_PID=""
if [ "$HUM" = "1" ]; then
  if [ ! -f "$HERE/fleurs/hum/sample.wav" ]; then
    mkdir -p "$HERE/fleurs/hum"
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(win "$HERE/make_hum.ps1")" -Out "$(win "$HERE/fleurs/hum/sample.wav")" >/dev/null 2>&1
  fi
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(win "$HERE/playwav.ps1")" \
    -Wav "$(win "$HERE/fleurs/hum/sample.wav")" -NoNormalize >/dev/null 2>&1 &
  HUM_PID=$!
  echo "playing a faint hum during the whole run"
  sleep 3
fi
$ADB logcat -c
$ADB shell am instrument -w -e class $APP.engine.RealAudioEndToEndInstrumentedTest -e e2e 1 -e a "$A" -e b "$B" $STRICT_ARGS \
  $TEST/androidx.test.runner.AndroidJUnitRunner > "$HERE/out-e2e.txt" 2>&1 &
INSTR=$!

for step in $(seq 1 $STEPS); do
  READY=0
  for i in $(seq 1 300); do
    if $ADB logcat -d -s E2E:I | grep -q "READY step=$step "; then READY=1; break; fi
    kill -0 $INSTR 2>/dev/null || break
    sleep 1
  done
  [ $READY = 1 ] || { echo "step $step never became ready"; break; }
  CLIP=${CLIPS[$step]}
  [ -z "$CLIP" ] && { echo "step $step: playing nothing"; continue; }
  echo "step $step: playing $CLIP"
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(win "$HERE/playwav.ps1")" \
    -Wav "$(win "$HERE/fleurs/$CLIP/sample.wav")" >/dev/null 2>&1
done

wait $INSTR 2>/dev/null
if [ -n "$HUM_PID" ]; then
  kill $HUM_PID 2>/dev/null
  powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { \$_.CommandLine -like '*playwav.ps1*hum*' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }" >/dev/null 2>&1
fi
$ADB logcat -d -s E2E:I AndroidRuntime:E > "$HERE/log-e2e.txt" 2>&1
grep -E "step=|RESULT" "$HERE/log-e2e.txt" | sed 's/^.*E2E *: //'
$ADB shell "run-as $APP rm -rf files/models/model-$A"
if grep -q "^OK (1 test)" "$HERE/out-e2e.txt"; then echo "PASSED"; else echo "FAILED"; grep -m3 -E "AssertionError|Exception|FAILURES" "$HERE/out-e2e.txt"; exit 1; fi
