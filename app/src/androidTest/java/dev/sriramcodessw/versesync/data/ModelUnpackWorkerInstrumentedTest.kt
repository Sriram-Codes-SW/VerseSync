package dev.sriramcodessw.versesync.data

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val TAG = "UNPACK"

/**
 * Runs the real model unpack job on a device (TC-M-15).
 *
 * The model archives made here are tiny and use a made-up language code, so no speech model is
 * needed. [bigModel_isQueuedForAManualCheck] is skipped unless started with `-e bigmodel <MB>`.
 * It queues an unpack of that many megabytes and ends, so the job can be watched while the app is
 * closed or its process is killed.
 */
@RunWith(AndroidJUnit4::class)
class ModelUnpackWorkerInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val language = "zz"

    private val zipFile get() = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "model-$language.zip")
    private val modelDir get() = File(context.filesDir, "models/model-$language")

    @After
    fun cleanUp() {
        // The manual check needs the queued job and its files to stay after this test ends.
        if (InstrumentationRegistry.getArguments().getString("bigmodel") != null) return
        zipFile.delete()
        modelDir.deleteRecursively()
        File(context.filesDir, "models/model-$language.tmp").deleteRecursively()
        runBlocking { LanguagePreferences(context).removeLanguage(language) }
    }

    private fun writeModelZip(file: File) {
        file.parentFile?.mkdirs()
        ZipOutputStream(file.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("vosk-model-zz/"))
            out.closeEntry()
            out.putNextEntry(ZipEntry("vosk-model-zz/conf/model.conf"))
            out.write("fresh".toByteArray())
            out.closeEntry()
        }
    }

    private fun runWorker(): ListenableWorker.Result =
        runBlocking {
            TestListenableWorkerBuilder<ModelUnpackWorker>(context)
                .setInputData(workDataOf(ModelUnpackWorker.KEY_LANGUAGE to language))
                .build()
                .doWork()
        }

    @Test
    fun worker_installsTheModelRecordsTheLanguageAndReportsDone() {
        writeModelZip(zipFile)

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals("fresh", File(modelDir, "conf/model.conf").readText())
        assertFalse("The archive should be deleted", zipFile.exists())
        assertTrue(runBlocking { LanguagePreferences(context).installedLanguages.first() }.contains(language))
        assertEquals(100, ModelDownloadStatus.flow.value[language])
    }

    @Test
    fun worker_failsOnACorruptArchiveAndInstallsNothing() {
        zipFile.parentFile?.mkdirs()
        zipFile.writeText("not a zip")

        val result = runWorker()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertFalse(modelDir.exists())
        assertFalse(runBlocking { LanguagePreferences(context).installedLanguages.first() }.contains(language))
        assertEquals(-1, ModelDownloadStatus.flow.value[language])
    }

    @Test
    fun enqueue_runsThroughWorkManagerToSuccess() {
        writeModelZip(zipFile)

        ModelUnpackWorker.enqueue(context, language)

        val workManager = WorkManager.getInstance(context)
        val deadline = System.currentTimeMillis() + 60_000
        var state: WorkInfo.State? = null
        while (System.currentTimeMillis() < deadline) {
            state =
                workManager
                    .getWorkInfosForUniqueWork(ModelUnpackWorker.workName(language))
                    .get()
                    .firstOrNull()
                    ?.state
            if (state == WorkInfo.State.SUCCEEDED) break
            Thread.sleep(250)
        }
        assertEquals(WorkInfo.State.SUCCEEDED, state)
        assertEquals("fresh", File(modelDir, "conf/model.conf").readText())
    }

    @Test
    fun bigModel_isQueuedForAManualCheck() {
        val megabytes = InstrumentationRegistry.getArguments().getString("bigmodel")?.toIntOrNull()
        assumeTrue("Pass -e bigmodel <MB> to run this test", megabytes != null)

        // Zeros compress to almost nothing, so the archive is small but unpacking writes a lot.
        zipFile.parentFile?.mkdirs()
        ZipOutputStream(zipFile.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("vosk-model-zz/"))
            out.closeEntry()
            val chunk = ByteArray(1024 * 1024)
            repeat(megabytes!!.coerceAtLeast(1) / 8) { part ->
                out.putNextEntry(ZipEntry("vosk-model-zz/am/part-$part.bin"))
                repeat(8) { out.write(chunk) }
                out.closeEntry()
            }
        }
        Log.i(TAG, "archive bytes=${zipFile.length()} unpacks to about $megabytes MB")

        ModelUnpackWorker.enqueue(context, language)
        Log.i(TAG, "QUEUED language=$language")

        // The files are left for the manual check, so skip the clean up.
        throw org.junit.AssumptionViolatedException("Queued. Watch models/model-$language and kill the app process.")
    }
}
