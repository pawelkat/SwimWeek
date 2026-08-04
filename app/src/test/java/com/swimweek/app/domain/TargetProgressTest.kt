package com.swimweek.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetProgressTest {

    @Test
    fun noTarget_zeroFraction() {
        assertEquals(0f, TargetProgress.fraction(1000.0, 0.0), 0f)
        assertFalse(TargetProgress.hasTarget(0.0))
        assertFalse(TargetProgress.hasTarget(-1.0))
    }

    @Test
    fun halfAndFull() {
        assertEquals(0.5f, TargetProgress.fraction(500.0, 1000.0), 0.001f)
        assertEquals(1f, TargetProgress.fraction(1000.0, 1000.0), 0f)
        assertTrue(TargetProgress.isComplete(1000.0, 1000.0))
    }

    @Test
    fun overTarget_clampedToOne() {
        assertEquals(1f, TargetProgress.fraction(2000.0, 1000.0), 0f)
        assertTrue(TargetProgress.isComplete(2000.0, 1000.0))
    }

    @Test
    fun zeroDistance() {
        assertEquals(0f, TargetProgress.fraction(0.0, 1000.0), 0f)
        assertFalse(TargetProgress.isComplete(0.0, 1000.0))
    }
}
