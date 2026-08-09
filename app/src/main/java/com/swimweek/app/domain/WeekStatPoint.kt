package com.swimweek.app.domain

/**
 * One week for home statistics (target vs result bar chart).
 */
data class WeekStatPoint(
    val week: WeekRange,
    /** Short label, e.g. "Jul 28" (week start date). */
    val label: String,
    val resultMeters: Double,
    val targetMeters: Double,
    val isCurrentWeek: Boolean = false,
) {
    val hasTarget: Boolean get() = targetMeters > 0.0
}
