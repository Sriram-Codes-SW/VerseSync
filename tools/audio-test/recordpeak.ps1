param([int]$Seconds = 6, [string]$Device = "Line 1")

Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
using System.Threading;

public static class WaveRecorder {
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
    public struct WAVEINCAPS {
        public ushort wMid; public ushort wPid; public uint vDriverVersion;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string szPname;
        public uint dwFormats; public ushort wChannels; public ushort wReserved1;
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
    [DllImport("winmm.dll")] static extern uint waveInGetNumDevs();
    [DllImport("winmm.dll", CharSet = CharSet.Auto)] static extern uint waveInGetDevCaps(IntPtr id, ref WAVEINCAPS caps, uint size);
    [DllImport("winmm.dll")] static extern uint waveInOpen(out IntPtr h, uint dev, ref WAVEFORMATEX fmt, IntPtr cb, IntPtr inst, uint flags);
    [DllImport("winmm.dll")] static extern uint waveInPrepareHeader(IntPtr h, ref WAVEHDR hdr, uint size);
    [DllImport("winmm.dll")] static extern uint waveInAddBuffer(IntPtr h, ref WAVEHDR hdr, uint size);
    [DllImport("winmm.dll")] static extern uint waveInStart(IntPtr h);
    [DllImport("winmm.dll")] static extern uint waveInStop(IntPtr h);
    [DllImport("winmm.dll")] static extern uint waveInUnprepareHeader(IntPtr h, ref WAVEHDR hdr, uint size);
    [DllImport("winmm.dll")] static extern uint waveInClose(IntPtr h);

    public static string Record(string deviceContains, int seconds) {
        uint n = waveInGetNumDevs();
        int dev = -1; string found = "";
        for (uint i = 0; i < n; i++) {
            WAVEINCAPS c = new WAVEINCAPS();
            waveInGetDevCaps((IntPtr)i, ref c, (uint)Marshal.SizeOf(c));
            if (c.szPname.IndexOf(deviceContains, StringComparison.OrdinalIgnoreCase) >= 0) { dev = (int)i; found = c.szPname; break; }
        }
        if (dev < 0) return "input device not found";
        WAVEFORMATEX f = new WAVEFORMATEX();
        f.wFormatTag = 1; f.nChannels = 1; f.nSamplesPerSec = 16000; f.wBitsPerSample = 16; f.nBlockAlign = 2; f.nAvgBytesPerSec = 32000;
        IntPtr h; uint r = waveInOpen(out h, (uint)dev, ref f, IntPtr.Zero, IntPtr.Zero, 0);
        if (r != 0) return "waveInOpen failed " + r;
        short[] buf = new short[16000 * seconds];
        GCHandle gh = GCHandle.Alloc(buf, GCHandleType.Pinned);
        WAVEHDR hdr = new WAVEHDR(); hdr.lpData = gh.AddrOfPinnedObject(); hdr.dwBufferLength = (uint)(buf.Length * 2);
        uint hs = (uint)Marshal.SizeOf(hdr);
        waveInPrepareHeader(h, ref hdr, hs); waveInAddBuffer(h, ref hdr, hs); waveInStart(h);
        Thread.Sleep(seconds * 1000 + 500);
        waveInStop(h); waveInUnprepareHeader(h, ref hdr, hs); waveInClose(h);
        int peak = 0; for (int i = 0; i < buf.Length; i++) peak = Math.Max(peak, Math.Abs((int)buf[i]));
        gh.Free();
        return "device='" + found + "' peak=" + peak;
    }
}
"@

[WaveRecorder]::Record($Device, $Seconds)
