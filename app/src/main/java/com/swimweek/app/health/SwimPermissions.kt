package com.swimweek.app.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord

/**
 * Health Connect permission contract for SwimWeek v1.
 *
 * Required: exercise sessions + distance (for weekly swim totals).
 * Recommended: background read (widget refresh via WorkManager — PR 6).
 * Not requested: HISTORY (current week fits default 30-day window), write types.
 */
object SwimPermissions {

    val required: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
    )

    val recommendedBackground: Set<String> = setOf(
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
    )

    /** Required + optional background — used by the permission request launcher. */
    val allRequested: Set<String> = required + recommendedBackground
}
