package dev.sriramcodessw.versesync.eval.music

import dev.sriramcodessw.versesync.eval.Rate
import dev.sriramcodessw.versesync.fingerprinting.landmarks.LandmarkFingerprinter
import dev.sriramcodessw.versesync.fingerprinting.landmarks.LandmarkIndex
import dev.sriramcodessw.versesync.fingerprinting.landmarks.LandmarkPrint
import dev.sriramcodessw.versesync.fingerprinting.landmarks.LandmarkScores
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.IntStream
import kotlin.random.Random

/*
 * The evaluation of docs/09 section 13: generated songs, a library of some, queries damaged in set ways, and the
 * votes the matcher gives each. The grid, criterion and pass bar are fixed in that section, before any run.
 */
internal object PrintEvaluation {
    const val RATE = SyntheticMusic.RATE

    class Condition(
        val name: String,
        /** Re-performance conditions are not used to choose thresholds. */
        val rePerformance: Performance? = null,
        /** Queries are this long whatever their own length, for the headline condition. */
        val fixedSeconds: Int? = null,
        val damage: (FloatArray, Random) -> FloatArray = { x, _ -> x },
    )

    class Query(
        val song: Int,
        val known: Boolean,
        val startSeconds: Double,
        val seconds: Int,
    )

    class Outcome(
        val query: Query,
        val seconds: Int,
        val scores: LandmarkScores,
    )

    private fun noise(
        kind: Damage.Noise,
        db: Double,
    ): (FloatArray, Random) -> FloatArray = { x, r -> Damage.addNoise(x, kind, db, r) }

    private val different = Performance(timbreSeed = 9001, percussionSeed = 9002)

    val headlineName = "headline: 5 s, speaker band + echo + pink noise 5 dB"

    fun conditions(): List<Condition> =
        listOf(
            Condition("clean"),
            Condition("white noise 20 dB", damage = noise(Damage.Noise.WHITE, 20.0)),
            Condition("white noise 10 dB", damage = noise(Damage.Noise.WHITE, 10.0)),
            Condition("white noise 5 dB", damage = noise(Damage.Noise.WHITE, 5.0)),
            Condition("white noise 0 dB", damage = noise(Damage.Noise.WHITE, 0.0)),
            Condition("white noise -5 dB", damage = noise(Damage.Noise.WHITE, -5.0)),
            Condition("pink noise 5 dB", damage = noise(Damage.Noise.PINK, 5.0)),
            Condition("speech-shaped noise 5 dB", damage = noise(Damage.Noise.SPEECH, 5.0)),
            Condition("level +20 dB, clipped", damage = { x, _ -> Damage.gain(x, 20.0) }),
            Condition("level -20 dB", damage = { x, _ -> Damage.gain(x, -20.0) }),
            Condition("speaker band 250 Hz to 4 kHz", damage = { x, _ -> Damage.speakerBand(x) }),
            Condition("room echo", damage = { x, r -> Damage.echo(x, r) }),
            Condition("speed +1%", damage = { x, _ -> Damage.speed(x, 1.01) }),
            Condition("speed -1%", damage = { x, _ -> Damage.speed(x, 0.99) }),
            Condition(headlineName, fixedSeconds = 5, damage = { x, r -> Damage.addNoise(Damage.echo(Damage.speakerBand(x), r), Damage.Noise.PINK, 5.0, r) }),
            Condition("re-performance: different sound", rePerformance = different),
            Condition("re-performance: tempo +3%", rePerformance = Performance(tempoFactor = 1.03)),
            Condition("re-performance: tempo +5%", rePerformance = Performance(tempoFactor = 1.05)),
            Condition("re-performance: tempo +8%", rePerformance = Performance(tempoFactor = 1.08)),
            Condition(
                "re-performance: live-like (sound, +5% drifting, loose timing)",
                rePerformance = Performance(timbreSeed = 9001, percussionSeed = 9002, tempoFactor = 1.05, driftFraction = 0.02, jitterSeconds = 0.03),
            ),
        )

    class Setup(
        val seed: Long,
        val librarySongs: Int = 60,
        val outsideSongs: Int = 40,
        val songSeconds: Int = 90,
        val knownPerSong: Int = 5,
        val unknownQueries: Int = 1000,
        /** The stress variant: songs of a family share tempo and tuning exactly. */
        val exactFamilies: Boolean = false,
    )

