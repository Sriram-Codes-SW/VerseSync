package dev.sriramcodessw.versesync.ui.screens

import androidx.compose.runtime.saveable.listSaver

/**
 * Where a person is in the voice shortcut, and what each thing they do leads to, without any screen or microphone
 * so it can be tested (issue #60, ADR-10).
 *
 * An attempt is one stretch of listening. It ends when the person taps the microphone again or [LISTEN_MS] pass,
 * and if it did not open the shortcut it counts as failed. After [VocalLockPolicy.MAX_ATTEMPTS] failed attempts the
 * device lock takes over. The "Use device lock" button goes to the device lock at once and is not an attempt.
 * A match only counts while listening: the recognizer gives a last result after it is stopped, and that must not open.
 */
data class VoiceShortcutAttempts(
    val state: State = State.Idle,
    val failed: Int = 0,
) {
    enum class State { Idle, Listening, DeviceLock, Open }

    /** What the screen or activity must do now. */
    enum class Effect { StartListening, StopListening, PromptDevice }

    class Step(
        val next: VoiceShortcutAttempts,
        val effects: List<Effect> = emptyList(),
    ) {
        /** Does what [effects] say, in order, with whatever starts and stops the microphone and opens the device prompt. */
        fun carryOut(
            startListening: () -> Unit,
            stopListening: () -> Unit,
            promptDevice: () -> Unit,
        ) {
            effects.forEach { effect ->
                when (effect) {
                    Effect.StartListening -> startListening()
                    Effect.StopListening -> stopListening()
                    Effect.PromptDevice -> promptDevice()
                }
            }
        }
    }

    /** The number to show in "Try N of 2". */
    val tryNumber: Int get() = failed + 1

    fun micTapped(): Step =
        when (state) {
            State.Idle -> Step(copy(state = State.Listening), listOf(Effect.StartListening))
            State.Listening -> attemptEnded()
            else -> Step(this)
        }

    fun timedOut(): Step = if (state == State.Listening) attemptEnded() else Step(this)

    /**
     * The app went to the background while listening (issue #62). The microphone stops and the shortcut is idle
     * again, but it is not a failed attempt: the person did not run out of time or say the wrong words.
     */
    fun interrupted(): Step = if (state == State.Listening) Step(copy(state = State.Idle), listOf(Effect.StopListening)) else Step(this)

    fun matched(): Step = if (state == State.Listening) Step(copy(state = State.Open), listOf(Effect.StopListening)) else Step(this)

    fun fallbackTapped(): Step =
        when (state) {
            State.Idle -> Step(copy(state = State.DeviceLock), listOf(Effect.PromptDevice))
            State.Listening -> Step(copy(state = State.DeviceLock), listOf(Effect.StopListening, Effect.PromptDevice))
            else -> Step(this)
        }

    private fun attemptEnded(): Step {
        val failedNow = failed + 1
        return if (VocalLockPolicy.fallsBackToDeviceLock(failedNow)) {
            Step(VoiceShortcutAttempts(State.DeviceLock, failedNow), listOf(Effect.StopListening, Effect.PromptDevice))
        } else {
            Step(VoiceShortcutAttempts(State.Idle, failedNow), listOf(Effect.StopListening))
        }
    }

    companion object {
        /** How long one attempt listens: the proposed rules act at a median of 2.6 s and a 90th percentile of 4.0 s on read speech (docs/09), plus time to start. */
        const val LISTEN_MS = 10_000L

        /**
         * Saved across a rotation as two numbers. Listening and open are not kept: the microphone stops when the screen
         * is left, so a restored screen starts again from idle.
         */
        val Saver =
            listSaver<VoiceShortcutAttempts, Int>(
                save = { listOf(it.state.ordinal, it.failed) },
                restore = {
                    val state = State.entries[it[0]]
                    VoiceShortcutAttempts(if (state == State.Listening || state == State.Open) State.Idle else state, it[1])
                },
            )
    }
}
