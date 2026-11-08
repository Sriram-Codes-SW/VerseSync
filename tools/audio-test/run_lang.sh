#!/bin/bash
# Usage: run_lang.sh <language code> [decoy lyrics]
# Installs the language's Vosk model in the app, starts the on-device recognition test, plays the
# FLEURS recording into the emulator microphone through the virtual cable, and prints the result.
export MSYS_NO_PATHCONV=1
HERE="$(cd "$(dirname "$0")" && pwd)"
ADB=${ADB:-adb}
APP=dev.sriramcodessw.versesync
TEST=$APP.test
CODE=$1
DECOY=${2:-"completely unrelated words about something else entirely"}
URLS=$HERE/../../app/src/main/java/dev/sriramcodessw/versesync/data/LanguageViewModel.kt
URL=$(grep -o "\"$CODE\", \"https://[^\"]*\"" "$URLS" | head -1 | sed -E 's/.*"(https[^"]*)"/\1/')
[ -z "$URL" ] && { echo "$CODE: no model url"; exit 2; }
win() { cygpath -w "$1"; }

# 1. Model
mkdir -p "$HERE/models"
if [ ! -d "$HERE/models/$CODE" ]; then
  env -u MSYS_NO_PATHCONV curl -sL -o "$HERE/models/$CODE.zip" "$URL" && mkdir -p "$HERE/models/$CODE/u" && unzip -q -o "$HERE/models/$CODE.zip" -d "$HERE/models/$CODE/u"
  rm -f "$HERE/models/$CODE.zip"
fi
ROOT=$(ls -d "$HERE/models/$CODE/u"/*/ | head -1)
$ADB shell "rm -rf /data/local/tmp/m-$CODE" >/dev/null
$ADB push "$(win "$ROOT")" /data/local/tmp/m-$CODE >/dev/null 2>&1
$ADB shell "run-as $APP rm -rf files/models/model-$CODE"
$ADB shell "run-as $APP mkdir -p files/models"
$ADB shell "run-as $APP cp -r /data/local/tmp/m-$CODE files/models/model-$CODE"
$ADB shell "rm -rf /data/local/tmp/m-$CODE"

# 2. Sentence for the target song
$ADB push "$(win "$HERE/fleurs/$CODE/lyrics.txt")" /data/local/tmp/lyrics-$CODE.txt >/dev/null 2>&1
$ADB shell "run-as $APP mkdir -p files/lang"
$ADB shell "run-as $APP cp /data/local/tmp/lyrics-$CODE.txt files/lang/$CODE.txt"
$ADB shell "rm -f /data/local/tmp/lyrics-$CODE.txt"

# 3. Start the test, wait until it is listening
$ADB logcat -c
$ADB shell am instrument -w -e class dev.sriramcodessw.versesync.engine.LanguageRecognitionInstrumentedTest -e code "$CODE" $TEST/androidx.test.runner.AndroidJUnitRunner > "$HERE/out-$CODE.txt" 2>&1 &
INSTR=$!
READY=0
for i in $(seq 1 120); do
  if $ADB logcat -d -s RECOG:I | grep -q "READY code=$CODE"; then READY=1; break; fi
  if $ADB logcat -d -s RECOG:I | grep -q "ready=false"; then break; fi
  sleep 2
done
if [ $READY = 1 ]; then
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(win "$HERE/playwav.ps1")" -Wav "$(win "$HERE/fleurs/$CODE/sample.wav")" >/dev/null 2>&1
fi
for i in $(seq 1 60); do
  $ADB logcat -d -s RECOG:I | grep -q "RESULT code=$CODE" && break
  kill -0 $INSTR 2>/dev/null || break
  sleep 2
done
wait $INSTR 2>/dev/null
$ADB logcat -d -s RECOG:I VOSK:V VoskVoiceRecognition:V AndroidRuntime:E > "$HERE/log-$CODE.txt" 2>&1
LINE=$($ADB logcat -d -s RECOG:I | grep -E "RESULT code=$CODE|model code=$CODE" | tail -2 | cut -c1-400 | tr '\n' ' ')
OK=$(grep -c "^OK (1 test)" "$HERE/out-$CODE.txt")
echo "$CODE | ready=$READY | passed=$OK | $LINE"
$ADB shell "run-as $APP rm -rf files/models/model-$CODE"
