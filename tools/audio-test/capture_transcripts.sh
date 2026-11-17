#!/bin/bash
# Usage: capture_transcripts.sh <corpus dir> [language codes...]
# Captures what the speech recognizer hears for the recordings of an evaluation corpus, on the emulator
# or a phone, with no microphone: each recording is fed straight into the real Vosk recognizer.
# The transcripts are written to <corpus dir>/transcripts/, ready for the JVM replay
# (docs/09-evaluation-harness.md). The app and its test APK must already be installed.
# Speech models come from tools/audio-test/models/<code> (downloaded by run_lang.sh).
export MSYS_NO_PATHCONV=1
HERE="$(cd "$(dirname "$0")" && pwd)"
ADB=${ADB:-adb}
APP=dev.sriramcodessw.versesync
TEST=$APP.test
CORPUS=$(cd "$1" && pwd); shift
NAME=$(basename "$CORPUS")
win() { cygpath -w "$1"; }

if [ $# -gt 0 ]; then CODES="$*"; else
  CODES=$(grep -o '"language": *"[^"]*"' "$CORPUS/queries.json" | sed -E 's/.*: *"([^"]*)"/\1/' | sort -u)
fi

# The corpus goes to the app's files once.
$ADB shell "rm -rf /data/local/tmp/eval-$NAME"
$ADB push "$(win "$CORPUS")" /data/local/tmp/eval-$NAME >/dev/null 2>&1
$ADB shell "run-as $APP rm -rf files/eval/$NAME"
$ADB shell "run-as $APP mkdir -p files/eval"
$ADB shell "run-as $APP cp -r /data/local/tmp/eval-$NAME files/eval/$NAME"
$ADB shell "rm -rf /data/local/tmp/eval-$NAME"
$ADB shell "run-as $APP mkdir -p files/models"

for CODE in $CODES; do
  ROOT=$(ls -d "$HERE/models/$CODE/u"/*/ 2>/dev/null | head -1)
  if [ -z "$ROOT" ]; then echo "$CODE | no model in tools/audio-test/models, skipped"; continue; fi
  $ADB shell "rm -rf /data/local/tmp/m-$CODE" >/dev/null
  $ADB push "$(win "$ROOT")" /data/local/tmp/m-$CODE >/dev/null 2>&1
  $ADB shell "run-as $APP rm -rf files/models/model-$CODE"
  $ADB shell "run-as $APP cp -r /data/local/tmp/m-$CODE files/models/model-$CODE"
  $ADB shell "rm -rf /data/local/tmp/m-$CODE"
  OUT=$($ADB shell am instrument -w -e class dev.sriramcodessw.versesync.eval.TranscriptCaptureInstrumentedTest -e corpus "$NAME" -e code "$CODE" $TEST/androidx.test.runner.AndroidJUnitRunner 2>&1)
  if echo "$OUT" | grep -q "^OK ("; then STATE=ok; else STATE=FAILED; echo "$OUT" | tail -15; fi
  echo "$CODE | $STATE | $($ADB logcat -d -s EVALCAP:I | grep "CAPTURED code=$CODE" | tail -1 | cut -c1-200)"
  $ADB shell "run-as $APP rm -rf files/models/model-$CODE"
done

# Bring the transcripts back.
mkdir -p "$CORPUS/transcripts"
for f in $($ADB shell "run-as $APP ls files/eval/$NAME/transcripts" | tr -d '\r'); do
  $ADB exec-out "run-as $APP cat files/eval/$NAME/transcripts/$f" > "$CORPUS/transcripts/$f"
done
echo "transcripts: $(ls "$CORPUS/transcripts" | wc -l) files in $CORPUS/transcripts"
$ADB shell "run-as $APP rm -rf files/eval/$NAME"
