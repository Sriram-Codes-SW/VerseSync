package dev.sriramcodessw.versesync.stt

import kotlinx.coroutines.flow.StateFlow

/**
 * What the recognition engine needs from a speech-to-text recognizer, so that the engine does not depend on which one is
 * used underneath (docs/09-evaluation-harness.md section 17). [VoskVoiceRecognition] is the only one today.
 *
 * The text is the latest thing heard, as one string: a new value is a new piece of speech, an unchanged value is not
 * reported again, and a blank value is never reported.
 */
interface SpeechRecognizer {
    /** True once a model for the loaded language can be used. */
    val isModelReady: StateFlow<Boolean>

    /** The latest text heard, or an empty string when nothing was heard or after [stopListening]. */
    val recognitionResult: StateFlow<String>

    /** Starts loading what is needed to recognize [languageCode]; [isModelReady] turns true when it is usable. */
    fun loadModel(languageCode: String)

    /** Starts listening. Does nothing when no model is ready. */
    fun startListening()

    /** Stops listening and clears the text. Safe to call from any thread, more than once, and before [startListening]. */
    fun stopListening()

    /** Stops listening and frees everything held, for good. The object is not used after this (issue #62). */
    fun release()
}
