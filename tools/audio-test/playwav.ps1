param(
    [string]$Wav,
    [string]$Device = "Line 1",
    [switch]$List
)

Add-Type -TypeDefinition @"
using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Threading;

public static class WavePlayer {
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
    public struct WAVEOUTCAPS {
        public ushort wMid; public ushort wPid; public uint vDriverVersion;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string szPname;
        public uint dwFormats; public ushort wChannels; public ushort wReserved1; public uint dwSupport;
    }
    [StructLayout(LayoutKind.Sequential)]
    public struct WAVEFORMATEX {
        public ushort wFormatTag; public ushort nChannels; public uint nSamplesPerSec;
        public uint nAvgBytesPerSec; public ushort nBlockAlign; public ushort wBitsPerSample; public ushort cbSize;
    }
    [StructLayout(LayoutKind.Sequential)]
    public struct WAVEHDR {
        public IntPtr lpData; public uint dwBufferLength; public uint dwBytesRecorded; public IntPtr dwUser;
        public uint dwFlags; public uint dwLoops; public IntPtr lpNext; public IntPtr reserved;
    }
    [DllImport("winmm.dll")] static extern uint waveOutGetNumDevs();
    [DllImport("winmm.dll", CharSet = CharSet.Auto)] static extern uint waveOutGetDevCaps(IntPtr id, ref WAVEOUTCAPS caps, uint size);
    [DllImport("winmm.dll")] static extern uint waveOutOpen(out IntPtr h, uint dev, ref WAVEFORMATEX fmt, IntPtr cb, IntPtr inst, uint flags);
    [DllImport("winmm.dll")] static extern uint waveOutPrepareHeader(IntPtr h, ref WAVEHDR hdr, uint size);
    [DllImport("winmm.dll")] static extern uint waveOutWrite(IntPtr h, ref WAVEHDR hdr, uint size);
    [DllImport("winmm.dll")] static extern uint waveOutUnprepareHeader(IntPtr h, ref WAVEHDR hdr, uint size);
    [DllImport("winmm.dll")] static extern uint waveOutClose(IntPtr h);

    public static string[] Devices() {
        uint n = waveOutGetNumDevs();
        string[] names = new string[n];
        for (uint i = 0; i < n; i++) {
            WAVEOUTCAPS c = new WAVEOUTCAPS();
            waveOutGetDevCaps((IntPtr)i, ref c, (uint)Marshal.SizeOf(c));
            names[i] = c.szPname;
        }
        return names;
    }

    // Reads a 16-bit PCM WAV and returns 48 kHz stereo samples with one second of silence on each side.
    static short[] Load(string path) {
        byte[] b = File.ReadAllBytes(path);
        int pos = 12, channels = 1, rate = 16000, bits = 16, dataOff = 0, dataLen = 0;
        while (pos + 8 <= b.Length) {
            string id = System.Text.Encoding.ASCII.GetString(b, pos, 4);
            int len = BitConverter.ToInt32(b, pos + 4);
            if (id == "fmt ") {
                channels = BitConverter.ToInt16(b, pos + 10);
                rate = BitConverter.ToInt32(b, pos + 12);
                bits = BitConverter.ToInt16(b, pos + 22);
            } else if (id == "data") {
                dataOff = pos + 8; dataLen = Math.Min(len, b.Length - dataOff); break;
            }
            pos += 8 + len + (len & 1);
        }
        bool isFloat = (bits == 32);
        if (bits != 16 && bits != 32) throw new Exception("Only 16-bit PCM or 32-bit float WAV is supported");
        int bytesPer = bits / 8;
        int frames = dataLen / (bytesPer * channels);
        double[] mono = new double[frames];
        for (int i = 0; i < frames; i++) {
            double s = 0;
            for (int c = 0; c < channels; c++) {
                int off = dataOff + (i * channels + c) * bytesPer;
                s += isFloat ? BitConverter.ToSingle(b, off) * 32767.0 : BitConverter.ToInt16(b, off);
            }
            mono[i] = s / channels;
        }
        int outRate = 48000;
        int pad = outRate;
        int outFrames = (int)((long)frames * outRate / rate);
        short[] o = new short[(outFrames + 2 * pad) * 2];
        for (int i = 0; i < outFrames; i++) {
            double src = (double)i * rate / outRate;
            int i0 = (int)src; int i1 = Math.Min(i0 + 1, frames - 1);
            double v = mono[i0] + (mono[i1] - mono[i0]) * (src - i0);
            short sv = (short)Math.Max(-32768, Math.Min(32767, v));
            o[(pad + i) * 2] = sv; o[(pad + i) * 2 + 1] = sv;
        }
        return o;
    }

    public static void Play(string path, string deviceContains) {
        string[] names = Devices();
        int dev = -1;
        for (int i = 0; i < names.Length; i++) if (names[i].IndexOf(deviceContains, StringComparison.OrdinalIgnoreCase) >= 0) { dev = i; break; }
        if (dev < 0) throw new Exception("Output device not found: " + deviceContains);
        short[] samples = Load(path);
        WAVEFORMATEX f = new WAVEFORMATEX();
        f.wFormatTag = 1; f.nChannels = 2; f.nSamplesPerSec = 48000; f.wBitsPerSample = 16;
        f.nBlockAlign = 4; f.nAvgBytesPerSec = 48000 * 4; f.cbSize = 0;
        IntPtr h;
        uint r = waveOutOpen(out h, (uint)dev, ref f, IntPtr.Zero, IntPtr.Zero, 0);
        if (r != 0) throw new Exception("waveOutOpen failed: " + r);
        GCHandle gh = GCHandle.Alloc(samples, GCHandleType.Pinned);
        WAVEHDR hdr = new WAVEHDR();
        hdr.lpData = gh.AddrOfPinnedObject(); hdr.dwBufferLength = (uint)(samples.Length * 2);
        uint hs = (uint)Marshal.SizeOf(hdr);
        waveOutPrepareHeader(h, ref hdr, hs);
        waveOutWrite(h, ref hdr, hs);
        while ((hdr.dwFlags & 1) == 0) Thread.Sleep(50); // WHDR_DONE
        waveOutUnprepareHeader(h, ref hdr, hs);
        waveOutClose(h);
        gh.Free();
    }
}
"@

if ($List) { [WavePlayer]::Devices() | ForEach-Object { $i = 0 } { "{0}: {1}" -f $i++, $_ }; return }
[WavePlayer]::Play($Wav, $Device)
"played $Wav to '$Device'"
