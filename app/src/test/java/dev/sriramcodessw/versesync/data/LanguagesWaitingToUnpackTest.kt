package dev.sriramcodessw.versesync.data

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** TC-U-62: at start-up the app finds models whose download finished while it was closed, and only those (issue #36). */
class LanguagesWaitingToUnpackTest {
    @Test
    fun aFinishedDownloadWhoseArchiveIsStillThere_isWaiting() {
        val waiting = languagesWaitingToUnpack(listOf("VerseSyncModel:sv", "VerseSyncModel:de")) { true }
        assertEquals(listOf("sv", "de"), waiting)
    }

    @Test
    fun aFinishedDownloadWhoseArchiveWasUnpacked_isNotWaiting_becauseUnpackingDeletesTheArchive() {
        val waiting = languagesWaitingToUnpack(listOf("VerseSyncModel:sv", "VerseSyncModel:de")) { it == "de" }
        assertEquals(listOf("de"), waiting)
    }

    @Test
    fun descriptionsThatAreNotOurs_orNotAPlainLanguageCode_areIgnored() {
        val waiting =
            languagesWaitingToUnpack(
                listOf(null, "", "Something else", "VerseSyncModel:", "VerseSyncModel:../../x", "VerseSyncModel:a/b", "VerseSyncModel:en-us"),
            ) { true }
        assertEquals(listOf("en-us"), waiting)
    }

    @Test
    fun theSameLanguageIsOnlyListedOnce() {
        val waiting = languagesWaitingToUnpack(listOf("VerseSyncModel:fr", "VerseSyncModel:fr")) { true }
        assertEquals(listOf("fr"), waiting)
    }

    @Test
    fun noDownloads_nothingWaiting() {
        assertEquals(emptyList(), languagesWaitingToUnpack(emptyList()) { true })
    }
}
