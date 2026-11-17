package dev.sriramcodessw.versesync

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/** TC-U-35: the pitch library is used in one place only (docs/07-pitch-detection-design.md). */
class ArchitectureTest {
    private val mainSources: File =
        listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }

    @Test
    fun theSignalProcessingLibraryIsOnlyUsedByItsAdapter() {
        val offenders =
            mainSources
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { it.invariantSeparatorsPath.contains("/audio/tarsos/") }
                .filter { file -> file.readLines().any { it.contains("be.tarsos") } }
                .map { it.name }
                .toList()

        assertTrue(offenders.isEmpty(), "These files use TarsosDSP outside the adapter: $offenders")
    }

    @Test
    fun theAudioInterfacesDoNotDependOnAndroidOrALibrary() {
        val ports = File(mainSources, "dev/sriramcodessw/versesync/audio/AudioPorts.kt").readLines()

        val forbidden = ports.filter { it.startsWith("import ") && !it.startsWith("import kotlin") }

        assertTrue(forbidden.isEmpty(), "AudioPorts.kt imports: $forbidden")
    }
}
