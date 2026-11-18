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
  [int]$Threads = 8
)
$ErrorActionPreference = 'Stop'
# The app's language code -> Whisper's. Only Chinese differs.
$language = @{ 'cn' = 'zh' }
New-Item -ItemType Directory -Force -Path $Out | Out-Null
$queries = Get-Content -Raw -Encoding UTF8 (Join-Path $Corpus 'queries.json') | ConvertFrom-Json
$timing = @('id,language,audio_seconds,wall_seconds,threads')
foreach ($q in $queries) {
  $wav = Join-Path $Corpus $q.file
  $code = if ($language.ContainsKey($q.language)) { $language[$q.language] } else { $q.language }
  $audio = [math]::Round(((Get-Item -LiteralPath $wav).Length - 44) / 32000.0, 1)
  $watch = [System.Diagnostics.Stopwatch]::StartNew()
  & $Cli -m $Model -f $wav -l $code -mc 0 -oj -of (Join-Path $Out $q.id) -t $Threads -np 2>&1 | Out-Null
  $watch.Stop()
  if (-not (Test-Path -LiteralPath (Join-Path $Out "$($q.id).json"))) { Write-Output "FAILED $($q.id)"; continue }
  $row = '{0},{1},{2},{3},{4}' -f $q.id, $code, $audio, [math]::Round($watch.Elapsed.TotalSeconds, 1), $Threads
  $timing += $row
  Write-Output $row
}
[System.IO.File]::WriteAllLines((Join-Path $Out 'timing.csv'), $timing)
