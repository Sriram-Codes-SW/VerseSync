package dev.sriramcodessw.versesync.data

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
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
