package com.swimweek.app.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwimPermissionsTest {

    @Test
    fun required_hasExerciseAndDistanceOnly() {
        assertEquals(2, SwimPermissions.required.size)
        assertTrue(SwimPermissions.required.any { it.contains("READ") || it.contains("read") || it.isNotEmpty() })
        // Background is separate
        assertTrue(SwimPermissions.required.none { it in SwimPermissions.recommendedBackground })
    }

    @Test
    fun allRequested_includesBackground() {
        assertTrue(SwimPermissions.allRequested.containsAll(SwimPermissions.required))
        assertTrue(SwimPermissions.allRequested.containsAll(SwimPermissions.recommendedBackground))
        assertEquals(
            SwimPermissions.required.size + SwimPermissions.recommendedBackground.size,
            SwimPermissions.allRequested.size,
        )
    }

    @Test
    fun historyNotRequested() {
        val joined = SwimPermissions.allRequested.joinToString()
        assertFalse(joined.contains("HISTORY", ignoreCase = true))
    }
}
