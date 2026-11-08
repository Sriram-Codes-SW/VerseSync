package dev.sriramcodessw.versesync.engine

import java.text.Normalizer

/** One comparable unit of text. [cjk] tokens come from scripts written without word spaces. */
internal data class LyricToken(
    val text: String,
    val cjk: Boolean,
)

/** Words shorter than this are ignored when scoring speech, as before. CJK tokens are exempt. */
private const val MIN_WORD_LENGTH = 3

// Keep letters, combining marks and digits. Marks matter because the vowel signs of Indic scripts
// are combining marks. Dropping them used to cut words into one or two letter fragments.
private val NON_WORD = Regex("[^\\p{L}\\p{M}\\p{Nd}\\s]")

// Marks that speech models and printed lyrics disagree about, removed from both sides:
// Arabic tatweel and short-vowel marks, the Devanagari nukta, zero-width space and joiner.
private val IGNORED_MARKS = Regex("[\\u0640\\u064B-\\u065F\\u0670\\u093C\\u200B\\u200D]")

private fun isCjk(codePoint: Int): Boolean =
    when (Character.UnicodeScript.of(codePoint)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        -> true

        else -> false
    }

/**
 * Splits [text] into tokens that compare the same whether they came from printed lyrics or from a
 * speech model.
 *
 * - Text is lowercased and put in canonical composed form, so decomposed input matches.
 * - Punctuation becomes a separator, but combining marks stay inside their word.
 * - Chinese and Japanese are written without word spaces and speech models insert spaces at
 *   different places than a lyric sheet does. Runs of those characters, ignoring spaces inside
 *   them, become overlapping pairs of characters so both sides produce the same tokens.
 */
internal fun tokenizeLyrics(text: String): List<LyricToken> {
    val clean =
        Normalizer
            .normalize(text.lowercase(), Normalizer.Form.NFC)
            .replace(IGNORED_MARKS, "")
            .replace(NON_WORD, " ")

    val tokens = mutableListOf<LyricToken>()
    val word = StringBuilder()
    val cjkRun = mutableListOf<String>()

    fun flushWord() {
        if (word.isNotEmpty()) tokens += LyricToken(word.toString(), cjk = false)
        word.setLength(0)
    }

    fun flushCjk() {
        when {
            cjkRun.size == 1 -> tokens += LyricToken(cjkRun[0], cjk = true)

            cjkRun.size > 1 -> for (i in 0 until cjkRun.size - 1) {
                tokens += LyricToken(cjkRun[i] + cjkRun[i + 1], cjk = true)
            }
        }
        cjkRun.clear()
    }

    var i = 0
    while (i < clean.length) {
        val cp = clean.codePointAt(i)
        val len = Character.charCount(cp)
        when {
            Character.isWhitespace(cp) -> {
                flushWord()
            }

            // a CJK run continues across spaces
            isCjk(cp) -> {
                flushWord()
                cjkRun += clean.substring(i, i + len)
            }

            else -> {
                flushCjk()
                word.appendCodePoint(cp)
            }
        }
        i += len
    }
    flushWord()
    flushCjk()
    return tokens
}

/**
 * Fraction of the spoken tokens that also appear in the lyrics, from 0 to 1.
 * Short words are ignored on the spoken side, as they are too common to identify a song.
 */
internal fun lyricMatchScore(
    spoken: String,
    lyrics: String,
): Float = tokenMatchScore(spokenMatchTokens(spoken), lyricTokenSet(lyrics))

/**
 * The tokens of speech that take part in scoring. Short words are dropped, because they are too
 * common to identify a song. Computed once per speech text, not once per song.
 */
internal fun spokenMatchTokens(spoken: String): List<String> = tokenizeLyrics(spoken).filter { it.cjk || it.text.length >= MIN_WORD_LENGTH }.map { it.text }

/**
 * Every token of a song's lyrics. Tokenizing is the expensive part of matching, so callers that
 * search a whole library on every speech update keep this per song and reuse it.
 */
internal fun lyricTokenSet(lyrics: String): Set<String> = tokenizeLyrics(lyrics).map { it.text }.toSet()

/** Fraction of [spokenTokens] found in [lyricTokens], from 0 to 1. */
internal fun tokenMatchScore(
    spokenTokens: List<String>,
    lyricTokens: Set<String>,
): Float {
    if (spokenTokens.isEmpty() || lyricTokens.isEmpty()) return 0f
    return spokenTokens.count { it in lyricTokens }.toFloat() / spokenTokens.size
}
