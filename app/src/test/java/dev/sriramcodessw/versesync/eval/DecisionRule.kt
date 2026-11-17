package dev.sriramcodessw.versesync.eval

import dev.sriramcodessw.versesync.engine.SpeechDetail

/*
 * A rule that DECIDES which song was said, apart from the rule that decides which songs are CANDIDATES.
 *
 * The candidate rule (SpeechRules: share and minimum words) is what the app uses to build the list a person
 * chooses from, where a wrong candidate is harmless. A decision acts without a person choosing, as the vocal
 * lock does, so it is judged on its own, and it must see every song: if songs that are not candidates were
 * left out, a stricter candidate rule would remove the rivals and make the leader look clear (the sweep in
 * docs/09 section 9 first did that). Nothing here changes what the app runs.
 */
internal data class DecisionRule(
    /** The leader must have found at least this many different heard words in its lyrics (or all of them, if fewer). */
    val minWords: Int,
    /** The leader must have found at least this share of the heard words in its lyrics. */
    val minShare: Float,
    /** The leader's rarity weight must exceed the strongest other song's by MORE than this, so a tie never decides. */
    val absoluteGap: Float,
    /** And, if above 0, be at least this fraction above it (the rule the lock uses, see SpeechMatcher.LEAD_MARGIN). */
    val relativeMargin: Float = 0f,
) {
    /** The song decided on, or null. [state] is the evidence for every song with a heard word in its lyrics. */
    fun decide(state: Map<String, SpeechDetail>): Decision? {
        if (state.isEmpty()) return null
        val ranked = state.entries.sortedByDescending { it.value.weight }
        val top = ranked[0]
        val rival = if (ranked.size > 1) ranked[1].value.weight else 0f
        val detail = top.value
        if (detail.words < minOf(minWords, detail.lyricWords)) return null
        if (detail.share < minShare) return null
        if (!(detail.weight - rival > absoluteGap)) return null
        if (relativeMargin > 0f && detail.weight < rival * (1f + relativeMargin)) return null
        return Decision(top.key, rival)
    }

    fun label() = "words>=$minWords share>=$minShare gap>$absoluteGap" + if (relativeMargin > 0f) " lead>=$relativeMargin" else ""

    companion object {
        /** The words path of the lock as the app runs it, but with the rival taken from every song and a tie not counted. */
        val currentLock = DecisionRule(minWords = 3, minShare = 0.35f, absoluteGap = 0f, relativeMargin = 0.25f)

        /** The current speech rule (two words, share 0.35, lead 25 percent) in the same form, for comparison. */
        val current = DecisionRule(minWords = 2, minShare = 0.35f, absoluteGap = 0f, relativeMargin = 0.25f)
    }
}

internal class Decision(
    val song: String,
    val rivalWeight: Float,
)

/** What a rule does over recordings, counting the rule as acting the first time it fires and also looking at the end. */
internal class RuleSlice(
    val name: String,
    val known: Int,
    val unknown: Int,
    /** The FIRST time the rule fires it names the right song, a different song, or any song for a song not in the library. */
    val firstRight: Rate,
    val firstWrongSong: Rate,
    val firstFalse: Rate,
    /** The same, from the evidence at the end of the recording. */
    val finalRight: Rate,
    val finalWrongSong: Rate,
    val finalFalse: Rate,
    /** Final decisions where no other song had any heard word: the lead was not tested at all. */
    val noRivalAtEnd: Int,
)

internal object RuleMetrics {
    fun evaluate(
        name: String,
        results: List<QueryResult>,
        rule: DecisionRule,
    ): RuleSlice {
        var known = 0
        var unknown = 0
        var firstRight = 0
        var firstWrong = 0
        var firstFalse = 0
        var finalRight = 0
        var finalWrong = 0
        var finalFalse = 0
        var noRival = 0
        for (result in results) {
            val target = result.query.song
            if (target == null) unknown++ else known++
            val first = result.speechSteps.firstNotNullOfOrNull { rule.decide(it.state) }
            val last = result.speechSteps.lastOrNull()?.let { rule.decide(it.state) }
            if (last != null && last.rivalWeight <= 0f) noRival++
            if (target == null) {
                if (first != null) firstFalse++
                if (last != null) finalFalse++
            } else {
                if (first?.song == target) {
                    firstRight++
                } else if (first != null) {
                    firstWrong++
                }
                if (last?.song == target) {
                    finalRight++
                } else if (last != null) {
                    finalWrong++
                }
            }
        }
        return RuleSlice(
            name,
            known,
            unknown,
            Rate(firstRight, known),
            Rate(firstWrong, known),
            Rate(firstFalse, unknown),
            Rate(finalRight, known),
            Rate(finalWrong, known),
            Rate(finalFalse, unknown),
            noRival,
        )
    }
}
