package com.swimweek.app.util

import com.swimweek.app.domain.DistanceUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LengthFormatTest {

    @Test
    fun formatMeters_includesUnit() {
        val s = LengthFormat.format(2500.0, DistanceUnit.METERS, Locale.US)
        assertTrue(s.contains("2,500") || s.contains("2500"))
        assertTrue(s.endsWith(" m") || s.contains(" m"))
    }

    @Test
    fun formatYards_fromMeters() {
        val s = LengthFormat.format(914.4, DistanceUnit.YARDS, Locale.US)
        assertTrue(s.contains("1,000") || s.contains("1000"))
        assertTrue(s.contains("yd"))
    }

    @Test
    fun roundMeters_nonNegative() {
        assertTrue(LengthFormat.roundMeters(-1.0) == 0)
        assertTrue(LengthFormat.roundMeters(10.4) == 10)
        assertTrue(LengthFormat.roundMeters(10.6) == 11)
    }
}
