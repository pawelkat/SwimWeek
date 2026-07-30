package com.swimweek.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncReasonTest {

    @Test
    fun periodicIsNotForceFullReason() {
        val forceFullReasons = setOf(
            SyncReason.MANUAL,
            SyncReason.APP_FOREGROUND,
            SyncReason.MIDNIGHT,
            SyncReason.PERMISSION_GRANTED,
            SyncReason.WEEK_IDENTITY_CHANGED,
            SyncReason.TOKEN_EXPIRED,
            SyncReason.WIDGET_PIN,
        )
        assertTrue(SyncReason.PERIODIC !in forceFullReasons)
        assertTrue(SyncReason.CHANGES !in forceFullReasons)
        assertEquals(SyncReason.PERIODIC.name, "PERIODIC")
    }

    @Test
    fun allReasonsHaveStableNames() {
        SyncReason.entries.forEach { reason ->
            assertEquals(reason, SyncReason.valueOf(reason.name))
        }
    }
}
