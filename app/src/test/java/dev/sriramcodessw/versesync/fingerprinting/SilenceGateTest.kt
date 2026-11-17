package dev.sriramcodessw.versesync.fingerprinting

import dev.sriramcodessw.versesync.audio.AudioFrame
import dev.sriramcodessw.versesync.audio.FixedPitchDetector
import dev.sriramcodessw.versesync.audio.tarsos.TarsosPitchDetector
import org.junit.jupiter.api.Test
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TC-U-37: frames too quiet to be a voice are not given a pitch (issue #33). */
class SilenceGateTest {
    private val frames = SyntheticSignals.frames(12)

    /** A pure tone whose RMS level is [db] dB relative to full scale. */
    private fun toneAt(
        db: Double,
        hz: Double = 470.0,
    ): FloatArray {
        val amplitude = sqrt(2.0) * Math.pow(10.0, db / 20.0)
        return SyntheticSignals.tone(hz, frames, amplitude)
    }

    @Test
    fun theLibraryAloneReportsAFaintHumAsVoiced_whichIsWhyTheGateExists() {
        // The emulator microphone's hum is about -73 dB. Without the gate it becomes a voiced tone.
        val hum = toneAt(-73.0)
        val detector = TarsosPitchDetector()

        val track = (0 until 12).map { detector.detect(AudioFrame(hum.copyOfRange(it * 1024, (it + 1) * 1024), 16000, it.toLong())).hz }

        assertTrue(track.all { it > 400f }, "expected the bare detector to report the hum: $track")
    }

    @Test
    fun aFaintHum_isUnvoiced() {
        assertTrue(PitchTrack.of(toneAt(-73.0)).all { it == 0f })
    }

    @Test
    fun aHumRecordedIntoAFingerprint_leavesItEmpty() {
        val fingerprinter = AudioFingerprinter(TarsosPitchDetector())
        val hum = toneAt(-73.0)

        for (i in 0 until 12) {
            fingerprinter.onFrame(AudioFrame(hum.copyOfRange(i * 1024, (i + 1) * 1024), 16000, i.toLong()))
        }

        assertEquals(List(12) { 0f }, fingerprinter.fingerprint.value)
    }

    @Test
    fun quietSpeechLevel_isStillVoiced() {
        // Quiet speech is around -40 dB or louder.
        val track = PitchTrack.of(toneAt(-40.0, hz = 180.0))

        assertTrue(track.all { it in 170f..190f }, "quiet speech level lost its pitch: $track")
    }

    @Test
    fun theThresholdIsBetweenTheHumAndQuietSpeech() {
        assertTrue(AudioLevel.SILENCE_DB < -45f, "too high: quiet singing could be cut")
        assertTrue(AudioLevel.SILENCE_DB > -70f, "too low: a faint hum would pass")
    }

    @Test
    fun justBelowTheThresholdIsSilentAndJustAboveIsNot() {
        assertTrue(AudioLevel.isSilent(toneAt(AudioLevel.SILENCE_DB - 1.0)))
        assertFalse(AudioLevel.isSilent(toneAt(AudioLevel.SILENCE_DB + 1.0)))
    }

    @Test
    fun emptyOrInvalidInput_isSilent() {
        assertTrue(AudioLevel.isSilent(FloatArray(0)))
        assertTrue(AudioLevel.isSilent(FloatArray(1024)))
        assertTrue(AudioLevel.isSilent(floatArrayOf(Float.NaN, 0.5f)))
    }

    @Test
    fun aSilentFrame_doesNotReachTheDetector() {
        val detector = FixedPitchDetector(220f)
        val fingerprinter = AudioFingerprinter(detector)

        fingerprinter.onFrame(AudioFrame(FloatArray(1024), 16000, 0))

        assertEquals(0, detector.frames)
        assertEquals(0f, fingerprinter.instantPitch.value)
    }

    @Test
    fun aLoudFrame_reachesTheDetector() {
        val detector = FixedPitchDetector(220f)
        val fingerprinter = AudioFingerprinter(detector)

        fingerprinter.onFrame(AudioFrame(SyntheticSignals.tone(220.0, 1024), 16000, 0))

        assertEquals(1, detector.frames)
        assertEquals(220f, fingerprinter.instantPitch.value)
    }

    @Test
    fun speechThenSilence_keepsTheSpeechAndDropsTheTail() {
        val signal =
            SyntheticSignals.join(
                SyntheticSignals.tone(220.0, SyntheticSignals.frames(6)),
                toneAt(-73.0).copyOf(SyntheticSignals.frames(6)),
            )

        val track = PitchTrack.of(signal)

        assertTrue(track.take(6).all { it in 215f..225f })
        assertTrue(track.drop(6).all { it == 0f })
    }
}
