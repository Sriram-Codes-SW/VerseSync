package dev.sriramcodessw.versesync.eval

import com.google.gson.Gson
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Gate A of docs/09 section 17: converts the windows written by `tools/audio-test/run_whisper_windows.ps1` into
 * transcripts, then replays the corpus with the speech rule only (pitch is left out; it does not depend on speech and
 * not every recording of the negative set can be read as a WAV). Skipped unless both properties are given:
 *
 *   ./gradlew :app:testDebugUnitTest --tests "*WhisperWindowedRun*" -Pversesync.eval.corpus=<dir> -Pversesync.eval.whisper=<runner output dir> [-Pversesync.eval.label=<name>]
 *
 * It writes `<corpus>/transcripts/`, `build/eval/windows-<label>.md` (what each recording gave) and the usual report.
 */
class WhisperWindowedRun {
    @Test
    fun convertAndReplay() {
        val corpusDir = System.getProperty("versesync.eval.corpus")
        val whisperDir = System.getProperty("versesync.eval.whisper")
        assumeTrue(!corpusDir.isNullOrBlank() && !whisperDir.isNullOrBlank(), "no corpus or runner output given, skipping")
        val corpus = Corpus.load(File(corpusDir))
        val label = System.getProperty("versesync.eval.label") ?: File(whisperDir).name
        val out = File("build/eval").also { it.mkdirs() }
        val transcripts = File(corpus.root, "transcripts").also { it.mkdirs() }

        // windows.csv: id,end_ms,start_ms,silent,wall_seconds
        val byId =
            File(whisperDir, "windows.csv")
                .readLines()
                .drop(1)
                .filter { it.isNotBlank() }
                .map { it.split(",") }
                .groupBy { it[0] }
        val table = StringBuilder()
        table.appendLine("# Live windows: $label")
        table.appendLine()
        table.appendLine("| Recording | Windows | Too quiet | Blank | Loop dropped | Entries | Seconds of audio |")
        table.appendLine("|---|---|---|---|---|---|---|")
        for (query in corpus.queries) {
            val rows = byId[query.id] ?: continue
            val windows =
                rows.map { row ->
                    val endMs = row[1].toLong()
                    val file = File(whisperDir, "${query.id}/w$endMs.json")
                    WhisperWindows.Window(endMs, if (row[3] == "1" || !file.isFile) null else file.readText(Charsets.UTF_8))
                }
            val converted = WhisperWindows.convert(windows)
            File(transcripts, "${query.id}.json").writeText(Gson().toJson(converted.heard), Charsets.UTF_8)
            table.appendLine(
                "| ${query.id} | ${windows.size} | ${converted.silent} | ${converted.blank} | ${converted.loops} | ${converted.heard.size} | " +
                    "${(windows.maxOfOrNull { it.endMs } ?: 0) / 1000} |",
            )
        }
        File(out, "windows-$label.md").writeText(table.toString(), Charsets.UTF_8)

        val results = Replay(corpus, EvalConfig(pitch = false)).run()
        val written = Report.write(corpus, results, label, File(out, "report-$label").also { it.mkdirs() })
        println("Windows table: ${File(out, "windows-$label.md").absolutePath}; report: ${written.markdown.absolutePath}")
        assertTrue(written.markdown.isFile)
    }
}
