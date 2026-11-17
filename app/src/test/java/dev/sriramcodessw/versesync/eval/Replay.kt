package dev.sriramcodessw.versesync.eval

import dev.sriramcodessw.versesync.engine.MatchEvidence
import dev.sriramcodessw.versesync.engine.PitchMatch
import dev.sriramcodessw.versesync.engine.PitchMatcher
import dev.sriramcodessw.versesync.engine.SpeechMatcher
import dev.sriramcodessw.versesync.engine.lyricTokenSet
import dev.sriramcodessw.versesync.engine.spokenMatchTokens
import dev.sriramcodessw.versesync.fingerprinting.PitchTrack

/*
 * Replays a recording through the same rules the app uses to find a song, without a phone.
 *
 * Pitch: the recording goes through the app's own fingerprinter and detector, and is scored against
 * each song every pitch interval, as HybridRecognitionEngine does.
 * Speech: the speech recognizer cannot run on a JVM, so what it heard on a device is captured once
 * (HeardText) and replayed through SpeechMatcher at the frames it was reported.
 *
 * Differences from the running app, so the numbers are not read as more than they are:
 * - the library is read once, so there are no library changes during a query;
 * - the pitch tick is relative to the start of the recording, not to when the user pressed the button;
 * - candidates are those at the end of the recording, as the app holds them.
 */

/** Which evidence a result is built from. */
enum class Path { SPEECH, PITCH, COMBINED }

class EvalConfig(
    val pitchIntervalMs: Long = 5000L,
)

/** The songs found for one recording by one [Path], strongest first, and when each first appeared. */
class PathResult(
    val ranked: List<String>,
    val evidence: Map<String, MatchEvidence>,
    val firstSeenMs: Map<String, Long>,
)

class QueryResult(
    val query: Query,
    val byPath: Map<Path, PathResult>,
    /** What could not be replayed, for example a missing transcript. Shown in the report. */
    val notes: List<String>,
)

class Replay(
    private val corpus: Corpus,
    private val config: EvalConfig = EvalConfig(),
) {
    private val lyricTokens: Map<String, Set<String>> = corpus.library.associate { it.id to lyricTokenSet(it.lyricText) }

    // The stored contour of each song, made from its reference recording as when a user syncs.
    private val contours: Map<String, List<Float>?> =
        corpus.library.associate { song ->
            song.id to song.reference?.let { PitchTrack.of(Wav.read16k(corpus.recording(it))) }
        }

    fun run(): List<QueryResult> = corpus.queries.map { run(it) }

    fun run(query: Query): QueryResult {
        val notes = mutableListOf<String>()
        val speech = replaySpeech(query, notes)
        val pitch = replayPitch(query, notes)
        val combined = combine(speech, pitch)
        return QueryResult(
            query,
            mapOf(
                Path.SPEECH to rank(speech.words, emptyMap(), speech.seen),
                Path.PITCH to rank(emptyMap(), pitch.matches, pitch.seen),
                Path.COMBINED to combined,
            ),
            notes,
        )
    }

    private class SpeechOutcome(
        val words: Map<String, Int>,
        val seen: Map<String, Long>,
    )

    private class PitchOutcome(
        val matches: Map<String, PitchMatch>,
        val seen: Map<String, Long>,
    )

    /** Every piece of heard text, in order, as the engine would search it. */
    private fun replaySpeech(
        query: Query,
        notes: MutableList<String>,
    ): SpeechOutcome {
        val heard = corpus.transcript(query.id)
        if (heard == null) {
            notes += "no transcript, speech not replayed"
            return SpeechOutcome(emptyMap(), emptyMap())
        }
        val words = HashMap<String, Int>()
        val seen = HashMap<String, Long>()
        for (entry in heard.sortedBy { it.frame }) {
            val text = SpeechMatcher.normalize(entry.text, query.languageCode)
            if (text.isBlank()) continue
            val spoken = spokenMatchTokens(text)
            if (spoken.isEmpty()) continue
            for (song in corpus.library) {
                val matched = SpeechMatcher.matchedWords(spoken, lyricTokens.getValue(song.id)) ?: continue
                words.merge(song.id, matched) { old, new -> maxOf(old, new) }
                seen.putIfAbsent(song.id, entry.frame * FRAME_MS)
            }
        }
        return SpeechOutcome(words, seen)
    }

    /** The best three songs under the threshold at each pitch tick; the last tick's are kept, as in the engine. */
    private fun replayPitch(
        query: Query,
        notes: MutableList<String>,
    ): PitchOutcome {
        val file = query.file
        if (file == null) {
            notes += "no recording, pitch not replayed"
            return PitchOutcome(emptyMap(), emptyMap())
        }
        val track = PitchTrack.of(Wav.read16k(corpus.recording(file)))
        val threshold = PitchMatcher.threshold(query.languageCode)
        val seen = HashMap<String, Long>()
        var latest: Map<String, PitchMatch> = emptyMap()
        var tickMs = config.pitchIntervalMs
        while (tickMs / FRAME_MS <= track.size) {
            // The engine reads a snapshot that is refreshed every fourth frame.
            val frames = (tickMs / FRAME_MS).toInt() / SNAPSHOT_EVERY * SNAPSHOT_EVERY
            latest =
                if (frames < MIN_LIVE_FRAMES) {
                    emptyMap()
                } else {
                    val live = track.subList(0, frames)
                    corpus.library
                        .mapNotNull { song ->
                            val match = PitchMatcher.match(contours[song.id], live)
                            if (match.score < threshold) song.id to match else null
                        }.sortedWith(compareBy({ it.second.score }, { it.first }))
                        .take(TOP_PITCH)
                        .toMap()
                }
            latest.keys.forEach { seen.putIfAbsent(it, tickMs) }
            tickMs += config.pitchIntervalMs
        }
        return PitchOutcome(latest, seen)
    }

    private fun combine(
        speech: SpeechOutcome,
        pitch: PitchOutcome,
    ): PathResult {
        val seen = HashMap<String, Long>(speech.seen)
        pitch.seen.forEach { (id, ms) -> seen.merge(id, ms) { a, b -> minOf(a, b) } }
        return rank(speech.words, pitch.matches, seen)
    }

    /** Candidates are the songs heard in speech plus the pitch matches, ordered by evidence. */
    private fun rank(
        words: Map<String, Int>,
        pitch: Map<String, PitchMatch>,
        seen: Map<String, Long>,
    ): PathResult {
        val ids = (words.keys + pitch.keys)
        val evidence =
            ids.associateWith { id ->
                val match = pitch[id]
                MatchEvidence(
                    speechWords = words[id] ?: 0,
                    pitchScore = match?.score ?: Float.MAX_VALUE,
                    pitchOverlap = match?.overlap ?: 0,
                )
            }
        val ranked =
            ids.sortedWith(
                compareByDescending<String> { evidence.getValue(it).speechWords }
                    .thenBy { evidence.getValue(it).pitchScore }
                    .thenBy { it },
            )
        return PathResult(ranked, evidence, seen.filterKeys { it in evidence })
    }

    companion object {
        /** Milliseconds of audio in one 1,024-sample frame at 16 kHz. */
        const val FRAME_MS = 64L
        private const val SNAPSHOT_EVERY = 4
        private const val MIN_LIVE_FRAMES = 10
        private const val TOP_PITCH = 3
    }
}
