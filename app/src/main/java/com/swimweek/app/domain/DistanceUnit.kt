package com.swimweek.app.domain

import java.util.Locale

/**
 * Display units for swimming distance.
 * Storage / aggregation always uses meters; conversion is presentation-only.
 */
enum class DistanceUnit {
    METERS,
    YARDS,
    MILES,
    ;

    companion object {
        private const val METERS_PER_YARD = 0.9144
        private const val METERS_PER_MILE = 1609.344

        /**
         * Locale-based default: US customary → yards; otherwise meters.
         * (Miles remain a Settings override for long open-water totals.)
         */
        fun defaultForLocale(locale: Locale = Locale.getDefault()): DistanceUnit {
            return when (locale.country.uppercase(Locale.ROOT)) {
                "US", "LR", "MM" -> YARDS
                else -> METERS
            }
        }

        fun metersTo(unit: DistanceUnit, meters: Double): Double =
            when (unit) {
                METERS -> meters
                YARDS -> meters / METERS_PER_YARD
                MILES -> meters / METERS_PER_MILE
            }

        fun toMeters(unit: DistanceUnit, value: Double): Double =
            when (unit) {
                METERS -> value
                YARDS -> value * METERS_PER_YARD
                MILES -> value * METERS_PER_MILE
            }
    }
}
