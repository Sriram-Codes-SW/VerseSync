package dev.sriramcodessw.versesync.ui.screens

import dev.sriramcodessw.versesync.data.LockType

/**
 * Every word a person reads about the voice shortcut, in one place, so it can be checked as a whole
 * (LockCopyTest) and cannot drift back into claiming more than the lock does.
 *
 * What the voice shortcut does: it opens when the words of a synced song are heard. It does not know who is
 * speaking, so anyone who knows or has recorded the words can open it. The device lock stays in place as the
 * fallback and is the only protection (ADR-10, docs/10-voice-shortcut-threat-model.md). The stored setting is still
 * [LockType.VOCAL]; only the words shown to a person changed.
 */
object LockCopy {
    const val NAME = "Voice shortcut"

    /** The notice: what it does and, in the same sentence, who it does not keep out. */
    const val DESCRIPTION = "Open by saying or singing words from a synced song. Anyone who knows the words can open it."

    const val LOCK_TYPE_DIALOG_TITLE = "How VerseSync opens"
    const val SETTINGS_SECTION = "App lock"

    const val SCREEN_TITLE = NAME
    const val PROMPT = "Say or sing a few lines of:"
    const val LISTENING = "Listening for the words..."
    const val FALLBACK_BUTTON = "Use device lock"
    const val DEVICE_PROMPT_SUBTITLE = "Use your device lock to open VerseSync."

    /** Which listening try this is, out of the most there are before the device lock takes over. */
    fun tryLabel(
        number: Int,
        of: Int,
    ) = "Try $number of $of"

    /** The name shown in settings for the chosen lock type. */
    fun typeName(type: LockType): String =
        when (type) {
            LockType.VOCAL -> NAME
            else -> type.name.lowercase().replaceFirstChar { it.uppercase() }
        }

    /** Every string above, for the test that checks them. */
    val all =
        listOf(
            NAME,
            DESCRIPTION,
            LOCK_TYPE_DIALOG_TITLE,
            SETTINGS_SECTION,
            SCREEN_TITLE,
            PROMPT,
            LISTENING,
            FALLBACK_BUTTON,
            DEVICE_PROMPT_SUBTITLE,
            tryLabel(1, VocalLockPolicy.MAX_ATTEMPTS),
        )
}
