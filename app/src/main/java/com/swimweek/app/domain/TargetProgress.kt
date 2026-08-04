package com.swimweek.app.domain

/**
 * Weekly distance target progress (0f..1f).
 * [targetMeters] ≤ 0 means no target (ring hidden / empty).
 */
object TargetProgress {
    fun fraction(distanceMeters: Double, targetMeters: Double): Float {
        if (targetMeters <= 0.0) return 0f
        if (distanceMeters <= 0.0) return 0f
        val raw = distanceMeters / targetMeters
        return raw.coerceIn(0.0, 1.0).toFloat()
    }

    fun hasTarget(targetMeters: Double): Boolean = targetMeters > 0.0

    fun isComplete(distanceMeters: Double, targetMeters: Double): Boolean =
        hasTarget(targetMeters) && distanceMeters >= targetMeters
}
