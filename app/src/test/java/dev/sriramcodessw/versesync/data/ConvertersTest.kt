package dev.sriramcodessw.versesync.data

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun toFloatList_withNullOrBlankString_shouldReturnNullWithoutCrashing() {
        // Given (ADR-06, TC-U-01)
        assertNull(converters.toFloatList(null))
        assertNull(converters.toFloatList(""))
        assertNull(converters.toFloatList("   "))
    }

    @Test
    fun toFloatList_withValidCsv_shouldParseFloatListCorrectly() {
        // Given (ADR-06, TC-U-02)
        val csv = "100.5,200.25,300.0"
        val expected = listOf(100.5f, 200.25f, 300.0f)

        val result = converters.toFloatList(csv)

        assertEquals(expected, result)
    }

    @Test
    fun fromFloatList_withList_shouldFormatAsCsv() {
        val list = listOf(100.5f, 200.25f)
        val result = converters.fromFloatList(list)

        assertEquals("100.5,200.25", result)
    }

    @Test
    fun fromFloatList_withNull_shouldReturnNull() {
        assertNull(converters.fromFloatList(null))
    }
}
