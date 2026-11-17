package dev.sriramcodessw.versesync.eval

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "EVALCAP"

/**
 * Captures what the speech recognizer hears for the recordings of an evaluation corpus, for the
 * JVM harness to replay (docs/09-evaluation-harness.md). Each recording is fed straight into the real
 * Vosk recognizer one 1,024-sample frame at a time, with no microphone, so a run is repeatable.
 *
 * It is skipped unless started with `-e corpus <name> -e code <language code>`. The corpus is read
 * from `<app files>/eval/<name>/` (queries.json and recordings/), the speech model must already be
 * installed for that language, and a transcript is written to `transcripts/<query id>.json` for each
 * recording in that language: the text whenever it changed and how many frames had been fed then.
 *
 * Differences from the running app: frames come at once instead of in real time, and the recognizer
 * is fed 1,024-sample pieces instead of the larger buffers the app's speech service reads.
 */
@RunWith(AndroidJUnit4::class)
class TranscriptCaptureInstrumentedTest {
    @Test
    fun captureTranscripts() {
        val args = InstrumentationRegistry.getArguments()
        val corpus = args.getString("corpus")
        val code = args.getString("code")
        assumeTrue("Pass -e corpus <name> -e code <language> to run this test", corpus != null && code != null)
        corpus!!
        code!!

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.filesDir, "eval/$corpus")
        val gson = Gson()
        val queries = gson.fromJson(File(root, "queries.json").readText(Charsets.UTF_8), JsonArray::class.java).map { it.asJsonObject }
        val mine = queries.filter { it.str("language") == code && it.str("file") != null }
        assumeTrue("No recordings in $code", mine.isNotEmpty())

        val modelDir = File(context.filesDir, "models/model-$code")
        assertTrue("Speech model for $code is not installed at $modelDir", modelDir.isDirectory)
        File(root, "transcripts").mkdirs()

        Model(modelDir.absolutePath).use { model ->
            for (query in mine) {
                val id = query.str("id")!!
                val heard = transcribe(model, File(root, query.str("file")!!))
                File(root, "transcripts/$id.json").writeText(gson.toJson(heard), Charsets.UTF_8)
                Log.i(TAG, "CAPTURED code=$code id=$id entries=${heard.size} last=${heard.lastOrNull()?.get("text")?.asString?.take(120)}")
            }
        }
    }

    /** The text the recognizer reported, as the app reads it: a partial or final text, kept when it is not blank and changed. */
    private fun transcribe(
        model: Model,
        wav: File,
    ): List<JsonObject> {
        val pcm = readPcm16(wav)
        val heard = mutableListOf<JsonObject>()
        var last = ""

        fun note(
            json: String,
            frames: Int,
            isFinal: Boolean,
        ) {
            val parsed = Gson().fromJson(json, JsonObject::class.java)
            val text =
                when {
                    parsed.has("partial") -> parsed.get("partial").asString
                    parsed.has("text") -> parsed.get("text").asString
                    else -> ""
                }
            // A finished result is kept even when its text equals the last partial, because it marks the end of an utterance.
            if (text.isNotBlank() && (text != last || isFinal)) {
                last = text
                heard +=
                    JsonObject().apply {
                        addProperty("frame", frames)
                        addProperty("text", text)
                        if (isFinal) addProperty("final", true)
                    }
            }
        }

        Recognizer(model, SAMPLE_RATE.toFloat()).use { recognizer ->
            val frameBytes = FRAME_SAMPLES * 2
            var frames = 0
            var at = 0
            while (at + frameBytes <= pcm.size) {
                val chunk = pcm.copyOfRange(at, at + frameBytes)
                val isFinal = recognizer.acceptWaveForm(chunk, chunk.size)
                frames++
                note(if (isFinal) recognizer.result else recognizer.partialResult, frames, isFinal)
                at += frameBytes
            }
            note(recognizer.finalResult, frames, true)
        }
        return heard
    }

    private fun JsonObject.str(key: String): String? = if (has(key) && !get(key).isJsonNull) get(key).asString else null

    /** Reads a 16 kHz mono WAV, 16-bit or 32-bit float, as little-endian 16-bit samples. */
    private fun readPcm16(file: File): ByteArray {
        val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var format = 1
        var channels = 1
        var rate = SAMPLE_RATE
        var bits = 16
        while (pos + 8 <= bytes.limit()) {
            val id = String(bytes.array(), pos, 4)
            val length = bytes.getInt(pos + 4)
            if (id == "fmt ") {
                format = bytes.getShort(pos + 8).toInt() and 0xFFFF
                channels = bytes.getShort(pos + 10).toInt()
                rate = bytes.getInt(pos + 12)
                bits = bytes.getShort(pos + 22).toInt()
            } else if (id == "data") {
                assertTrue("${file.name}: needs 16 kHz mono, has $rate Hz and $channels channels", rate == SAMPLE_RATE && channels == 1)
                val start = pos + 8
                val count = minOf(length, bytes.limit() - start)
                if (format == 1 && bits == 16) return bytes.array().copyOfRange(start, start + count)
                assertTrue("${file.name}: only 16-bit PCM and 32-bit float are supported", format == 3 && bits == 32)
                val out = ByteBuffer.allocate(count / 2).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until count / 4) {
                    out.putShort((bytes.getFloat(start + i * 4) * 32767f).toInt().coerceIn(-32768, 32767).toShort())
                }
                return out.array()
            }
            pos += 8 + length + (length and 1)
        }
        error("${file.name} has no audio data")
    }

    private companion object {
        const val SAMPLE_RATE = 16000
        const val FRAME_SAMPLES = 1024
    }
}
