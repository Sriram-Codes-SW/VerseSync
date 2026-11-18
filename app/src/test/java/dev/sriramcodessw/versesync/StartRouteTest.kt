package dev.sriramcodessw.versesync

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** TC-U-61: the app does not show the first-run screen, and so does not ask for location, before it has read its saved setup state (issue #79). */
class StartRouteTest {
    @Test
    fun beforeTheSavedValueIsRead_thereIsNoRoute_soNoFirstRunScreenIsBuilt() {
        assertNull(startRouteFor(null))
    }

    @Test
    fun afterFirstRunSetup_theAppStartsOnTheMainScreen() {
        assertEquals(AppRoutes.MAIN_SCREEN, startRouteFor(true))
    }

    @Test
    fun beforeFirstRunSetup_theAppStartsOnTheLanguageScreen() {
        assertEquals(AppRoutes.LANGUAGE_SELECTION, startRouteFor(false))
    }
}
