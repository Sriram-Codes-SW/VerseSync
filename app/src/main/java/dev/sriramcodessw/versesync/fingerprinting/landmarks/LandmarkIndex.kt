package dev.sriramcodessw.versesync.fingerprinting.landmarks

/** How strongly the best song matched a query, and how strongly the best other song did. */
class LandmarkScores(
    val song: Int?,
    val votes: Int,
    val rivalVotes: Int,
    /** Frames from the start of the query to the start of the stored print, at the winning offset. */
    val offsetFrames: Int,
) {
    /** The song, if it has at least [minVotes] votes and [ratio] times the best other song's votes. */
    fun accepted(
        minVotes: Int,
        ratio: Float,
    ): Int? = if (song != null && votes >= minVotes && votes >= ratio * rivalVotes) song else null
}

/**
 * The prints of a library, searched by hash. Hashes live in sorted primitive arrays, not boxed maps. A hash that many
 * songs share says little, so a hash with more than [MAX_POSTINGS] entries is skipped.
 */
class LandmarkIndex(
    prints: Map<Int, LandmarkPrint>,
) {
    private val songIds: IntArray
    private val hashes: IntArray
    private val songs: IntArray
    private val frames: IntArray

    val size: Int get() = hashes.size

    init {
        val ids = prints.keys.sorted()
        songIds = ids.toIntArray()
        val total = ids.sumOf { prints.getValue(it).size }
        val allSongs = IntArray(total)
        val allFrames = IntArray(total)
        val keys = LongArray(total)
        var at = 0
        ids.forEachIndexed { index, id ->
            val print = prints.getValue(id)
            for (i in 0 until print.size) {
                allSongs[at] = index
                allFrames[at] = print.frames[i]
                keys[at] = (print.hashes[i].toLong() shl 32) or at.toLong()
                at++
            }
        }
        keys.sort()
        hashes = IntArray(total) { (keys[it] shr 32).toInt() }
        songs = IntArray(total) { allSongs[keys[it].toInt()] }
        frames = IntArray(total) { allFrames[keys[it].toInt()] }
    }

    private fun lowerBound(hash: Int): Int {
        var lo = 0
        var hi = hashes.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (hashes[mid] < hash) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** Votes for every song, then the best two. Offsets within one frame of each other count together. */
    fun scores(query: LandmarkPrint): LandmarkScores {
        var votes = LongArray(1024)
        var count = 0
        for (q in 0 until query.size) {
            var k = lowerBound(query.hashes[q])
            val end = k + countEqual(k, query.hashes[q])
            if (end - k > MAX_POSTINGS) continue
            while (k < end) {
                if (count == votes.size) votes = votes.copyOf(votes.size * 2)
                val offset = frames[k] - query.frames[q] + OFFSET_BIAS
                votes[count++] = (songs[k].toLong() shl 32) or offset.toLong()
                k++
            }
        }
        if (count == 0) return LandmarkScores(null, 0, 0, 0)
        votes.sort(0, count)

        // Runs of equal (song, offset), with the count of each; adjacent offsets of one song are neighbors.
        val runKey = LongArray(count)
        val runCount = IntArray(count)
        var runs = 0
        var i = 0
        while (i < count) {
            var j = i
            while (j < count && votes[j] == votes[i]) j++
            runKey[runs] = votes[i]
            runCount[runs] = j - i
            runs++
            i = j
        }
        val bestVotes = IntArray(songIds.size)
        val bestOffset = IntArray(songIds.size)
        for (r in 0 until runs) {
            val song = (runKey[r] shr 32).toInt()
            var sum = runCount[r]
            if (r > 0 && runKey[r - 1] == runKey[r] - 1) sum += runCount[r - 1]
            if (r + 1 < runs && runKey[r + 1] == runKey[r] + 1) sum += runCount[r + 1]
            if (sum > bestVotes[song]) {
                bestVotes[song] = sum
                bestOffset[song] = (runKey[r] and 0xFFFFFFFFL).toInt() - OFFSET_BIAS
            }
        }
        var top = -1
        var second = 0
        for (s in songIds.indices) {
            if (top == -1 || bestVotes[s] > bestVotes[top]) {
                if (top != -1) second = maxOf(second, bestVotes[top])
                top = s
            } else {
                second = maxOf(second, bestVotes[s])
            }
        }
        return LandmarkScores(songIds[top], bestVotes[top], second, bestOffset[top])
    }

    private fun countEqual(
        from: Int,
        hash: Int,
    ): Int {
        var n = 0
        while (from + n < hashes.size && hashes[from + n] == hash) n++
        return n
    }

    /** The song recognized, or null. */
    fun match(
        query: LandmarkPrint,
        minVotes: Int,
        ratio: Float,
    ): Int? = scores(query).accepted(minVotes, ratio)

    companion object {
        const val MAX_POSTINGS = 2000

        /** Added to an offset (which can be negative) so it fits the low 32 bits of a vote. */
        private const val OFFSET_BIAS = 1 shl 24
    }
}
