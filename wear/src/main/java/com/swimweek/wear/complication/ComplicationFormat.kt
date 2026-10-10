package com.swimweek.wear.complication

import java.util.Locale
import kotlin.math.roundToInt

/** Pure formatting for complication text (unit-tested; no Android deps). */
object ComplicationFormat {
    private const val METERS_PER_YARD = 0.9144
    private const val METERS_PER_MILE = 1609.344

    fun convert(meters: Double, unit: String): Double = when (unit) {
        "YARDS" -> meters / METERS_PER_YARD
        "MILES" -> meters / METERS_PER_MILE
        else -> meters
    }

    fun unitSuffix(unit: String): String = when (unit) {
        "YARDS" -> "yd"
        "MILES" -> "mi"
        else -> "m"
    }

    /** Compact value for SHORT_TEXT (≈ 7 chars max): 850 / 1.2k / 12k / 3.4 (miles). */
    fun shortValue(meters: Double, unit: String): String {
        val v = convert(meters.coerceAtLeast(0.0), unit)
        return when {
            unit == "MILES" -> String.format(Locale.US, "%.1f", v)
            v < 1000 -> v.roundToInt().toString()
            v < 10_000 -> String.format(Locale.US, "%.1fk", v / 1000).replace(".0k", "k")
            else -> "${(v / 1000).roundToInt()}k"
        }
    }

    /** Full label, e.g. "1,250 m". */
    fun fullValue(meters: Double, unit: String): String {
        val v = convert(meters.coerceAtLeast(0.0), unit)
        val num = if (unit == "MILES") String.format(Locale.US, "%.2f", v) else String.format(Locale.US, "%,d", v.roundToInt())
        return "$num ${unitSuffix(unit)}"
    }

    /** RANGED_VALUE: (value, max) in display units; max falls back to value when no target. */
    fun range(meters: Double, targetMeters: Double, unit: String): Pair<Float, Float> {
        val v = convert(meters.coerceAtLeast(0.0), unit).toFloat()
        val max = if (targetMeters > 0) convert(targetMeters, unit).toFloat() else v.coerceAtLeast(1f)
        return v.coerceAtMost(max) to max
    }
}
