# Builds the SEED evaluation corpus from the FLEURS clips that fetch_fleurs.sh downloaded.
#
#   powershell -File make_seed_corpus.ps1 -Out <dir>
#
# Every third language (in sorted order) is held out of the library, and its clip becomes a recording of a
# song that is not in the library. The other clips are library songs, and each is also its own query.
#
# This is a seed, not an evaluation set: one speaker per language, spoken not sung, and a library clip is
# its own pitch reference, so the pitch numbers for songs in the library are meaningless. Only the speech
# numbers and the false-accept numbers mean something. corpus.json says so, and the report repeats it.
param(
    [Parameter(Mandatory = $true)][string]$Out,
    [string]$Fleurs,
    [string[]]$Languages
)
$ErrorActionPreference = 'Stop'
if (-not $Fleurs) { $Fleurs = Join-Path $PSScriptRoot 'fleurs' }
$utf8 = New-Object System.Text.UTF8Encoding($false)

# Languages that have both a downloaded clip and an app speech model.
$models = Join-Path $PSScriptRoot 'models'
$codes = Get-ChildItem $Fleurs -Directory | Where-Object { $_.Name -ne 'hum' -and (Test-Path (Join-Path $_.FullName 'sample.wav')) -and (Test-Path (Join-Path $models $_.Name)) } | ForEach-Object { $_.Name } | Sort-Object
if ($Languages) { $codes = $codes | Where-Object { $Languages -contains $_ } }

New-Item -ItemType Directory -Force (Join-Path $Out 'recordings') | Out-Null
$library = @()
$queries = @()
$i = 0
foreach ($code in $codes) {
    $i++
    $heldOut = ($i % 3 -eq 0)
    $lyrics = (Get-Content (Join-Path $Fleurs "$code/lyrics.txt") -Raw -Encoding UTF8).Trim()
    $name = "recordings/$code.wav"
    Copy-Item (Join-Path $Fleurs "$code/sample.wav") (Join-Path $Out $name) -Force
    if (-not $heldOut) {
        $library += [ordered]@{ id = $code; title = "FLEURS $code"; language = $code; lyrics = $lyrics; reference = $name }
    }
    $queries += [ordered]@{
        id = "q-$code"; file = $name; song = $(if ($heldOut) { $null } else { $code })
        mode = 'speak'; language = $code; condition = 'studio'; device = 'fleurs'; speaker = "fleurs-$code"; speakerGroup = 'adult'
    }
}
[System.IO.File]::WriteAllText((Join-Path $Out 'library.json'), (ConvertTo-Json -InputObject @($library) -Depth 5), $utf8)
[System.IO.File]::WriteAllText((Join-Path $Out 'queries.json'), (ConvertTo-Json -InputObject @($queries) -Depth 5), $utf8)
$meta = [ordered]@{
    name    = 'seed (Google FLEURS, CC BY 4.0)'
    caution = 'SEED CORPUS, NOT AN EVALUATION SET: one speaker per language, spoken not sung, studio recordings. A library clip is its own pitch reference, so pitch numbers for songs in the library mean nothing here; read the speech numbers and the false-accept numbers only.'
}
[System.IO.File]::WriteAllText((Join-Path $Out 'corpus.json'), (ConvertTo-Json -InputObject $meta), $utf8)
Write-Host "$($codes.Count) languages: $(@($library).Count) in the library, $(@($queries).Count - @($library).Count) held out. Written to $Out"
