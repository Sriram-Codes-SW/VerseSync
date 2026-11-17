package dev.sriramcodessw.versesync.data

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TC-U-33: the work done by the model unpack job, which must be safe to run again. */
class UnpackDownloadedModelTest {
    @TempDir
    lateinit var tempDir: File

    private val zip get() = File(tempDir, "model-xx.zip")
    private val target get() = File(tempDir, "models/model-xx")

    private fun writeModelZip(file: File = zip) {
        ZipOutputStream(file.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("vosk-model-xx/"))
            out.closeEntry()
            out.putNextEntry(ZipEntry("vosk-model-xx/conf/model.conf"))
            out.write("fresh".toByteArray())
            out.closeEntry()
        }
    }

    @Test
    fun aGoodArchive_isInstalledAndDeleted() {
        writeModelZip()

        assertEquals(UnpackOutcome.INSTALLED, unpackDownloadedModel(zip, target))

        assertEquals("fresh", File(target, "conf/model.conf").readText())
        assertFalse(zip.exists(), "The archive should be deleted once it is installed")
    }

    @Test
    fun runningItAgainAfterItFinished_changesNothing() {
        writeModelZip()
        unpackDownloadedModel(zip, target)

        assertEquals(UnpackOutcome.ALREADY_INSTALLED, unpackDownloadedModel(zip, target))

        assertEquals("fresh", File(target, "conf/model.conf").readText())
    }

    @Test
    fun noArchiveAndNoModel_isAFailure() {
        assertEquals(UnpackOutcome.FAILED, unpackDownloadedModel(zip, target))
    }

    @Test
    fun aCorruptArchive_failsAndLeavesAWorkingModelAlone() {
        File(target, "conf").mkdirs()
        File(target, "conf/model.conf").writeText("working")
        zip.writeText("this is not a zip file")

        assertEquals(UnpackOutcome.FAILED, unpackDownloadedModel(zip, target))

        assertEquals("working", File(target, "conf/model.conf").readText())
        assertFalse(zip.exists())
    }

    @Test
    fun aNewerArchive_replacesTheInstalledModel() {
        File(target, "conf").mkdirs()
        File(target, "conf/model.conf").writeText("old")
        writeModelZip()

        assertEquals(UnpackOutcome.INSTALLED, unpackDownloadedModel(zip, target))

        assertTrue(File(target, "conf/model.conf").readText() == "fresh")
    }

    @Test
    fun theSharedStatus_recordsEachLanguageAndSurvivesNewObservers() {
        ModelDownloadStatus.update("xx", 0)
        ModelDownloadStatus.update("yy", 100)
        ModelDownloadStatus.update("xx", -1)

        assertEquals(-1, ModelDownloadStatus.flow.value["xx"])
        assertEquals(100, ModelDownloadStatus.flow.value["yy"])
    }
}
