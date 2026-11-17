package dev.sriramcodessw.versesync.concert

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * TC-U-54: concert mode stays free of audio, recognition and network (ADR-20 Phase 1), and the app
 * does not ask for a new permission. These read the source files, so a change that breaks the rule
 * fails here instead of in a review.
 */
class ConcertGuardsTest {
    private val concertSources =
        File("src/main/java/dev/sriramcodessw/versesync/concert")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    @Test
    fun theConcertSourcesWereFound() {
        assertTrue(concertSources.size >= 5, "found ${concertSources.map { it.name }}")
    }

    @Test
    fun concertCodeUsesNoAudioRecognitionOrNetwork() {
        val forbidden =
            listOf(
                "versesync.engine",
                "versesync.fingerprinting",
                "SpeechRecognizer",
                "AudioRecord",
                "MediaRecorder",
                "RECORD_AUDIO",
                "org.vosk",
                "org.tensorflow",
                "java.net.",
                "okhttp",
                "HttpURLConnection",
                "ConnectivityManager",
                "android.net.",
                "URL(",
                "WakeLock",
            )
        val found =
            concertSources.flatMap { file ->
                val text = file.readText()
                forbidden.filter { it in text }.map { "${file.name}: $it" }
            }

        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun concertModeNeverLooksAtProStatus() {
        // Concert mode is free (ADR-20, the owner's decision). Pro status must not gate it.
        val found =
            concertSources
                .filter { Regex("""isPro|BillingViewModel|TrialViewModel""").containsMatchIn(it.readText()) }
                .map { it.name }

        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun theManifestDeclaresTheSamePermissionsAsBefore() {
        // Concert mode needs none. Adding a permission means updating this list on purpose, with a
        // reason in the pull request and the ADR.
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val declared =
            Regex("""<uses-permission[^>]*?android:name="([^"]+)"""")
                .findAll(manifest)
                .map { it.groupValues[1] }
                .toSortedSet()

        val expected =
            sortedSetOf(
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.BLUETOOTH",
                "android.permission.BLUETOOTH_ADMIN",
                "android.permission.BLUETOOTH_ADVERTISE",
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.BLUETOOTH_SCAN",
                "android.permission.RECORD_AUDIO",
                "com.android.vending.BILLING",
            )

        assertEquals(expected, declared)
    }
}