    /** A library, its index, and every query, ready to be run through conditions. */
    class Run(
        val setup: Setup,
    ) {
        private val originals = ConcurrentHashMap<Int, FloatArray>()
        private val performances = ConcurrentHashMap<Pair<Int, Int>, FloatArray>()

        fun original(index: Int): FloatArray = originals.getOrPut(index) { SyntheticMusic.song(setup.seed, index, setup.songSeconds, exactFamilies = setup.exactFamilies) }

        fun performed(
            index: Int,
            performance: Performance,
        ): FloatArray = performances.getOrPut(index to System.identityHashCode(performance)) { SyntheticMusic.song(setup.seed, index, setup.songSeconds, performance, setup.exactFamilies) }

        val prints: Map<Int, LandmarkPrint> by lazy {
            val result = ConcurrentHashMap<Int, LandmarkPrint>()
            IntStream.range(0, setup.librarySongs).parallel().forEach { result[it] = LandmarkFingerprinter.fingerprint(original(it)) }
            result.toSortedMap()
        }

        val index: LandmarkIndex by lazy { LandmarkIndex(prints) }

        val queries: List<Query> by lazy {
            val random = Random(setup.seed + 7)
            val lengths = listOf(3, 5, 10)
            val known =
                (0 until setup.librarySongs).flatMap { song ->
                    List(setup.knownPerSong) {
                        val seconds = lengths[random.nextInt(lengths.size)]
                        Query(song, true, random.nextDouble() * (setup.songSeconds - seconds - 0.5), seconds)
                    }
                }
            val unknown =
                List(setup.unknownQueries) {
                    val seconds = lengths[random.nextInt(lengths.size)]
                    val song = setup.librarySongs + random.nextInt(setup.outsideSongs)
                    Query(song, false, random.nextDouble() * (setup.songSeconds - seconds - 0.5), seconds)
                }
            known + unknown
        }

        /** Every query through [condition]: the votes the matcher gives. */
        fun outcomes(condition: Condition): List<Outcome> {
            val list = queries
            val results = arrayOfNulls<Outcome>(list.size)
            IntStream.range(0, list.size).parallel().forEach { i ->
                val q = list[i]
                val source = condition.rePerformance?.let { performed(q.song, it) } ?: original(q.song)
                val seconds = condition.fixedSeconds ?: q.seconds
                val from = (q.startSeconds * RATE).toInt()
                val slice = source.copyOfRange(from, minOf(source.size, from + seconds * RATE))
                val damaged = condition.damage(slice, Random(setup.seed * 31 + i * 7L + condition.name.hashCode()))
                results[i] = Outcome(q, seconds, index.scores(LandmarkFingerprinter.fingerprint(damaged)))
            }
            return results.map { it!! }
        }
    }

    // The grid of docs/09 section 13.
    val minVotes = listOf(8, 10, 12, 15, 20, 25)
    val ratios = listOf(1.5f, 2f, 3f)

    class Rule(
        val minVotes: Int,
        val ratio: Float,
    ) {
        override fun toString() = "votes>=$minVotes ratio>=$ratio"
    }

    val grid: List<Rule> = minVotes.flatMap { v -> ratios.map { Rule(v, it) } }

    class Tally(
        val knownRight: Rate,
        val unknownAccepted: Rate,
        val knownWrongSong: Int,
    )

    fun tally(
        outcomes: List<Outcome>,
        rule: Rule,
    ): Tally {
        var known = 0
        var right = 0
        var wrong = 0
        var unknown = 0
        var accepted = 0
        for (o in outcomes) {
            val song = o.scores.accepted(rule.minVotes, rule.ratio)
            if (o.query.known) {
                known++
                if (song == o.query.song) {
                    right++
                } else if (song != null) {
                    wrong++
                }
            } else {
                unknown++
                if (song != null) accepted++
            }
        }
        return Tally(Rate(right, known), Rate(accepted, unknown), wrong)
    }

    // Criterion fixed in docs/09 section 13: pooled false accepts at most 0.1% with the upper end at most 0.5%.
    const val SELECT_FALSE = 0.001
    const val SELECT_FALSE_UPPER = 0.005

    // Pass bar on the report seed: pooled upper end at most 1%, headline top-1 at least 80%.
    const val PASS_FALSE_UPPER = 0.01
    const val PASS_HEADLINE = 0.80

    /** The rule chosen on [pooled] outcomes (every condition except the re-performance ones), or null. */
    fun choose(pooled: List<Outcome>): Rule? =
        grid
            .map { it to tally(pooled, it) }
            .filter { (_, t) -> t.unknownAccepted.value <= SELECT_FALSE && t.unknownAccepted.high <= SELECT_FALSE_UPPER }
            .sortedWith(compareByDescending<Pair<Rule, Tally>> { it.second.knownRight.value }.thenByDescending { it.first.minVotes }.thenByDescending { it.first.ratio })
            .firstOrNull()
            ?.first
}
