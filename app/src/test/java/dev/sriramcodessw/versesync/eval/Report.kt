package dev.sriramcodessw.versesync.eval

import com.google.gson.GsonBuilder
import java.io.File

/** Writes the result of a run as markdown for people and JSON for comparing runs. Neither holds a date, so the same corpus gives the same files. */
object Report {
    class Written(
        val markdown: File,
        val json: File,
    )

    fun write(
        corpus: Corpus,
        results: List<QueryResult>,
        label: String,
        outDir: File,
    ): Written {
        outDir.mkdirs()
        val md = File(outDir, "report.md")
        val json = File(outDir, "report.json")
        md.writeText(markdown(corpus, results, label))
        json.writeText(json(corpus, results, label))
        return Written(md, json)
    }

    fun markdown(
        corpus: Corpus,
        results: List<QueryResult>,
        label: String,
    ): String {
        val inLibrary = results.count { it.query.song != null }
        val outOfLibrary = results.size - inLibrary
        val sb = StringBuilder()
        sb.appendLine("# Recognition evaluation: $label")
        sb.appendLine()
        corpus.caution?.let { sb.appendLine("> **$it**").also { sb.appendLine() } }
        sb.appendLine("${results.size} recordings ($inLibrary of songs in the library, $outOfLibrary of songs that are not), ${corpus.library.size} songs in the library.")
        sb.appendLine("Intervals are 95 percent Wilson intervals. How the rules are replayed, and where that differs from the app, is in docs/09-evaluation-harness.md.")
        sb.appendLine()
        val notes = results.flatMap { r -> r.notes.map { it } }.groupingBy { it }.eachCount()
        notes.forEach { (note, n) -> sb.appendLine("- $n recordings: $note") }
        if (notes.isNotEmpty()) sb.appendLine()

        for (path in Path.entries) {
            sb.appendLine("## ${path.name.lowercase()}")
            sb.appendLine()
            Metrics.slices(results, path, corpus.library).forEach { (dimension, slices) ->
                if (dimension != "all") sb.appendLine("### by $dimension").also { sb.appendLine() }
                sb.appendLine("| slice | songs in lib | found | top-1 | not in lib | false accept | lock opens | lock false | latency |")
                sb.appendLine("|---|---|---|---|---|---|---|---|---|")
                slices.forEach { s ->
                    sb.appendLine(
                        "| ${s.name} | ${s.inLibrary} | ${s.found} | ${s.top1} | ${s.outOfLibrary} | ${s.falseAccept} | ${s.lockOpens} | ${s.lockFalse} | ${latency(s.medianLatencyMs)} |",
                    )
                }
                sb.appendLine()
            }
        }
        sb.appendLine("Trials needed to know a 1 percent false-accept rate to within half a point: ${Rate.trialsNeeded(0.01, 0.005)} per condition.")
        return sb.toString()
    }

    private fun latency(ms: Long?) = if (ms == null) "n/a" else "%.1f s".format(ms / 1000.0)

    fun json(
        corpus: Corpus,
        results: List<QueryResult>,
        label: String,
    ): String {
        fun rate(r: Rate) = mapOf("hits" to r.hits, "total" to r.total, "low" to r.low.takeUnless { it.isNaN() }, "high" to r.high.takeUnless { it.isNaN() })
        val paths =
            Path.entries.associate { path ->
                path.name.lowercase() to
                    Metrics.slices(results, path, corpus.library).mapValues { (_, slices) ->
                        slices.map { s ->
                            mapOf(
                                "slice" to s.name,
                                "inLibrary" to s.inLibrary,
                                "outOfLibrary" to s.outOfLibrary,
                                "found" to rate(s.found),
                                "top1" to rate(s.top1),
                                "falseAccept" to rate(s.falseAccept),
                                "lockOpens" to rate(s.lockOpens),
                                "lockFalse" to rate(s.lockFalse),
                                "medianLatencyMs" to s.medianLatencyMs,
                            )
                        }
                    }
            }
        val perQuery =
            results.map { r ->
                mapOf(
                    "id" to r.query.id,
                    "song" to r.query.song,
                    "candidates" to r.byPath.mapKeys { it.key.name.lowercase() }.mapValues { it.value.ranked },
                )
            }
        val doc = mapOf("label" to label, "recordings" to results.size, "paths" to paths, "queries" to perQuery)
        return GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create()
            .toJson(doc)
    }
}
