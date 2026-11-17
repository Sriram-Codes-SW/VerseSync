package dev.sriramcodessw.versesync.engine

import dev.sriramcodessw.versesync.fingerprinting.AudioFingerprinter
import kotlin.math.abs

// The rules that decide whether heard speech or a sung contour matches a song, as plain functions.
//
// The recognition engine uses them, and so does the evaluation harness (docs/08-quality-roadmap.md),
// so a measurement made offline is a measurement of the rules the app actually runs.

/** A pitch distance and how many frames it was measured over. */
internal data class PitchMatch(
    val score: Float,
    val overlap: Int,
)

internal object PitchMatcher {
    /** Minimum frames voiced in both contours before a pitch distance is trusted. */
    const val MIN_VOICED_OVERLAP = 8

    /** A pitch match scores below this many semitones. It was 3.0, which let a different spoken clip through. */
    const val THRESHOLD = 2.5f

    /** Sanskrit is heard through a Hindi model and sung more freely, so it gets a little more room. */
    const val THRESHOLD_SANSKRIT = 3.0f

    fun threshold(languageCode: String): Float = if (languageCode == "sa") THRESHOLD_SANSKRIT else THRESHOLD

    /** The best pitch distance of [live] against [reference], and the voiced frames it was measured over. */
    fun match(
        reference: List<Float>?,
        live: List<Float>,
    ): PitchMatch {
        val none = PitchMatch(Float.MAX_VALUE, 0)
        if (reference == null || reference.isEmpty() || live.isEmpty()) return none

        val refContour = AudioFingerprinter.toRelativeSemitoneContour(reference)
        val liveContour = AudioFingerprinter.toRelativeSemitoneContour(live)
        if (refContour.isEmpty() || liveContour.isEmpty()) return none

        var best = none
        val windowSize = liveContour.size.coerceAtMost(refContour.size)
        if (windowSize < 8) return none
        val liveSegment = liveContour.takeLast(windowSize)

        val step = 5.coerceAtMost((refContour.size - windowSize).coerceAtLeast(1))
        for (i in 0..(refContour.size - windowSize) step step) {
            val subRef = refContour.subList(i, i + windowSize)
            val match = distance(subRef, liveSegment)
            if (match.score < best.score) best = match
        }
        return best
    }

    /**
     * Mean absolute semitone difference over frames that are voiced in BOTH contours (ADR-04).
     * A frame that is silent in only one contour is skipped rather than compared against 0,
     * and too little shared voiced material is treated as no match.
     */
    private fun distance(
        list1: List<Float>,
        list2: List<Float>,
    ): PitchMatch {
        var count = 0
        var sum = 0.0
        for (i in list1.indices) {
            val a = list1[i]
            val b = list2[i]
            if (!a.isNaN() && !b.isNaN()) {
                sum += abs(a - b)
                count++
            }
        }
        return if (count >= MIN_VOICED_OVERLAP) PitchMatch((sum / count).toFloat(), count) else PitchMatch(Float.MAX_VALUE, count)
    }
}

internal object SpeechMatcher {
    /** Share of the spoken words that must appear in a song's lyrics for it to be a candidate. */
    const val SHARE_THRESHOLD = 0.35f

    /**
     * Different words that must be heard and found in a song's lyrics. Before, one word was enough: the
     * score is the share of heard words found in the lyrics, so a single heard word in the lyrics scored 1.0.
     * A song with fewer different words than this needs all of them.
     */
    const val MIN_MATCHED_WORDS = 2

    /**
     * How many different heard words ([spoken], from [spokenMatchTokens]) are found in the lyrics
     * ([lyricTokens]), or null if the song is not a candidate for this speech.
     */
    fun matchedWords(
        spoken: List<String>,
        lyricTokens: Set<String>,
    ): Int? {
        if (tokenMatchScore(spoken, lyricTokens) < SHARE_THRESHOLD) return null
        // The share alone is not enough evidence: one heard word that is in the lyrics scores 1.0.
        val words = matchedTokenCount(spoken, lyricTokens)
        return if (words >= minOf(MIN_MATCHED_WORDS, lyricTokens.size)) words else null
    }
}
