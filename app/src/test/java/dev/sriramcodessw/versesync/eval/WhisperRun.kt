package dev.sriramcodessw.versesync.eval

import com.google.gson.Gson
import dev.sriramcodessw.versesync.engine.lyricTokenSet
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Converts the files written by `whisper-cli -oj` for a corpus into transcripts, and writes a table of what was heard
 * in each recording (docs/09 section 16). It is skipped unless both are given:
 *
 *   ./gradlew :app:testDebugUnitTest --tests "*WhisperRun*" -Pversesync.eval.corpus=<dir> -Pversesync.eval.whisper=<dir of <query id>.json> [-Pversesync.eval.label=<name>]
 *
 * The transcripts are written to `<corpus>/transcripts/`, so use a copy of the corpus per model; the replay is then run
 * on that corpus with EvaluationRun, unchanged. The table goes to `build/eval/words-heard-<label>.md`.
 */
class WhisperRun {
    @Test
    fun convertAndCount() {
        val corpusDir = System.getProperty("versesync.eval.corpus")
        val whisperDir = System.getProperty("versesync.eval.whisper")
        assumeTrue(!corpusDir.isNullOrBlank() && !whisperDir.isNullOrBlank(), "no corpus or whisper output given, skipping")
        val corpus = Corpus.load(File(corpusDir))
        val label = System.getProperty("versesync.eval.label") ?: File(whisperDir).name
        val out = File("build/eval").also { it.mkdirs() }
        val transcripts = File(corpus.root, "transcripts").also { it.mkdirs() }
        val lyricWords = corpus.library.associate { it.id to lyricTokenSet(it.lyricText) }

        val rows = StringBuilder()
        rows.appendLine("# What was heard: $label")
        rows.appendLine()
        rows.appendLine("Counts with the app's own word rules. A loop is flagged when one sequence of 3 heard words appears 3 or more times, which a repeated chorus also does; nothing is filtered.")
        rows.appendLine()
        rows.appendLine("| Recording | In library | Entries | Words | Distinct heard | Of them in the lyrics | Loop flagged |")
        rows.appendLine("|---|---|---|---|---|---|---|")
        var missing = 0
        for (query in corpus.queries) {
            val file = File(whisperDir, "${query.id}.json")
            if (!file.isFile) {
                missing++
                rows.appendLine("| ${query.id} | ${if (query.song != null) "yes" else "no"} | no output | | | | |")
                continue
            }
            val heard = WhisperTranscript.toHeard(file.readText(Charsets.UTF_8))
            File(transcripts, "${query.id}.json").writeText(Gson().toJson(heard), Charsets.UTF_8)
            val counts = WhisperTranscript.count(heard, query.song?.let { lyricWords[it] })
            rows.appendLine(
                "| ${query.id} | ${if (query.song != null) "yes" else "no"} | ${counts.entries} | ${counts.words} | ${counts.distinctHeard} | " +
                    "${counts.distinctInLyrics ?: "n/a"} | ${if (counts.loopSuspected) "yes" else "no"} |",
            )
        }
        val table = File(out, "words-heard-$label.md")
        table.writeText(rows.toString(), Charsets.UTF_8)
        println("Converted ${corpus.queries.size - missing} of ${corpus.queries.size} recordings; table written to ${table.absolutePath}")
        assertTrue(table.isFile)
    }
}
