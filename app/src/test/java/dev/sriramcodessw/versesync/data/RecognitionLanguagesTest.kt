package dev.sriramcodessw.versesync.data

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RecognitionLanguagesTest {
    private val english = LanguageModelInfo("English", "en-us", "https://example.test/en.zip")
    private val hindi = LanguageModelInfo("Hindi", "hi", "https://example.test/hi.zip")
    private val tamil = LanguageModelInfo("Tamil", "ta", null)
    private val greek = LanguageModelInfo("Greek", "el", null)
    private val all = listOf(english, hindi, tamil, greek)

    @Test
    fun recognitionLanguages_offersInstalledAndNoModelLanguages() {
        assertEquals(listOf(hindi, tamil, greek), recognitionLanguages(all, setOf("hi")))
    }

    @Test
    fun recognitionLanguages_hidesLanguagesWhoseModelIsNotInstalled() {
        // Same as before for languages that have a model: they appear only once downloaded.
        assertEquals(listOf(tamil, greek), recognitionLanguages(all, emptySet()))
    }

    @Test
    fun recognitionLanguages_keepsTheOrderOfTheFullList() {
        assertEquals(listOf(english, hindi, tamil, greek), recognitionLanguages(all, setOf("hi", "en-us")))
    }

    @Test
    fun defaultRecognitionLanguage_isTheFirstInstalledOne() {
        assertEquals(hindi, defaultRecognitionLanguage(all, setOf("hi")))
    }

    @Test
    fun defaultRecognitionLanguage_fallsBackToTheFirstLanguageNotAPitchOnlyOne() {
        // With nothing installed the screen used to start on English. It still does.
        assertEquals(english, defaultRecognitionLanguage(all, emptySet()))
    }
}
