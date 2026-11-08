package dev.sriramcodessw.versesync.nfc

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.DynamicTest
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AppLockAuthenticatorsTest {

    private val strongPair = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    private val weakPair = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    @Test
    fun androidNineAndTen_useTheWeakPair() {
        // androidx.biometric rejects the strong pair on API 28 and 29, both when checking
        // availability and when building a prompt, which throws.
        assertEquals(weakPair, appLockAuthenticators(28))
        assertEquals(weakPair, appLockAuthenticators(29))
    }

    @TestFactory
    fun otherVersions_useTheStrongPair(): List<DynamicTest> =
        listOf(26, 27, 30, 31, 33, 34, 35, 36).map { sdk ->
            DynamicTest.dynamicTest("API $sdk uses the strong pair") {
                assertEquals(strongPair, appLockAuthenticators(sdk))
            }
        }

    @Test
    fun theTwoPairsAreDifferent() {
        assertNotEquals(strongPair, weakPair)
    }
}
