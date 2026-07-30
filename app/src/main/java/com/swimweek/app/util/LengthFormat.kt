package com.swimweek.app.util

import com.swimweek.app.domain.DistanceUnit
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Presentation formatting for swimming distance (meters stored internally).
 */
object LengthFormat {

    fun format(
        meters: Double,
        unit: DistanceUnit,
        locale: Locale = Locale.getDefault(),
    ): String {
        val value = DistanceUnit.metersTo(unit, meters)
        val number = NumberFormat.getNumberInstance(locale).apply {
            when (unit) {
                DistanceUnit.METERS -> {
                    maximumFractionDigits = 0
                    minimumFractionDigits = 0
                }
                DistanceUnit.YARDS -> {
                    maximumFractionDigits = 0
                    minimumFractionDigits = 0
                }
                DistanceUnit.MILES -> {
                    maximumFractionDigits = 2
                    minimumFractionDigits = 0
                }
            }
        }
        return "${number.format(value)} ${unitLabel(unit, value, locale)}"
    }

    /**
     * Compact widget-friendly string, e.g. "2,500 m" or "1.2 mi".
     */
    fun formatCompact(
        meters: Double,
        unit: DistanceUnit,
        locale: Locale = Locale.getDefault(),
    ): String = format(meters, unit, locale)

    fun unitLabel(unit: DistanceUnit, value: Double, locale: Locale = Locale.getDefault()): String {
        // Simple English abbreviations for v1; full i18n in later PRs.
        return when (unit) {
            DistanceUnit.METERS -> "m"
            DistanceUnit.YARDS -> if (value == 1.0) "yd" else "yd"
            DistanceUnit.MILES -> if (approxEquals(value, 1.0)) "mi" else "mi"
        }
    }

    /** Whole meters rounded for badge totals. */
    fun roundMeters(meters: Double): Int = meters.roundToInt().coerceAtLeast(0)

    private fun approxEquals(a: Double, b: Double): Boolean =
        kotlin.math.abs(a - b) < 1e-9
}
