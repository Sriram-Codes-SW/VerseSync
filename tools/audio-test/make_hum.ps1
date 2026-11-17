param(
    [string]$Out,
    [int]$Seconds = 480,
    [double]$Db = -73,
    [double]$Hz = 470
)

# Writes a WAV of a faint steady tone: 16 kHz, mono, 16-bit.
#
# It stands in for the hum that some microphones have. The emulator's microphone idled at a hum of
# about -73 dB relative to full scale, which the pitch detector reported as a voiced 470 Hz tone
# (issue 33). Play it with `playwav.ps1 -NoNormalize`, which keeps it that quiet.
#
# 470 Hz fits a whole number of cycles in one second at 16 kHz, so one second repeats without a
# break.

$rate = 16000
$peak = [math]::Sqrt(2) * [math]::Pow(10, $Db / 20) * 32767
$one = New-Object byte[] ($rate * 2)
for ($i = 0; $i -lt $rate; $i++) {
    $v = [int][math]::Round($peak * [math]::Sin(2 * [math]::PI * $Hz * $i / $rate))
    $b = [BitConverter]::GetBytes([int16]$v)
    $one[2 * $i] = $b[0]
    $one[2 * $i + 1] = $b[1]
}

$dataLen = $one.Length * $Seconds
$fs = [System.IO.File]::Create($Out)
try {
    $w = New-Object System.IO.BinaryWriter($fs)
    $w.Write([System.Text.Encoding]::ASCII.GetBytes("RIFF"))
    $w.Write([int](36 + $dataLen))
    $w.Write([System.Text.Encoding]::ASCII.GetBytes("WAVEfmt "))
    $w.Write([int]16)
    $w.Write([int16]1)
    $w.Write([int16]1)
    $w.Write([int]$rate)
    $w.Write([int]($rate * 2))
    $w.Write([int16]2)
    $w.Write([int16]16)
    $w.Write([System.Text.Encoding]::ASCII.GetBytes("data"))
    $w.Write([int]$dataLen)
    for ($s = 0; $s -lt $Seconds; $s++) { $w.Write($one) }
    $w.Flush()
} finally {
    $fs.Close()
}
"wrote $Out ($Seconds s, $Db dB, $Hz Hz, peak about $([math]::Round($peak)) of 32767)"
