package dev.sriramcodessw.versesync.engine

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Reports the loudest sample the microphone delivers each second. Skipped unless started with
 * `-e miclevel <seconds>`. Used to check that audio played on the PC reaches the emulator.
 */
@RunWith(AndroidJUnit4::class)
class MicrophoneLevelInstrumentedTest {
    @SuppressLint("MissingPermission")
    @Test
    fun microphone_reportsLevelPerSecond() {
        val seconds = InstrumentationRegistry.getArguments().getString("miclevel")?.toIntOrNull()
        assumeTrue("Pass -e miclevel <seconds> to run this test", seconds != null)

        val rate = 16000
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record =
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, rate),
            )
        val buffer = ShortArray(rate)
        record.startRecording()
        Log.i("MICLEVEL", "recording state=${record.recordingState}")
        try {
            for (second in 1..seconds!!) {
                var read = 0
                var peak = 0
                while (read < rate) {
                    val n = record.read(buffer, 0, buffer.size)
                    if (n <= 0) break
                    for (i in 0 until n) peak = maxOf(peak, abs(buffer[i].toInt()))
                    read += n
                }
                Log.i("MICLEVEL", "second=$second peak=$peak")
            }
        } finally {
            record.stop()
            record.release()
        }
    }
}
