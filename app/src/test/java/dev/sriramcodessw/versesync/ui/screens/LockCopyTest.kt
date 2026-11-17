package dev.sriramcodessw.versesync.ui.screens

import dev.sriramcodessw.versesync.data.LockType
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TC-U-47: what a person reads about the voice shortcut never claims security or identity (issue #48). It checks
 * the words, not who is speaking, and the device lock is the only protection.
 */
class LockCopyTest {
    // Words that would claim more than the shortcut does. Matched without regard to case.
    private val claims =
        listOf(
            Regex("""\bsecur\w*""", RegexOption.IGNORE_CASE),
            Regex("""authenticat\w*""", RegexOption.IGNORE_CASE),
            Regex("""\bauth\b""", RegexOption.IGNORE_CASE),
            Regex("""verif\w*""", RegexOption.IGNORE_CASE),
            Regex("""\bidentity\b""", RegexOption.IGNORE_CASE),
            Regex("""protect\w*""", RegexOption.IGNORE_CASE),
            Regex("""\byour voice\b""", RegexOption.IGNORE_CASE),
            Regex("""\bonly you\b""", RegexOption.IGNORE_CASE),
            Regex("""\bvocal (lock|auth\w*)\b""", RegexOption.IGNORE_CASE),
        )

    @Test
    fun theCopy_claimsNeitherSecurityNorIdentity() {
        // Also no 'recogni...': nothing here recognizes a person.
        val words = claims + Regex("""recogni\w*""", RegexOption.IGNORE_CASE)
        LockCopy.all.forEach { text ->
            words.forEach { re -> assertFalse(re.containsMatchIn(text), "\"$text\" matches ${re.pattern}") }
        }
    }

    @Test
    fun theDescription_saysWhoCanOpenIt_andTheFallbackNamesTheDeviceLock() {
        assertTrue(Regex("""\banyone\b""", RegexOption.IGNORE_CASE).containsMatchIn(LockCopy.DESCRIPTION))
        assertTrue("words" in LockCopy.DESCRIPTION)
        assertTrue("device lock" in LockCopy.FALLBACK_BUTTON.lowercase())
        assertTrue("device lock" in LockCopy.DEVICE_PROMPT_SUBTITLE.lowercase())
    }

    @Test
    fun everyStringOfTheCopy_isInTheListTheTestsCheck() {
        val constants =
            LockCopy::class.java.declaredFields
                .filter {
                    it.type == String::class.java &&
                        java.lang.reflect.Modifier
                            .isStatic(it.modifiers)
                }.map { it.also { f -> f.isAccessible = true }.get(null) as String }
        assertTrue(constants.size >= 9, "found ${constants.size} strings")
        constants.forEach { assertTrue(it in LockCopy.all, "\"$it\" is not in LockCopy.all, so the other tests would not check it") }
    }

    @Test
    fun theSettingsShowTheNewName_forTheStoredValueThatDidNotChange() {
        assertEquals("Voice shortcut", LockCopy.typeName(LockType.VOCAL))
        assertEquals("Device", LockCopy.typeName(LockType.DEVICE))
        assertEquals("Biometric", LockCopy.typeName(LockType.BIOMETRIC))
        // The stored value is unchanged (ADR-12): the enum still has VOCAL.
        assertEquals("VOCAL", LockType.VOCAL.name)
    }

    private val sources =
        listOf(
            "src/main/java/dev/sriramcodessw/versesync/ui/screens/VocalLockScreen.kt",
            "src/main/java/dev/sriramcodessw/versesync/ui/screens/SettingsScreen.kt",
            "src/main/java/dev/sriramcodessw/versesync/MainActivity.kt",
        )

    // Text in these files that is not about the lock.
    private val notAboutTheLock = setOf("Visual feedback of your voice stability")

    @Test
    fun noStringWrittenInTheLockScreens_claimsMoreThanTheCopyDoes() {
        val literal = Regex(""""((?:[^"\\\n]|\\.)*)"""")
        sources.forEach { path ->
            val file = File(path)
            assertTrue(file.isFile, "$path not found from ${File(".").absolutePath}")
            val code = file.readLines().joinToString("\n") { it.substringBefore("//") }
            literal.findAll(code).map { it.groupValues[1] }.filter { it !in notAboutTheLock }.forEach { text ->
                claims.forEach { re -> assertFalse(re.containsMatchIn(text), "\"$text\" in $path matches ${re.pattern}; put lock wording in LockCopy") }
            }
        }
    }
}
