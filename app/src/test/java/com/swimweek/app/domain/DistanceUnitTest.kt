package com.swimweek.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DistanceUnitTest {

    @Test
    fun defaultForLocale_usYards_elseMeters() {
        assertEquals(DistanceUnit.YARDS, DistanceUnit.defaultForLocale(Locale.US))
        assertEquals(DistanceUnit.METERS, DistanceUnit.defaultForLocale(Locale.UK))
        assertEquals(DistanceUnit.METERS, DistanceUnit.defaultForLocale(Locale.FRANCE))
    }

    @Test
    fun conversion_roundTripMeters() {
        val meters = 1500.0
        val yards = DistanceUnit.metersTo(DistanceUnit.YARDS, meters)
        assertEquals(meters, DistanceUnit.toMeters(DistanceUnit.YARDS, yards), 1e-9)

        val miles = DistanceUnit.metersTo(DistanceUnit.MILES, meters)
        assertEquals(meters, DistanceUnit.toMeters(DistanceUnit.MILES, miles), 1e-9)
    }

    @Test
    fun knownConversions() {
        assertEquals(1.0, DistanceUnit.metersTo(DistanceUnit.METERS, 1.0), 0.0)
        // 0.9144 m = 1 yd
        assertEquals(1.0, DistanceUnit.metersTo(DistanceUnit.YARDS, 0.9144), 1e-9)
        assertEquals(1.0, DistanceUnit.metersTo(DistanceUnit.MILES, 1609.344), 1e-9)
    }
}
