package dev.sriramcodessw.versesync.eval.music

import dev.sriramcodessw.versesync.eval.Rate
import dev.sriramcodessw.versesync.fingerprinting.landmarks.LandmarkFingerprinter
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.random.Random
import kotlin.test.assertTrue

/**
 * The evaluation of docs/09 section 13: choose a rule on seed A, report it once on seed B, the exact-family stress
 * variant, and the cost of a bigger library. Slow (minutes), so skipped unless asked for:
 *
 *   ./gradlew :app:testDebugUnitTest --tests "*PrintRun*" -Pversesync.eval.print=true [-Pversesync.eval.out=<dir>]
 */
class PrintRun {
    @Test
    fun evaluate() {
        assumeTrue(System.getProperty("versesync.eval.print") == "true", "not asked for, skipping")
        val out = File(System.getProperty("versesync.eval.out") ?: "build/eval").also { it.mkdirs() }
        val conditions = PrintEvaluation.conditions()
        val pooledConditions = conditions.filter { it.rePerformance == null }

        // Seed A: choose.
        val a = PrintEvaluation.Run(PrintEvaluation.Setup(seed = 43))
        val outcomesA = pooledConditions.associateWith { a.outcomes(it) }
        val pooledA = outcomesA.values.flatten()
        val chosen = PrintEvaluation.choose(pooledA)

        // Seed B: report once.
        val b = PrintEvaluation.Run(PrintEvaluation.Setup(seed = 44))
        val outcomesB = conditions.associateWith { b.outcomes(it) }
        val bytesPerSong =
            b.prints.values
                .map { it.toBytes().size }
                .average()

        // The stress variant: identical tempo and tuning within a family, clean queries only, seed B.
        val stress = PrintEvaluation.Run(PrintEvaluation.Setup(seed = 44, exactFamilies = true))
        val stressClean = stress.outcomes(conditions.first { it.name == "clean" })

        // With no rule chosen the strictest rule of the grid is used for the descriptive results, which choose nothing.
        val scale = scale(chosen ?: PrintEvaluation.Rule(25, 3f), conditions)
        val report = report(chosen, outcomesA, pooledA, outcomesB, bytesPerSong, stressClean, scale)
        File(out, "print-report.md").writeText(report)
        println("Print report written to ${File(out, "print-report.md").absolutePath}")
        assertTrue(File(out, "print-report.md").isFile)
    }

    private class ScaleRow(
        val songs: Int,
        val bytesPerSong: Double,
        val clean: PrintEvaluation.Tally?,
        val headline: PrintEvaluation.Tally?,
        val medianLookupMs: Double,
        val postings: Int,
    )

    private fun scale(
        rule: PrintEvaluation.Rule?,
        conditions: List<PrintEvaluation.Condition>,
    ): List<ScaleRow> =
        listOf(60 to 5, 600 to 1).map { (songs, knownEach) ->
            val run = PrintEvaluation.Run(PrintEvaluation.Setup(seed = 44, librarySongs = songs, outsideSongs = 40, songSeconds = 45, knownPerSong = knownEach))
            val headlineCondition = conditions.first { it.name == PrintEvaluation.headlineName }
            val clean = rule?.let { PrintEvaluation.tally(run.outcomes(conditions.first { it.name == "clean" }), it) }
            val headline = rule?.let { PrintEvaluation.tally(run.outcomes(headlineCondition), it) }
            // Lookup time alone, one query at a time on one thread, after a warm-up.
            val random = Random(1)
            val prints =
                run.queries.filter { !it.known }.take(220).map { q ->
                    val source = run.original(q.song)
                    val from = minOf((q.startSeconds * PrintEvaluation.RATE).toInt(), source.size - 5 * PrintEvaluation.RATE)
                    LandmarkFingerprinter.fingerprint(headlineCondition.damage(source.copyOfRange(from, from + 5 * PrintEvaluation.RATE), random))
                }
            prints.take(20).forEach { run.index.scores(it) }
            val times =
                prints.drop(20).map {
                    val t0 = System.nanoTime()
                    run.index.scores(it)
                    (System.nanoTime() - t0) / 1e6
                }
            ScaleRow(
                songs,
                run.prints.values
                    .map { it.toBytes().size }
                    .average(),
                clean,
                headline,
                times.sorted()[times.size / 2],
                run.index.size,
            )
        }

    private fun tally(
        outcomes: List<PrintEvaluation.Outcome>,
        rule: PrintEvaluation.Rule,
    ) = PrintEvaluation.tally(outcomes, rule)

