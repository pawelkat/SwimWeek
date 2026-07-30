package com.swimweek.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.swimweek.app.health.HealthConnectAvailability
import com.swimweek.app.health.HealthConnectSdkStatus
import com.swimweek.app.health.HealthPermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Schedules periodic swim sync and flex midnight week rollover.
 * Background periodic work is only enqueued when HC is available and
 * required (or background) permissions allow useful work.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val availability: HealthConnectAvailability,
    private val permissionChecker: HealthPermissionChecker,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val workManager get() = WorkManager.getInstance(context)

    fun ensureScheduled() {
        scope.launch {
            val canBackground = availability.status() == HealthConnectSdkStatus.AVAILABLE &&
                permissionChecker.hasRequiredPermissions()

            if (canBackground) {
                enqueuePeriodic()
                scheduleMidnightRollover()
            } else {
                // Keep midnight for week-identity UI path; skip aggressive periodic if no perms
                scheduleMidnightRollover()
            }
        }
    }

    fun enqueuePeriodic() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .build()

        val request = PeriodicWorkRequestBuilder<SwimSyncWorker>(
            6,
            TimeUnit.HOURS,
            1,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .addTag(TAG_PERIODIC)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun enqueueNow(reason: SyncReason) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .build()

        val request = OneTimeWorkRequestBuilder<SwimSyncWorker>()
            .setConstraints(constraints)
            .addTag(TAG_ONESHOT)
            .setInputData(
                androidx.work.workDataOf(SwimSyncWorker.KEY_REASON to reason.name),
            )
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_ONESHOT,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * One-shot toward next local midnight with flex (no exact alarms).
     * Rescheduled by [MidnightRolloverWorker] after each run.
     */
    fun scheduleMidnightRollover() {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var nextMidnight = LocalDate.now(zone).plusDays(1).atTime(LocalTime.MIDNIGHT).atZone(zone)
        // Small delay after midnight so week identity is stable
        nextMidnight = nextMidnight.plusMinutes(5)
        if (!nextMidnight.isAfter(now)) {
            nextMidnight = nextMidnight.plusDays(1)
        }
        val delay = Duration.between(now, nextMidnight)
        val delayMinutes = delay.toMinutes().coerceAtLeast(1)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .build()

        val request = OneTimeWorkRequestBuilder<MidnightRolloverWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            // Flex window: WorkManager may run within a window after delay for battery
            .setConstraints(constraints)
            .addTag(TAG_MIDNIGHT)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_MIDNIGHT,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val UNIQUE_PERIODIC = "swimweek_periodic_sync"
        const val UNIQUE_ONESHOT = "swimweek_oneshot_sync"
        const val UNIQUE_MIDNIGHT = "swimweek_midnight_rollover"
        const val TAG_PERIODIC = "periodic"
        const val TAG_ONESHOT = "oneshot"
        const val TAG_MIDNIGHT = "midnight"
    }
}
