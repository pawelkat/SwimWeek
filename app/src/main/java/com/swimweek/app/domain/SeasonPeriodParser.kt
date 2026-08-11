package com.swimweek.app.domain

import java.time.LocalDate
import java.time.Month

/**
 * Parses German Stadt Zürich season ranges such as:
 * - "9.–29. Mai"
 * - "30. Mai–16. August"
 * - "9. Mai–6. September"
 */
object SeasonPeriodParser {

    private val MONTHS = mapOf(
        "januar" to Month.JANUARY,
        "februar" to Month.FEBRUARY,
        "märz" to Month.MARCH,
        "marz" to Month.MARCH,
        "april" to Month.APRIL,
        "mai" to Month.MAY,
        "juni" to Month.JUNE,
        "juli" to Month.JULY,
        "august" to Month.AUGUST,
        "september" to Month.SEPTEMBER,
        "oktober" to Month.OCTOBER,
        "november" to Month.NOVEMBER,
        "dezember" to Month.DECEMBER,
    )

    private val RANGE = Regex(
        """(\d{1,2})\.?\s*(?:([A-Za-zäöüÄÖÜ]+))?\s*[–—-]\s*(\d{1,2})\.?\s*([A-Za-zäöüÄÖÜ]+)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * @return inclusive date range for [year], or null if unparseable.
     */
    fun parse(period: String?, year: Int): ClosedRange<LocalDate>? {
        if (period.isNullOrBlank()) return null
        val normalized = period
            .replace('\u00a0', ' ')
            .replace("–", "-")
            .replace("—", "-")
            .trim()
        val m = RANGE.find(normalized) ?: return null
        val startDay = m.groupValues[1].toIntOrNull() ?: return null
        val startMonthName = m.groupValues[2].ifBlank { m.groupValues[4] }
        val endDay = m.groupValues[3].toIntOrNull() ?: return null
        val endMonthName = m.groupValues[4]
        val startMonth = monthOf(startMonthName) ?: return null
        val endMonth = monthOf(endMonthName) ?: return null
        return try {
            val start = LocalDate.of(year, startMonth, startDay.coerceIn(1, startMonth.length(isLeap(year))))
            var end = LocalDate.of(year, endMonth, endDay.coerceIn(1, endMonth.length(isLeap(year))))
            // Handle wrap (unlikely for Sommerbad): if end before start, push end to next year
            if (end.isBefore(start)) {
                end = end.plusYears(1)
            }
            start..end
        } catch (_: Exception) {
            null
        }
    }

    fun contains(period: String?, date: LocalDate, seasonYear: Int?): Boolean {
        if (period.isNullOrBlank()) return true
        val year = seasonYear ?: date.year
        // Try season year and current year (scraped ranges are calendar-year based)
        val ranges = listOfNotNull(
            parse(period, year),
            if (year != date.year) parse(period, date.year) else null,
        )
        return ranges.any { date in it }
    }

    private fun monthOf(name: String): Month? =
        MONTHS[name.lowercase().trim()]

    private fun isLeap(year: Int): Boolean =
        java.time.Year.isLeap(year.toLong())
}
