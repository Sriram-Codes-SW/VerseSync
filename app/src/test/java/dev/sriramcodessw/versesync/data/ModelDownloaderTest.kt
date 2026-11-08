package dev.sriramcodessw.versesync.data

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ModelDownloaderTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun resolveInside_allowsNestedPaths() {
        val resolved = resolveInside(tempDir, "am/final.mdl")
        assertNotNull(resolved)
        assertEquals(File(tempDir.canonicalFile, "am/final.mdl"), resolved)
    }

    @Test
    fun resolveInside_rejectsParentTraversal() {
        assertNull(resolveInside(tempDir, "../evil.txt"))
        assertNull(resolveInside(tempDir, "am/../../evil.txt"))
    }

    @Test
    fun resolveInside_rejectsSiblingDirectoryWithSamePrefix() {
        val sibling = File(tempDir.parentFile, tempDir.name + "-other")
        assertNull(resolveInside(tempDir, "../" + sibling.name + "/x"))
    }

    @Test
    fun resolveInside_allowsRedundantDotSegmentsThatStayInside() {
        assertNotNull(resolveInside(tempDir, "am/./../conf/model.conf"))
    }
}

class ModelArchiveInstallTest {
    @TempDir
    lateinit var tempDir: File

    private fun zip(
        name: String,
        vararg entries: Pair<String, String>,
    ): File {
        val file = File(tempDir, name)
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (entryName, content) ->
                out.putNextEntry(ZipEntry(entryName))
                if (!entryName.endsWith("/")) out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }

    private fun existingModel(): File =
        File(tempDir, "models/model-xx").also {
            File(it, "am").mkdirs()
            File(it, "am/final.mdl").writeText("working model")
        }

    @Test
    fun install_flattensTheArchiveRootFolder() {
        val archive = zip("good.zip", "vosk-model-xx/" to "", "vosk-model-xx/am/" to "", "vosk-model-xx/am/final.mdl" to "new")
        val target = File(tempDir, "models/model-xx")
        target.parentFile.mkdirs()

        installModelArchive(archive, target)

        assertEquals("new", File(target, "am/final.mdl").readText())
        assertFalse(File(target.parentFile, "model-xx.tmp").exists())
    }

    @Test
    fun install_replacesAnExistingModel() {
        val target = existingModel()
        val archive = zip("good.zip", "root/" to "", "root/am/" to "", "root/am/final.mdl" to "newer")

        installModelArchive(archive, target)

        assertEquals("newer", File(target, "am/final.mdl").readText())
        assertFalse(File(target.parentFile, "model-xx.old").exists())
    }

    @Test
    fun failedInstall_leavesAnExistingModelWorking() {
        val target = existingModel()
        val archive = zip("evil.zip", "root/" to "", "root/ok.txt" to "x", "root/../../escape.txt" to "bad")

        assertFailsWith<SecurityException> { installModelArchive(archive, target) }

        assertEquals("working model", File(target, "am/final.mdl").readText())
        assertFalse(File(target.parentFile, "model-xx.tmp").exists())
    }

    @Test
    fun corruptArchive_leavesAnExistingModelWorking() {
        val target = existingModel()
        val archive = File(tempDir, "broken.zip").also { it.writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }

        runCatching { installModelArchive(archive, target) }

        assertEquals("working model", File(target, "am/final.mdl").readText())
    }
}
