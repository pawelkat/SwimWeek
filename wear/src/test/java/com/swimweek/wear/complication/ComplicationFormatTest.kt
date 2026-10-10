package com.swimweek.wear.complication

import org.junit.Assert.assertEquals
import org.junit.Test

class ComplicationFormatTest {
    @Test fun shortValue_meters() {
        assertEquals("0", ComplicationFormat.shortValue(0.0, "METERS"))
        assertEquals("850", ComplicationFormat.shortValue(850.0, "METERS"))
        assertEquals("1.2k", ComplicationFormat.shortValue(1_234.0, "METERS"))
        assertEquals("2k", ComplicationFormat.shortValue(2_000.0, "METERS"))
        assertEquals("12k", ComplicationFormat.shortValue(12_400.0, "METERS"))
    }

    @Test fun shortValue_yardsAndMiles() {
        assertEquals("1.1k", ComplicationFormat.shortValue(1_000.0, "YARDS"))
        assertEquals("1.0", ComplicationFormat.shortValue(1_609.344, "MILES"))
    }

    @Test fun shortValue_negativeClampedToZero() {
        assertEquals("0", ComplicationFormat.shortValue(-5.0, "METERS"))
    }

    @Test fun fullValue() {
        assertEquals("1,250 m", ComplicationFormat.fullValue(1_250.0, "METERS"))
        assertEquals("0 yd", ComplicationFormat.fullValue(0.0, "YARDS"))
    }

    @Test fun range_withTarget_clampsToMax() {
        assertEquals(2_000f to 4_000f, ComplicationFormat.range(2_000.0, 4_000.0, "METERS"))
        assertEquals(4_000f to 4_000f, ComplicationFormat.range(5_000.0, 4_000.0, "METERS"))
    }

    @Test fun range_noTarget_fullRing() {
        assertEquals(1_500f to 1_500f, ComplicationFormat.range(1_500.0, 0.0, "METERS"))
        assertEquals(0f to 1f, ComplicationFormat.range(0.0, 0.0, "METERS"))
    }
}
