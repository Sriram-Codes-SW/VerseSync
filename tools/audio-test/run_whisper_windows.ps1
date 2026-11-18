# Simulates live Whisper decoding of an evaluation corpus on the PC, with the settings fixed in docs/09 section 17.
# Usage: run_whisper_windows.ps1 -Corpus <dir> -Cli <whisper-cli.exe> -Model <ggml model> -Out <empty dir> [-WindowSec 10] [-HopSec 5] [-Threads 4]
#
# For each recording, a window ends every HopSec seconds (at HopSec, 2*HopSec, ...) and covers the last WindowSec seconds
# (less at the start), as the app would see them with one decode at a time and the newest window taken. A window whose
# loudness (RMS of samples from -1 to 1) is below 0.005 is not decoded. Other windows are decoded with greedy decoding,
# no fallback, non-speech tokens suppressed, no prompt, no carried context, the recording's language.
# Output: <Out>/<id>/w<end ms>.json (whisper-cli -oj) and <Out>/windows.csv (id,end_ms,start_ms,silent,wall_seconds).
# The recordings are WAV (16 kHz mono 16-bit), or any file whisper-cli can read when the query gives its length as "seconds"
# (then the loudness gate cannot be applied and the window is always decoded). Nothing here depends on the lyrics.
# Known difference from the app: the command-line program cannot turn timestamp tokens off in the decoder; the app would.
param(
  [Parameter(Mandatory = $true)][string]$Corpus,
  [Parameter(Mandatory = $true)][string]$Cli,
  [Parameter(Mandatory = $true)][string]$Model,
  [Parameter(Mandatory = $true)][string]$Out,
  [int]$WindowSec = 10,
  [int]$HopSec = 5,
  [int]$Threads = 4
)
$ErrorActionPreference = 'Stop'
$silentRms = 0.005
$language = @{ 'cn' = 'zh'; 'en-us' = 'en'; 'vn' = 'vi' }
New-Item -ItemType Directory -Force -Path $Out | Out-Null
$queries = Get-Content -Raw -Encoding UTF8 (Join-Path $Corpus 'queries.json') | ConvertFrom-Json
$rows = New-Object System.Collections.Generic.List[string]
$rows.Add('id,end_ms,start_ms,silent,wall_seconds')

Add-Type -TypeDefinition @'
public static class WavRms {
  // RMS of 16-bit samples (scaled to -1..1) from a 44-byte-header WAV held in memory.
  public static double Of(byte[] bytes, long fromSample, long count) {
    if (count <= 0) return 0.0;
    double sum = 0.0;
    for (long i = 0; i < count; i++) {
      long off = 44 + 2 * (fromSample + i);
      if (off + 1 >= bytes.Length) break;
      double v = (short)(bytes[off] | (bytes[off + 1] << 8)) / 32768.0;
      sum += v * v;
    }
    return System.Math.Sqrt(sum / count);
  }
}
'@
function Get-Rms([byte[]]$bytes, [long]$fromSample, [long]$count) { [WavRms]::Of($bytes, $fromSample, $count) }

foreach ($q in $queries) {
  if ($null -eq $q.file) { continue }
  $path = Join-Path $Corpus $q.file
  $code = if ($language.ContainsKey($q.language)) { $language[$q.language] } else { $q.language }
  $isWav = $path.ToLowerInvariant().EndsWith('.wav')
  if ($isWav) { $bytes = [IO.File]::ReadAllBytes($path); $seconds = ($bytes.Length - 44) / 32000.0 } else { $bytes = $null; $seconds = [double]$q.seconds }
  $dir = Join-Path $Out $q.id
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
  for ($end = $HopSec; $end -le $seconds + 1e-6; $end += $HopSec) {
    $start = [math]::Max(0, $end - $WindowSec)
    $silent = 0
    if ($isWav) {
      $rms = Get-Rms $bytes ([long]($start * 16000)) ([long](($end - $start) * 16000))
      if ($rms -lt $silentRms) { $silent = 1 }
    }
    $wall = 0
    if ($silent -eq 0) {
      $watch = [System.Diagnostics.Stopwatch]::StartNew()
      $ErrorActionPreference = 'Continue'
      & $Cli -m $Model -f $path -l $code -ot ([int]($start * 1000)) -d ([int](($end - $start) * 1000)) -mc 0 -bs 1 -bo 1 -nf -sns -t $Threads -oj -of (Join-Path $dir ('w{0}' -f [int]($end * 1000))) -np 2>&1 | Out-Null
      $ErrorActionPreference = 'Stop'
      $watch.Stop()
      $wall = [math]::Round($watch.Elapsed.TotalSeconds, 2)
    }
    $rows.Add(('{0},{1},{2},{3},{4}' -f $q.id, [int]($end * 1000), [int]($start * 1000), $silent, $wall))
  }
  Write-Output "done $($q.id) ($([math]::Round($seconds,1)) s)"
}
[System.IO.File]::WriteAllLines((Join-Path $Out 'windows.csv'), $rows)
