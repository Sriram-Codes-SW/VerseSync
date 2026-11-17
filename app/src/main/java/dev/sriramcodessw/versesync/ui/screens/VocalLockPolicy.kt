package dev.sriramcodessw.versesync.ui.screens

import dev.sriramcodessw.versesync.engine.MatchEvidence
import dev.sriramcodessw.versesync.engine.SpeechMatcher

/**
 * What the vocal lock needs before it opens, and what happens when it is given up on.
 *
 * Recognizing a song only needs it to be a plausible candidate, and a wrong candidate there is
 * harmless. The lock protects the library, so it asks for much more evidence.
 */
object VocalLockPolicy {
    /** How many times the vocal lock can be given up on before the device lock is required. */
    const val MAX_ATTEMPTS = 2

    /** Different words of the verse that must be heard. One word, or two, can be heard by chance. */
    const val MIN_WORDS = 3

    /** Or the voice must follow the verse's pitch this closely, in semitones... */
    const val MAX_PITCH_SCORE = 2.0f

    /** ...over at least this many frames sung in both, about two seconds. */
    const val MIN_PITCH_OVERLAP = 30

    /** True once [failedAttempts] attempts have been given up, and the device lock must take over. */
    fun fallsBackToDeviceLock(failedAttempts: Int): Boolean = failedAttempts >= MAX_ATTEMPTS

    /** Whether [evidence] for the target song is enough to open the lock. */
    fun opens(evidence: MatchEvidence?): Boolean {
        if (evidence == null) return false
        // Enough words, and no other song close behind: the words must tell this song apart from the rest (issue #44).
        val heardEnoughWords = evidence.speechWords >= MIN_WORDS && SpeechMatcher.leads(evidence.speechWeight, evidence.rivalWeight)
        val followedThePitch = evidence.pitchScore <= MAX_PITCH_SCORE && evidence.pitchOverlap >= MIN_PITCH_OVERLAP
        return heardEnoughWords || followedThePitch
    }
}
