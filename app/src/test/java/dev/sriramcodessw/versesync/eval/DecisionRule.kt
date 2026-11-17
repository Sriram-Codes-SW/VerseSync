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

/**
 * How long a decision has to hold before the rule acts on it, and which evidence it looks at. A garbled early piece of
 * heard text can make a rule fire that later text would have contradicted; waiting trades speed for fewer false decisions.
 */
internal data class Settle(
    /** The same song must be decided continuously for this many 64 ms frames of audio before the rule acts. 0 acts at once. */
    val holdFrames: Int = 0,
    /** Use evidence that can be taken back (finished utterances plus the current partial) instead of evidence that only grows. */
    val retractable: Boolean = false,
) {
    fun label() = "hold ${holdFrames * Replay.FRAME_MS} ms" + if (retractable) ", retractable" else ", sticky"
}

/** The song a rule acted on, and the frame at which it did. */
internal class Firing(
    val song: String,
    val frame: Int,
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
    /** Seconds of audio until the rule first acted on the right song, over recordings where it did: median and 90th percentile. */
    val medianSecondsToRight: Double? = null,
    val p90SecondsToRight: Double? = null,
)

internal object RuleMetrics {
    /**
     * When [rule], held for [settle], first acts on [steps]. A decision holds from the update that makes it until an update
     * that decides something else or nothing, or the end. The updates at the last reported frame are not counted: the
     * capture ends with a final result that a live recognizer only gives when it is stopped, and the app would not
     * have it while the person is still singing.
     */
    fun firing(
        steps: List<SpeechStep>,
        rule: DecisionRule,
        settle: Settle = Settle(),
    ): Firing? {
        if (steps.isEmpty()) return null
        val end = steps.last().frame
        var i = 0
        while (i < steps.size) {
            val decision = rule.decide(steps[i].state)
            if (decision == null) {
                i++
                continue
            }
            val start = steps[i].frame
            var j = i + 1
            while (j < steps.size && rule.decide(steps[j].state)?.song == decision.song) j++
            val holdsUntil = if (j < steps.size) steps[j].frame else end
            val actsAt = start + settle.holdFrames
            if (actsAt < holdsUntil && actsAt < end) return Firing(decision.song, actsAt)
            i = j
        }
        return null
    }

    private fun stepsOf(
        result: QueryResult,
        settle: Settle,
    ) = if (settle.retractable) result.retractableSteps else result.speechSteps

    fun evaluate(
        name: String,
        results: List<QueryResult>,
        rule: DecisionRule,
        settle: Settle = Settle(),
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
        val secondsToRight = mutableListOf<Double>()
        for (result in results) {
            val target = result.query.song
            if (target == null) unknown++ else known++
            val steps = stepsOf(result, settle)
            val first = firing(steps, rule, settle)
            val last = steps.lastOrNull()?.let { rule.decide(it.state) }
            if (last != null && last.rivalWeight <= 0f) noRival++
            if (target == null) {
                if (first != null) firstFalse++
                if (last != null) finalFalse++
            } else {
                if (first?.song == target) {
                    firstRight++
                    secondsToRight += first.frame * Replay.FRAME_MS / 1000.0
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
        val sorted = secondsToRight.sorted()
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
            sorted.getOrNull(sorted.size / 2),
            sorted.getOrNull(((sorted.size - 1) * 0.9).toInt()),
        )
    }
}
