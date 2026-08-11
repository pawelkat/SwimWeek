package com.swimweek.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrowdOccupancyTest {

    @Test
    fun levelBandsMatchCitySite() {
        // 0/220 → floor(0)+1 = 1
        assertEquals(1, CrowdOccupancy.levelFrom(0.0, 220.0))
        // 25% → floor(25/25)+1 = 2
        assertEquals(2, CrowdOccupancy.levelFrom(55.0, 220.0))
        // ~50% → 3
        assertEquals(3, CrowdOccupancy.levelFrom(110.0, 220.0))
        // ≥75% → 4
        assertEquals(4, CrowdOccupancy.levelFrom(165.0, 220.0))
        // over capacity still 4
        assertEquals(4, CrowdOccupancy.levelFrom(300.0, 220.0))
    }

    @Test
    fun invalidWhenMaxZero() {
        val o = CrowdOccupancy.fromRaw("SSD-1", "Altstetten", 0.0, 0.0, 0.0)
        assertFalse(o.valid)
        assertNull(o.level)
    }

    @Test
    fun validGuests() {
        val o = CrowdOccupancy.fromRaw("SSD-4", "City", 10.0, 220.0, 210.0)
        assertTrue(o.valid)
        assertEquals(10, o.guests)
        assertEquals(1, o.level)
    }
}
