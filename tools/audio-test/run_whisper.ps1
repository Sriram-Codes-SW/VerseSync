# Runs whisper-cli on every recording of an evaluation corpus, with the settings fixed in docs/09 section 16.
# Usage: run_whisper.ps1 -Corpus <corpus dir> -Cli <whisper-cli.exe> -Model <ggml model file> -Out <empty output dir> [-Threads 8]
# It writes <Out>/<query id>.json (whisper-cli -oj) and <Out>/timing.csv (audio seconds, wall-clock seconds, threads).
# No prompt, no voice-activity detection, no suppression flags; the language is the recording's own, from the table below.
# Convert and score the output with the WhisperRun and EvaluationRun tests. Nothing here depends on the lyrics.
param(
  [Parameter(Mandatory = $true)][string]$Corpus,
  [Parameter(Mandatory = $true)][string]$Cli,
  [Parameter(Mandatory = $true)][string]$Model,
  [Parameter(Mandatory = $true)][string]$Out,
  [int]$Threads = 8,
  # Skips a recording that already has output, to run only what failed; timing rows are then appended.
  [switch]$SkipExisting
)
$ErrorActionPreference = 'Stop'
# The app's language code -> Whisper's. Only Chinese and English differ (the app says 'en-us', Whisper has 'en').
$language = @{ 'cn' = 'zh'; 'en-us' = 'en' }
New-Item -ItemType Directory -Force -Path $Out | Out-Null
$queries = Get-Content -Raw -Encoding UTF8 (Join-Path $Corpus 'queries.json') | ConvertFrom-Json
$timingFile = Join-Path $Out 'timing.csv'
$timing = if ($SkipExisting -and (Test-Path -LiteralPath $timingFile)) { @(Get-Content -LiteralPath $timingFile) } else { @('id,language,audio_seconds,wall_seconds,threads') }
foreach ($q in $queries) {
  if ($SkipExisting -and (Test-Path -LiteralPath (Join-Path $Out "$($q.id).json"))) { continue }
  $wav = Join-Path $Corpus $q.file
  $code = if ($language.ContainsKey($q.language)) { $language[$q.language] } else { $q.language }
  $audio = [math]::Round(((Get-Item -LiteralPath $wav).Length - 44) / 32000.0, 1)
  $watch = [System.Diagnostics.Stopwatch]::StartNew()
  # Windows PowerShell 5.1 turns a native program's stderr text into a terminating error under 'Stop', so relax it for the call.
  $ErrorActionPreference = 'Continue'
  & $Cli -m $Model -f $wav -l $code -mc 0 -oj -of (Join-Path $Out $q.id) -t $Threads -np 2>&1 | Out-Null
  $ErrorActionPreference = 'Stop'
  $watch.Stop()
  if (-not (Test-Path -LiteralPath (Join-Path $Out "$($q.id).json"))) { Write-Output "FAILED $($q.id)"; continue }
  $row = '{0},{1},{2},{3},{4}' -f $q.id, $code, $audio, [math]::Round($watch.Elapsed.TotalSeconds, 1), $Threads
  $timing += $row
  Write-Output $row
}
[System.IO.File]::WriteAllLines($timingFile, $timing)
