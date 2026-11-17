package dev.sriramcodessw.versesync.fingerprinting

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import dev.sriramcodessw.versesync.fingerprinting.SyntheticSignals.FRAME
import dev.sriramcodessw.versesync.fingerprinting.SyntheticSignals.frames
import dev.sriramcodessw.versesync.fingerprinting.SyntheticSignals.join
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.math.abs
import kotlin.math.ln
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TC-U-31: what the current pitch detector (TarsosDSP YIN) reports for audio with a known pitch.
 *
 * These tests pin today's behavior, so that moving the detector behind an interface, or replacing
 * it (docs/07-pitch-detection-design.md), can be checked against it. The tolerances are looser
 * than the measured results, which are within 0.2 percent for clean tones.
 */
class PitchCharacterizationTest {
    private val twelveFrames = frames(12)

    private fun cents(
        measured: Float,
        expected: Double,
    ) = 1200 * ln(measured / expected) / ln(2.0)

    @TestFactory
    fun cleanTones_areReportedWithinThirtyCents() =
        listOf(82.0, 110.0, 220.0, 440.0, 880.0, 1000.0).map { hz ->
            DynamicTest.dynamicTest("$hz Hz") {
                PitchTrack.of(SyntheticSignals.tone(hz, twelveFrames)).forEach {
                    assertTrue(abs(cents(it, hz)) < 30, "$hz Hz was reported as $it")
                }
            }
        }

    @Test
    fun pitch_doesNotDependOnLoudness() {
        val quiet = PitchTrack.of(SyntheticSignals.tone(220.0, twelveFrames, amplitude = 0.01))
        val loud = PitchTrack.of(SyntheticSignals.tone(220.0, twelveFrames, amplitude = 0.9))

        quiet.zip(loud).forEach { (q, l) -> assertEquals(l, q, 1.5f) }
    }

    @Test
    fun silence_isUnvoiced() {
        assertTrue(PitchTrack.of(SyntheticSignals.silence(twelveFrames)).all { it == 0f })
    }

    @Test
    fun whiteNoise_isUnvoiced() {
        assertTrue(PitchTrack.of(SyntheticSignals.noise(twelveFrames)).all { it == 0f })
    }

    @Test
    fun silenceBeforeATone_isUnvoicedThenTheToneIsFoundAtOnce() {
        val track = PitchTrack.of(join(SyntheticSignals.silence(frames(4)), SyntheticSignals.tone(330.0, frames(4))))

        assertEquals(List(4) { 0f }, track.take(4))
        track.drop(4).forEach { assertEquals(330f, it, 3f) }
    }

    @Test
    fun anOctaveJump_isFollowedFrameForFrame() {
        val track = PitchTrack.of(join(SyntheticSignals.tone(220.0, frames(6)), SyntheticSignals.tone(440.0, frames(6))))

        track.take(6).forEach { assertEquals(220f, it, 3f) }
        track.drop(6).forEach { assertEquals(440f, it, 5f) }
    }

    @Test
    fun aGlide_risesSteadilyAndStaysInRange() {
        val track = PitchTrack.of(SyntheticSignals.glide(200.0, 300.0, twelveFrames))

        assertTrue(track.zipWithNext().all { (a, b) -> b > a }, "not rising: $track")
        assertTrue(track.first() in 195f..215f && track.last() in 285f..305f, "range was $track")
    }

    @Test
    fun vibrato_staysWithinItsDepthAroundTheCenter() {
        val track = PitchTrack.of(SyntheticSignals.vibrato(220.0, 6.0, 5.0, twelveFrames))

        assertTrue(track.all { it in 205f..235f }, "outside the vibrato range: $track")
        assertEquals(220f, track.average().toFloat(), 6f)
    }

    @Test
    fun aVoiceLikeSound_isReportedAtItsFundamentalNotAnOvertone() {
        PitchTrack.of(SyntheticSignals.voiceLike(150.0, twelveFrames)).forEach { assertEquals(150f, it, 3f) }
    }

    @Test
    fun aTone_survivesModerateNoise() {
        val noisy = SyntheticSignals.mix(SyntheticSignals.tone(220.0, twelveFrames), SyntheticSignals.noise(twelveFrames), 0.3)

        PitchTrack.of(noisy).forEach { assertTrue(abs(cents(it, 220.0)) < 50, "reported $it") }
    }

    @Test
    fun oneValueIsProducedPerFrame_atSixteenKilohertz() {
        // The stored fingerprint relies on this cadence: 1,024 samples at 16,000 Hz is 64 ms.
        assertEquals(12, PitchTrack.of(SyntheticSignals.tone(220.0, twelveFrames)).size)
        assertEquals(11, PitchTrack.of(SyntheticSignals.tone(220.0, twelveFrames - 1)).size)
        assertEquals(15.625, SyntheticSignals.SAMPLE_RATE.toDouble() / FRAME, 1e-9)
    }

    @Test
    fun theFingerprint_collectsEveryFrameButPublishesEveryFourth() {
        // The state flow snapshot is refreshed when the stored list reaches a multiple of four.
        val dispatcher: AudioDispatcher = mock()
        val fingerprinter = AudioFingerprinter(dispatcher)
        fingerprinter.attachPitchProcessor(16000f, FRAME)
        val captor = argumentCaptor<AudioProcessor>()
        verify(dispatcher).addAudioProcessor(captor.capture())
        val signal = SyntheticSignals.tone(220.0, frames(10))

        for (i in 0 until 10) {
            val event = AudioEvent(TarsosDSPAudioFormat(16000f, 16, 1, true, false))
            event.floatBuffer = signal.copyOfRange(i * FRAME, (i + 1) * FRAME)
            captor.firstValue.process(event)
        }

        assertEquals(8, fingerprinter.fingerprint.value.size)
    }
}
