package dev.sriramcodessw.versesync.eval

import android.Manifest
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
import kotlin.math.sqrt

private const val TAG = "MICPROBE"

/**
 * Measures how loud the device's microphone hears the room for a few seconds, so a test can tell whether
 * sound played on the PC reaches an emulator's microphone (docs/09, the end-to-end song test). It plays and
 * stores nothing: only the level is logged.
 *
 * It is skipped unless started with `-e probe true`. The result is in the log under the tag MICPROBE as RMS and
 * peak on a 0 to 1 scale, for each second.
 */
@RunWith(AndroidJUnit4::class)
class MicrophoneLevelProbeInstrumentedTest {
    @SuppressLint("MissingPermission")
    @Test
    fun logTheMicrophoneLevel() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Pass -e probe true to run this test", args.getString("probe") == "true")

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)

        val seconds = args.getString("seconds")?.toIntOrNull() ?: 5
        val rate = 16_000
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder =
            AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, rate))
        val chunk = ShortArray(rate)
        var loudest = 0.0
        try {
            recorder.startRecording()
            for (second in 1..seconds) {
                var read = 0
                while (read < chunk.size) {
                    val n = recorder.read(chunk, read, chunk.size - read)
                    if (n <= 0) break
                    read += n
                }
                var sum = 0.0
                var peak = 0.0
                for (i in 0 until read) {
                    val v = chunk[i] / 32768.0
                    sum += v * v
                    peak = maxOf(peak, abs(v))
                }
                val rms = if (read > 0) sqrt(sum / read) else 0.0
                loudest = maxOf(loudest, rms)
                Log.i(TAG, "second=$second samples=$read rms=${"%.5f".format(rms)} peak=${"%.5f".format(peak)}")
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
        Log.i(TAG, "loudest rms=${"%.5f".format(loudest)}")
    }
}