    /**
     * What is reported when no rule met the criterion: nothing is chosen, so there is no pass or fail, and these are
     * descriptive only, at the strictest rule of the grid. Seed A by condition says where the false accepts come from;
     * the re-performance conditions, the stress variant and the library sizes were never used to choose.
     */
    private fun descriptive(
        sb: StringBuilder,
        outcomesA: Map<PrintEvaluation.Condition, List<PrintEvaluation.Outcome>>,
        outcomesB: Map<PrintEvaluation.Condition, List<PrintEvaluation.Outcome>>,
        bytesPerSong: Double,
        stressClean: List<PrintEvaluation.Outcome>,
        scale: List<ScaleRow>,
    ) {
        val strictest = PrintEvaluation.Rule(25, 3f)
        sb.appendLine()
        sb.appendLine("## Seed A by condition at the strictest rule of the grid (`$strictest`)")
        sb.appendLine()
        sb.appendLine("Diagnostic only, to see where false accepts come from. Nothing is chosen from it, and the pass bar is not applied.")
        sb.appendLine()
        sb.appendLine("| condition | top-1 | false accepts |")
        sb.appendLine("|---|---|---|")
        for ((condition, outcomes) in outcomesA) {
            val t = tally(outcomes, strictest)
            sb.appendLine("| ${condition.name} | ${t.knownRight} | ${t.unknownAccepted} |")
        }
        sb.appendLine()
        sb.appendLine("## Re-performance, stress and library size, at the strictest rule (`$strictest`)")
        sb.appendLine()
        sb.appendLine("Descriptive, seed B, with no bar. None of this was used to choose a rule. The re-performance rows play the same generated score with other sounds, a faster or drifting tempo and loose timing, the closest stand-in here for a live band.")
        sb.appendLine()
        sb.appendLine("| re-performance | library queries | top-1 | false accepts |")
        sb.appendLine("|---|---|---|---|")
        for ((condition, outcomes) in outcomesB.filterKeys { it.rePerformance != null }) {
            val t = tally(outcomes, strictest)
            sb.appendLine("| ${condition.name.removePrefix("re-performance: ")} | ${t.knownRight.total} | ${t.knownRight} | ${t.unknownAccepted} |")
        }
        sb.appendLine()
        val s = tally(stressClean, strictest)
        sb.appendLine("Stress variant (songs of a family share tempo and tuning exactly), clean queries: top-1 ${s.knownRight}, false accepts ${s.unknownAccepted}.")
        sb.appendLine()
        sb.appendLine("Cost of a bigger library: songs of 45 s, bytes per song ${"%.0f".format(bytesPerSong * 0.5)} at this length. Lookup time is the median for one 5 s query on one thread, not counting fingerprinting.")
        sb.appendLine()
        sb.appendLine("| songs in library | hashes stored | bytes per song | median lookup ms | clean top-1 | clean false accepts | headline top-1 | headline false accepts |")
        sb.appendLine("|---|---|---|---|---|---|---|---|")
        for (row in scale) {
            sb.appendLine(
                "| ${row.songs} | ${row.postings} | ${"%.0f".format(row.bytesPerSong)} | ${"%.1f".format(row.medianLookupMs)} | ${row.clean?.knownRight} | ${row.clean?.unknownAccepted} | ${row.headline?.knownRight} | ${row.headline?.unknownAccepted} |",
            )
        }
    }

