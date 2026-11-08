package dev.sriramcodessw.versesync.engine

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sriramcodessw.versesync.data.Song
import dev.sriramcodessw.versesync.data.VerseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private const val TAG = "RECOG"

/**
 * Real-microphone recognition check for one language, driven from the PC.
 *
 * It is skipped unless started with `-e code <language code>`. The matching sentence is read from
 * `<app files>/lang/<code>.txt`, the speech model must already be installed for that
 * language, and the PC plays the recording into the emulator's microphone after RECOG READY is
 * logged. The test fails unless the song whose lyrics are that sentence is recognized, and it also
 * records what the speech model actually heard so a failure can be told apart from a bad model.
 */
@RunWith(AndroidJUnit4::class)
class LanguageRecognitionInstrumentedTest {

    @Test
    fun spokenSentence_isRecognizedAsItsSong() {
        val args = InstrumentationRegistry.getArguments()
        val code = args.getString("code")
        assumeTrue("Pass -e code <language> to run this test", code != null)
        code!!
        val decoyLyrics = args.getString("decoy") ?: "completely unrelated words about something else entirely"

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sentence = File(context.filesDir, "lang/$code.txt").readText(Charsets.UTF_8).trim()

        val db = Room.inMemoryDatabaseBuilder(context, VerseDatabase::class.java).build()
        val dao = db.songDao()
        val scope = CoroutineScope(Dispatchers.Default)
        val engine = HybridRecognitionEngine(context, dao)
        try {
            runBlocking {
                dao.insert(Song(title = "Target", artist = "test", lyrics = sentence, language = code))
                dao.insert(Song(title = "Decoy", artist = "test", lyrics = decoyLyrics, language = code))

                engine.loadLanguageModel(code)
                val ready = withTimeoutOrNull(240_000) { engine.isModelReady.first { it } }
                Log.i(TAG, "model code=$code ready=${ready == true}")
                assertTrue("Speech model for $code did not load", ready == true)

                var lastHeard = ""
                scope.launch {
                    engine.sttText.collect {
                        if (it.isNotBlank() && it != lastHeard) {
                            lastHeard = it
                            Log.i(TAG, "heard code=$code text=$it")
                        }
                    }
                }

                engine.startRecognition(isPro = false)
                Log.i(TAG, "READY code=$code")

                val titles = withTimeoutOrNull(90_000) {
                    engine.recognitionResult.first { result -> result.candidates.any { it.title == "Target" } }
                }?.candidates?.map { it.title }
                Log.i(TAG, "RESULT code=$code matched=${titles != null} candidates=$titles heard=$lastHeard")
                assertTrue("Song for $code was not recognized. Heard: $lastHeard", titles != null)
                assertTrue("Decoy must not be recognized for $code", titles?.contains("Decoy") != true)
            }
        } finally {
            engine.stopRecognition()
            scope.cancel()
            db.close()
        }
    }
}
