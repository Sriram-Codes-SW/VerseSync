package dev.sriramcodessw.versesync.data

/**
 * Languages the recognition screen offers: those with a downloaded speech model, plus those no
 * speech model exists for. The latter can only be recognized by pitch, and a user whose songs are
 * all in them has no model to download, so they must not be hidden.
 */
fun recognitionLanguages(
    all: List<LanguageModelInfo>,
    installed: Set<String>,
): List<LanguageModelInfo> = all.filter { installed.contains(it.code) || !it.hasSpeechModel }

/** The language selected first: the first installed one, else the first language in [all]. */
fun defaultRecognitionLanguage(
    all: List<LanguageModelInfo>,
    installed: Set<String>,
): LanguageModelInfo = all.firstOrNull { installed.contains(it.code) } ?: all[0]
