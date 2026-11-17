package dev.sriramcodessw.versesync.engine

import dev.sriramcodessw.versesync.fingerprinting.AudioFingerprinter
import kotlin.math.abs
import kotlin.math.ln

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

/**
 * The knobs of the speech rules, in one place so the evaluation harness can sweep them (docs/09-evaluation-harness.md).
 * The defaults are what the app runs.
 */
internal data class SpeechRules(
    /** Share of the heard words that must appear in a song's lyrics for it to be a candidate. */
    val shareThreshold: Float = SpeechMatcher.SHARE_THRESHOLD,
    /**
     * Different heard words that must be found in a song's lyrics. Before, one word was enough: the
     * score is the share of heard words found in the lyrics, so a single heard word in the lyrics scored 1.0.
     * A song with fewer different words than this needs all of them.
     */
    val minWords: Int = SpeechMatcher.MIN_MATCHED_WORDS,
    /** A heard word one or two letters off a lyric word counts as that word, for garbled text. Long words only. */
    val fuzzy: Boolean = false,
    /** How far ahead of the strongest other song a song must be to count as the clear leader (a fraction). */
    val leadMargin: Float = SpeechMatcher.LEAD_MARGIN,
)

/** How strongly one song matched some heard words: how many different ones, and how rare they are in the library. */
internal data class SpeechScore(
    val words: Int,
    val weight: Float,
)

/** The tokens of one song's lyrics, with what is needed to find a heard word that is a little off. */
internal class LyricIndex(
    val tokens: Set<String>,
) {
    private val byLength: Map<Int, List<String>> by lazy { tokens.groupBy { it.length } }

    /** True if [word] is not in the lyrics exactly but is within a letter or two of a word that is. */
    fun nearMiss(word: String): Boolean {
        if (word.length < FUZZY_MIN_LENGTH) return false
        val allowed = if (word.length >= FUZZY_LONG_LENGTH) 2 else 1
        for (length in (word.length - allowed)..(word.length + allowed)) {
            if (byLength[length]?.any { editDistanceAtMost(word, it, allowed) } == true) return true
        }
        return false
    }

    private companion object {
        const val FUZZY_MIN_LENGTH = 5
        const val FUZZY_LONG_LENGTH = 9
    }
}

/** True if [a] can be turned into [b] with at most [max] single-letter edits. Cheap, because [max] is 1 or 2. */
internal fun editDistanceAtMost(
    a: String,
    b: String,
    max: Int,
): Boolean {
    if (kotlin.math.abs(a.length - b.length) > max) return false
    var previous = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val current = IntArray(b.length + 1)
        current[0] = i
        var rowMin = current[0]
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            rowMin = minOf(rowMin, current[j])
        }
        if (rowMin > max) return false
        previous = current
    }
    return previous[b.length] <= max
}

/**
 * A library, ready to score heard words against every song at once. Songs are keyed by [K], and each
 * word is weighted by how few songs contain it, so a word every song has, such as "love", counts for little.
 */
internal class SpeechLibrary<K>(
    tokenSets: Map<K, Set<String>>,
    private val rules: SpeechRules = SpeechRules(),
) {
    private val indexes: Map<K, LyricIndex> = tokenSets.mapValues { LyricIndex(it.value) }
    private val songs = tokenSets.size

    // In how many songs each word appears.
    private val documentFrequency: Map<String, Int> =
        HashMap<String, Int>().also { counts -> tokenSets.values.forEach { tokens -> tokens.forEach { counts.merge(it, 1, Int::plus) } } }

    /** The weight of a heard word: higher the fewer songs have it. A word in no song exactly (a near miss) weighs the most. */
    private fun weight(word: String): Float = ln(1f + songs.toFloat() / maxOf(1, documentFrequency[word] ?: 0))

    /** The score of every song that is a candidate for [spoken] (from [spokenMatchTokens]); the others are left out. */
    fun scoreAll(spoken: List<String>): Map<K, SpeechScore> {
        if (spoken.isEmpty()) return emptyMap()
        val result = HashMap<K, SpeechScore>()
        for ((key, index) in indexes) {
            val tokens = index.tokens
            if (tokens.isEmpty()) continue
            var heardInLyrics = 0
            val matched = HashSet<String>()
            for (word in spoken) {
                if (word in tokens || (rules.fuzzy && index.nearMiss(word))) {
                    heardInLyrics++
                    matched += word
                }
            }
            if (heardInLyrics.toFloat() / spoken.size < rules.shareThreshold) continue
            if (matched.size < minOf(rules.minWords, tokens.size)) continue
            result[key] = SpeechScore(matched.size, matched.sumOf { weight(it).toDouble() }.toFloat())
        }
        return result
    }
}

internal object SpeechMatcher {
    /** Share of the spoken words that must appear in a song's lyrics for it to be a candidate. */
    const val SHARE_THRESHOLD = 0.35f

    /** Different heard words that must be found in a song's lyrics (see [SpeechRules.minWords]). */
    const val MIN_MATCHED_WORDS = 2

    /**
     * A song leads when its score is at least this much more than the strongest other song's. Close or
     * equal scores mean the speech cannot tell the songs apart, and music with a few common words shared
     * between songs tied them in a trial (issue #44). Provisional: it is to be set from the evaluation set.
     */
    const val LEAD_MARGIN = 0.25f

    /**
     * Normalizes Devanagari text to improve Sanskrit matching accuracy when using the Hindi model.
     * Other languages are returned as heard.
     */
    fun normalize(
        text: String,
        languageCode: String,
    ): String {
        if (languageCode != "sa") return text
        return text
            .replace("़", "") // Remove Nukta (Hindi specific)
            .replace("v", "b") // Sanskrit 'v' often heard as 'b' by Hindi models
            .trim()
    }

    /**
     * How many different heard words ([spoken], from [spokenMatchTokens]) are found in the lyrics
     * ([lyricTokens]), or null if the song is not a candidate for this speech.
     */
    fun matchedWords(
        spoken: List<String>,
        lyricTokens: Set<String>,
    ): Int? = SpeechLibrary(mapOf(0 to lyricTokens)).scoreAll(spoken)[0]?.words

    /**
     * For each song, the strongest score among the OTHER songs, for [leads]. A song alone has none.
     */
    fun <K> rivalWeights(weights: Map<K, Float>): Map<K, Float> {
        if (weights.size < 2) return weights.mapValues { 0f }
        val sorted = weights.values.sortedDescending()
        return weights.mapValues { (_, own) -> if (own >= sorted[0]) sorted[1] else sorted[0] }
    }

    /** Whether [weight] is clearly ahead of [rival], the strongest other song. No rival means it leads. */
    fun leads(
        weight: Float,
        rival: Float,
        margin: Float = LEAD_MARGIN,
    ): Boolean = rival <= 0f || weight >= rival * (1f + margin)
}
