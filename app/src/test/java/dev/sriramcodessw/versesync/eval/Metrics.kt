package dev.sriramcodessw.versesync.eval

import dev.sriramcodessw.versesync.engine.MatchEvidence
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
    val medianLatencyMs: Long?,
)

object Metrics {
    /** Ways to cut the recordings. The first, "all", is everything. */
    val dimensions: List<Pair<String, (Query) -> String>> =
        listOf(
            "all" to { _ -> "all" },
            "language" to { q -> q.languageCode },
            "mode" to { q -> q.modeName },
            "condition" to { q -> q.conditionName },
            "device" to { q -> q.deviceName },
            "speaker group" to { q -> q.speakerGroupName },
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
    ): Slice {
        var inLibrary = 0
        var outOfLibrary = 0
        var found = 0
        var top1 = 0
        var falseAccept = 0
        var lockOpens = 0
        var lockFalseOpens = 0
        var lockFalseTrials = 0
        val latencies = mutableListOf<Long>()

        for (result in results) {
            val outcome = result.byPath.getValue(path)
            val target = result.query.song
            if (target == null) {
                outOfLibrary++
                if (outcome.ranked.isNotEmpty()) falseAccept++
            } else {
                inLibrary++
                if (target in outcome.ranked) {
                    found++
                    outcome.firstSeenMs[target]?.let { latencies += it }
                }
                if (isTop1(target, outcome)) top1++
                if (VocalLockPolicy.opens(outcome.evidence[target])) lockOpens++
            }
            for (song in library) {
                if (song.id == target) continue
                lockFalseTrials++
                if (VocalLockPolicy.opens(outcome.evidence[song.id])) lockFalseOpens++
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
            medianLatencyMs = latencies.sorted().let { if (it.isEmpty()) null else it[it.size / 2] },
        )
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