    private fun report(
        chosen: PrintEvaluation.Rule?,
        outcomesA: Map<PrintEvaluation.Condition, List<PrintEvaluation.Outcome>>,
        pooledA: List<PrintEvaluation.Outcome>,
        outcomesB: Map<PrintEvaluation.Condition, List<PrintEvaluation.Outcome>>,
        bytesPerSong: Double,
        stressClean: List<PrintEvaluation.Outcome>,
        scale: List<ScaleRow>,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("# Recorded-music landmark prints: generated corpora")
        sb.appendLine()
        sb.appendLine("Generated music, not real recordings. Plan, grid, criterion and pass bar as fixed in docs/09 section 13. Seed A chooses, seed B is reported once. 60 library songs and 40 songs not in the library of 90 s per seed; 300 queries of library songs and 1,000 of songs not in the library per condition. Intervals are 95% Wilson.")
        sb.appendLine()
        sb.appendLine("## Chosen on seed A")
        sb.appendLine()
        if (chosen == null) {
            sb.appendLine("**No rule met the criterion on seed A** (pooled false accepts at most 0.1% with the upper end at most 0.5%), so none is chosen and the pass bar cannot be applied. Pooled results for every rule on seed A:")
            sb.appendLine()
            sb.appendLine("| rule | top-1 (library queries) | false accepts (songs not in the library) |")
            sb.appendLine("|---|---|---|")
            PrintEvaluation.grid.forEach { r -> tally(pooledA, r).let { sb.appendLine("| $r | ${it.knownRight} | ${it.unknownAccepted} |") } }
            descriptive(sb, outcomesA, outcomesB, bytesPerSong, stressClean, scale)
            return sb.toString()
        }
        val a = tally(pooledA, chosen)
        sb.appendLine("Chosen: `$chosen`. On seed A, pooled over every condition except re-performance: top-1 ${a.knownRight}; false accepts ${a.unknownAccepted}.")
        sb.appendLine()

        sb.appendLine("## Reported on seed B")
        sb.appendLine()
        sb.appendLine("| condition | library queries | top-1 | wrong song | queries for songs not in the library | false accepts |")
        sb.appendLine("|---|---|---|---|---|---|")
        for ((condition, outcomes) in outcomesB) {
            val t = tally(outcomes, chosen)
            sb.appendLine("| ${condition.name} | ${t.knownRight.total} | ${t.knownRight} | ${t.knownWrongSong} | ${t.unknownAccepted.total} | ${t.unknownAccepted} |")
        }
        sb.appendLine()
        val pooledB = outcomesB.filterKeys { it.rePerformance == null }.values.flatten()
        val p = tally(pooledB, chosen)
        sb.appendLine("Pooled over every condition except re-performance: top-1 ${p.knownRight}, wrong song ${p.knownWrongSong} of ${p.knownRight.total}, false accepts ${p.unknownAccepted}.")
        sb.appendLine()
        sb.appendLine("By snippet length, pooled the same way:")
        sb.appendLine()
        sb.appendLine("| snippet | top-1 | false accepts |")
        sb.appendLine("|---|---|---|")
        for (length in listOf(3, 5, 10)) {
            val t = tally(pooledB.filter { it.seconds == length }, chosen)
            sb.appendLine("| $length s | ${t.knownRight} | ${t.unknownAccepted} |")
        }
        sb.appendLine()
        val headline =
            outcomesB.entries
                .first { it.key.name == PrintEvaluation.headlineName }
                .value
                .let { tally(it, chosen) }
        val passFalse = p.unknownAccepted.high <= PrintEvaluation.PASS_FALSE_UPPER
        val passHeadline = headline.knownRight.value >= PrintEvaluation.PASS_HEADLINE
        sb.appendLine("**Pass bar:** pooled false accepts with the upper end at most 1% (${p.unknownAccepted}): ${if (passFalse) "PASS" else "FAIL"}. Headline top-1 at least 80% (${headline.knownRight}): ${if (passHeadline) "PASS" else "FAIL"}.")
        sb.appendLine()

        sb.appendLine("## Stress variant: songs of a family share tempo and tuning exactly")
        sb.appendLine()
        val s = tally(stressClean, chosen)
        sb.appendLine("Clean queries, seed B, the same library size and the same rule: top-1 ${s.knownRight}, false accepts ${s.unknownAccepted}.")
        sb.appendLine()

        sb.appendLine("## Cost of a bigger library (no bar)")
        sb.appendLine()
        sb.appendLine("Songs of 45 s, seed B. Bytes per song at this length: ${"%.0f".format(bytesPerSong * 0.5)} (about ${"%.0f".format(bytesPerSong / 90 * 240 / 1024)} KB for four minutes at the density of the 90 s songs). Lookup time is the median for one 5 s query on one thread, not counting fingerprinting.")
        sb.appendLine()
        sb.appendLine("| songs in library | hashes stored | bytes per song | median lookup ms | clean top-1 | clean false accepts | headline top-1 | headline false accepts |")
        sb.appendLine("|---|---|---|---|---|---|---|---|")
        for (row in scale) {
            sb.appendLine(
                "| ${row.songs} | ${row.postings} | ${"%.0f".format(row.bytesPerSong)} | ${"%.1f".format(row.medianLookupMs)} | ${row.clean?.knownRight} | ${row.clean?.unknownAccepted} | ${row.headline?.knownRight} | ${row.headline?.unknownAccepted} |",
            )
        }
        sb.appendLine()
        sb.appendLine("Rate type: ${Rate::class.simpleName}.")
        return sb.toString()
    }
}
