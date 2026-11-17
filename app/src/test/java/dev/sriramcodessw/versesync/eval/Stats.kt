package dev.sriramcodessw.versesync.eval

import kotlin.math.sqrt

/** A count out of a total, with a 95 percent Wilson score interval, which behaves well for small samples and rates near 0 or 1. */
class Rate(
    val hits: Int,
    val total: Int,
) {
    val value: Double get() = if (total == 0) Double.NaN else hits.toDouble() / total

    val low: Double get() = interval().first
    val high: Double get() = interval().second

    private fun interval(): Pair<Double, Double> {
        if (total == 0) return Double.NaN to Double.NaN
        val z = 1.96
        val p = value
        val n = total.toDouble()
        val denominator = 1 + z * z / n
        val center = (p + z * z / (2 * n)) / denominator
        val margin = z * sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / denominator
        return (center - margin).coerceAtLeast(0.0) to (center + margin).coerceAtMost(1.0)
    }

    /** For example "6/10 = 60% (31 to 83)". */
    override fun toString(): String {
        if (total == 0) return "none"

        fun pct(x: Double) = "%.0f".format(x * 100)
        return "$hits/$total = ${pct(value)}% (${pct(low)} to ${pct(high)})"
    }

    companion object {
        /**
         * How many trials are needed so that a rate near [rate] is known to within plus or minus
         * [margin] at 95 percent confidence. For one percent to within half a point this is a few hundred.
         */
        fun trialsNeeded(
            rate: Double,
            margin: Double,
        ): Int = kotlin.math.ceil(1.96 * 1.96 * rate * (1 - rate) / (margin * margin)).toInt()
    }
}
