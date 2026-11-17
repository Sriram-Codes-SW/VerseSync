package dev.sriramcodessw.versesync.eval

import org.junit.jupiter.api.Test
import kotlin.math.ln
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TC-U-46: the grid and the criterion of docs/09 section 12 are what the code runs. */
class SettleSelectionTest {
    @Test
    fun theGrid_isTheSixtyFourRulesOfTheDocument_withTheGapScaledForTheLibrary() {
        val grid = SettleSelection.grid(100)

        assertEquals(64, grid.size)
        assertEquals(64, grid.map { it.label() }.toSet().size)
        assertTrue(grid.all { it.rule.minWords == 4 })
        assertEquals(setOf(0.65f, 0.8f), grid.map { it.rule.minShare }.toSet())
        assertEquals(setOf(0, 8, 16, 31), grid.map { it.settle.holdFrames }.toSet())
        // A gap of 1.0 in units of ln(1 + songs) is about 4.6 for 100 songs.
        assertEquals(ln(101.0), grid.maxOf { it.rule.absoluteGap }.toDouble(), 0.001)
        assertEquals(grid.size, grid.map { it.rule.absoluteGap }.size)
    }

    private fun scored(
        hold: Int,
        retractable: Boolean,
        share: Float,
        gap: Float,
        right: Int,
        wrong: Int,
        falseDecisions: Int,
        unknown: Int = 300,
    ) = SettleSelection.Scored(
        SettleSelection.Spec(DecisionRule(4, share, gap * 4.6f), Settle(hold, retractable), gap),
        RuleSlice("r", 100, unknown, Rate(right, 100), Rate(wrong, 100), Rate(falseDecisions, unknown), Rate(0, 100), Rate(0, 100), Rate(0, unknown), 0),
    )

    @Test
    fun theCriterion_needsALowPointRate_andALowUpperBound_andFewWrongSongs() {
        assertTrue(scored(0, false, 0.65f, 0.4f, 80, 0, 2, 300).eligible, "0.7 percent, upper end about 2.4 percent")
        assertTrue(!scored(0, false, 0.65f, 0.4f, 80, 0, 4, 300).eligible, "1.3 percent is over 1 percent")
        assertTrue(!scored(0, false, 0.65f, 0.4f, 80, 0, 0, 40).eligible, "0 of 40 has an upper end far over 3 percent")
        assertTrue(!scored(0, false, 0.65f, 0.4f, 80, 2, 1, 300).eligible, "2 wrong songs is over 1 percent")
    }

    @Test
    fun aTie_goesToTheShorterHold_thenStickyEvidence_thenTheLowerShare_thenTheSmallerGap() {
        val a = scored(16, false, 0.8f, 0.4f, 70, 0, 1)
        val b = scored(8, true, 0.8f, 0.4f, 70, 0, 1)
        val c = scored(8, false, 0.8f, 0.4f, 70, 0, 1)
        val d = scored(8, false, 0.65f, 0.4f, 70, 0, 1)
        val e = scored(8, false, 0.65f, 0.6f, 70, 0, 1)

        assertEquals(d.spec, SettleSelection.choose(listOf(a, b, c, e, d)))
        // More right answers beat every tie-break.
        val better = scored(31, true, 0.8f, 1.0f, 71, 0, 1)
        assertEquals(better.spec, SettleSelection.choose(listOf(a, b, c, d, e, better)))
    }

    @Test
    fun noEligibleRule_chooseNothing() {
        assertNull(SettleSelection.choose(listOf(scored(0, false, 0.65f, 0.4f, 90, 0, 40))))
    }
}
