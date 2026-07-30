package com.swimweek.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OverlapPolicyTest {

    private fun session(
        id: String,
        startEpoch: Long,
        endEpoch: Long,
        meters: Double,
    ): SwimSession =
        SwimSession(
            id = id,
            exerciseType = SwimType.POOL,
            start = Instant.ofEpochSecond(startEpoch),
            end = Instant.ofEpochSecond(endEpoch),
            distanceMeters = meters,
            distanceSource = DistanceSource.AGGREGATE_ORIGIN_FILTERED,
            partialDistance = meters == 0.0,
            dataOriginPackage = "com.sec.android.app.shealth",
        )

    @Test
    fun nonOverlapping_unchanged() {
        val a = session("a", 0, 100, 1000.0)
        val b = session("b", 100, 200, 500.0)
        val out = applyOverlapPolicy(listOf(a, b))
        assertEquals(1000.0, out.single { it.id == "a" }.distanceMeters, 0.0)
        assertEquals(500.0, out.single { it.id == "b" }.distanceMeters, 0.0)
        assertFalse(out.any { it.overlapSuppressed })
    }

    @Test
    fun overlapping_creditsLongerSessionOnly() {
        val short = session("short", 0, 50, 800.0)
        val long = session("long", 25, 200, 1200.0)
        val out = applyOverlapPolicy(listOf(short, long))
        assertEquals(1200.0, out.single { it.id == "long" }.distanceMeters, 0.0)
        val suppressed = out.single { it.id == "short" }
        assertEquals(0.0, suppressed.distanceMeters, 0.0)
        assertTrue(suppressed.overlapSuppressed)
        assertTrue(suppressed.partialDistance)
    }

    @Test
    fun overlapping_sameDuration_earlierStartWins() {
        val first = session("first", 0, 100, 300.0)
        val second = session("second", 50, 150, 900.0)
        // durations both 100s — earlier start (first) keeps distance
        val out = applyOverlapPolicy(listOf(second, first))
        assertEquals(300.0, out.single { it.id == "first" }.distanceMeters, 0.0)
        assertEquals(0.0, out.single { it.id == "second" }.distanceMeters, 0.0)
    }

    @Test
    fun emptyAndSingle_passthrough() {
        assertTrue(applyOverlapPolicy(emptyList()).isEmpty())
        val one = session("only", 0, 10, 100.0)
        assertEquals(listOf(one), applyOverlapPolicy(listOf(one)))
    }
}
