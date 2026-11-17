package dev.sriramcodessw.versesync.eval

import dev.sriramcodessw.versesync.engine.MatchEvidence
import dev.sriramcodessw.versesync.engine.SpeechMatcher
import dev.sriramcodessw.versesync.ui.screens.VocalLockPolicy

/*
 * What is measured, for one group of recordings (a "slice") and one evidence path:
 *
 *   found          the right song is among the candidates (recognition shows candidates; a person picks)
 *   top-1          the right song is strictly the strongest candidate; a tie counts as a miss
 *   false accept   a recording of a song that is NOT in the library still produced a candidate
 *   lock opens     the vocal lock would open for the right song (VocalLockPolicy, the real rule)
 *   lock false     the vocal lock would open for a song that was NOT the one sung. Each wrong song of
 *                  each recording is one trial, so these trials are not independent; read the interval as optimistic.
 *   latency        median time until the right song first appeared, among recordings where it was found
 */
class Slice(
    val name: String,
    val inLibrary: Int,
    val outOfLibrary: Int,
    val found: Rate,
    val top1: Rate,
    val falseAccept: Rate,
    val lockOpens: Rate,
    val lockFalse: Rate,
    /** The speech decision (see [Metrics.decision]) names the right song, over songs in the library. */
    val decidesRight: Rate,
    /** The decision names a different song, over songs in the library. */
    val decidesWrongSong: Rate,
    /** The decision names a song for a recording of one that is not in the library. */
    val decidesFalse: Rate,
    val medianLatencyMs: Long?,
)

internal object Metrics {
    /** Ways to cut the recordings. The first, "all", is everything. */
    val dimensions: List<Pair<String, (Query) -> String>> =
        listOf(
            "all" to { _ -> "all" },
            "language" to { q -> q.languageCode },
            "mode" to { q -> q.modeName },
            "condition" to { q -> q.conditionName },
            "device" to { q -> q.deviceName },
            "speaker group" to { q -> q.speakerGroupName },
            "snippet" to { q -> q.snippetName },
        )

    fun slices(
        results: List<QueryResult>,
        path: Path,
        library: List<LibrarySong>,
    ): Map<String, List<Slice>> =
        dimensions.associate { (dimension, key) ->
            dimension to
                results
                    .groupBy { key(it.query) }
                    .toSortedMap()
                    .map { (value, group) -> slice(if (dimension == "all") "all" else "$dimension: $value", group, path, library) }
        }

    fun slice(
        name: String,
        results: List<QueryResult>,
        path: Path,
        library: List<LibrarySong>,
        leadMargin: Float = SpeechMatcher.LEAD_MARGIN,
    ): Slice {
        var inLibrary = 0
        var outOfLibrary = 0
        var found = 0
        var top1 = 0
        var falseAccept = 0
        var lockOpens = 0
        var lockFalseOpens = 0
        var lockFalseTrials = 0
        var decidedRight = 0
        var decidedWrongSong = 0
        var decidedFalse = 0
        val latencies = mutableListOf<Long>()

        for (result in results) {
            val outcome = result.byPath.getValue(path)
            val target = result.query.song
            val decided = decision(outcome, leadMargin)
            if (target == null) {
                outOfLibrary++
                if (outcome.ranked.isNotEmpty()) falseAccept++
                if (decided != null) decidedFalse++
            } else {
                inLibrary++
                if (target in outcome.ranked) {
                    found++
                    outcome.firstSeenMs[target]?.let { latencies += it }
                }
                if (isTop1(target, outcome)) top1++
                if (decided == target) {
                    decidedRight++
                } else if (decided != null) {
                    decidedWrongSong++
                }
                if (VocalLockPolicy.opens(outcome.evidence[target], leadMargin)) lockOpens++
            }
            for (song in library) {
                if (song.id == target) continue
                lockFalseTrials++
                if (VocalLockPolicy.opens(outcome.evidence[song.id], leadMargin)) lockFalseOpens++
            }
        }
        return Slice(
            name = name,
            inLibrary = inLibrary,
            outOfLibrary = outOfLibrary,
            found = Rate(found, inLibrary),
            top1 = Rate(top1, inLibrary),
            falseAccept = Rate(falseAccept, outOfLibrary),
            lockOpens = Rate(lockOpens, inLibrary),
            lockFalse = Rate(lockFalseOpens, lockFalseTrials),
            decidesRight = Rate(decidedRight, inLibrary),
            decidesWrongSong = Rate(decidedWrongSong, inLibrary),
            decidesFalse = Rate(decidedFalse, outOfLibrary),
            medianLatencyMs = latencies.sorted().let { if (it.isEmpty()) null else it[it.size / 2] },
        )
    }

    /**
     * The song the speech evidence settles on, or null when it does not: the strongest candidate, and only if it
     * leads the strongest other candidate by [leadMargin] (issue #44: accept the best only if it beats the runner-up).
     * Pitch-only evidence does not decide.
     */
    fun decision(
        outcome: PathResult,
        leadMargin: Float,
    ): String? {
        val top = outcome.ranked.firstOrNull() ?: return null
        val evidence = outcome.evidence[top] ?: return null
        if (evidence.speechWeight <= 0f) return null
        return if (SpeechMatcher.leads(evidence.speechWeight, evidence.rivalWeight, leadMargin)) top else null
    }

    /** True when [target] has strictly stronger evidence than every other candidate. */
    private fun isTop1(
        target: String,
        outcome: PathResult,
    ): Boolean {
        val mine = outcome.evidence[target] ?: return false
        return outcome.evidence.all { (id, other) -> id == target || stronger(mine, other) }
    }

    /** Rarer heard words win (more of them, and less common in the library); with equal words, a closer pitch wins. */
    private fun stronger(
        a: MatchEvidence,
        b: MatchEvidence,
    ): Boolean = if (a.speechWeight != b.speechWeight) a.speechWeight > b.speechWeight else a.pitchScore < b.pitchScore
}
