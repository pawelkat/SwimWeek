package com.swimweek.app.alert

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.swimweek.app.BuildConfig
import com.swimweek.app.data.PreferencesStore
import com.swimweek.app.data.ZurichPoolsRepository
import com.swimweek.app.domain.EveningPoolSelector
import com.swimweek.app.util.ClockProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Schedules [PoolReminderWorker] for 1.5h before the longest-open evening pool closes.
 * Uses WorkManager (no exact-alarm permission); delivery is best-effort.
 */
@Singleton
class PoolReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val planner: PoolReminderPlanner,
    private val poolsRepository: ZurichPoolsRepository,
    private val preferencesStore: PreferencesStore,
    private val notifier: PoolReminderNotifier,
    private val clockProvider: ClockProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val workManager get() = WorkManager.getInstance(context)

    fun ensureScheduled() {
        scope.launch {
            scheduleNext(afterNotification = false)
        }
    }

    /**
     * @param afterNotification when true, skip any remaining window today (next day onward).
     */
    suspend fun scheduleNext(afterNotification: Boolean = false) {
        val prefs = preferencesStore.get()
        if (!prefs.eveningPoolReminderEnabled) {
            cancel()
            return
        }

        notifier.ensureChannel()

        val planned = if (afterNotification) {
            planFromTomorrow()
        } else {
            planner.planNext()
        }

        if (planned == null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "no evening pool reminder planned")
            }
            cancel()
            return
        }

        val now = ZonedDateTime.ofInstant(clockProvider.now(), clockProvider.zoneId())
        if (!planned.notifyAt.isAfter(now)) {
            cancel()
            return
        }

        val delayMinutes = Duration.between(now, planned.notifyAt).toMinutes().coerceAtLeast(1)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .build()

        val closeHm = planned.close.toString().let { if (it.length >= 5) it.substring(0, 5) else it }

        val request = OneTimeWorkRequestBuilder<PoolReminderWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag(TAG_WORK)
            .setInputData(
                workDataOf(
                    PoolReminderWorker.KEY_POOL_ID to planned.poolId,
                    PoolReminderWorker.KEY_POOL_NAME to planned.poolName,
                    PoolReminderWorker.KEY_CLOSE to closeHm,
                    PoolReminderWorker.KEY_LEAD_MINUTES to planned.leadMinutes,
                ),
            )
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "scheduled ${planned.poolName} close=${planned.close} notify=${planned.notifyAt} in ${delayMinutes}m",
            )
        }
    }

    private fun planFromTomorrow(): PlannedPoolReminder? {
        val zone = clockProvider.zoneId()
        val now = ZonedDateTime.ofInstant(clockProvider.now(), zone)
        val catalog = poolsRepository.getCatalog()
        val tomorrow = now.toLocalDate().plusDays(1)
        val selection = EveningPoolSelector.selectNext(
            pools = catalog.pools,
            fromDate = tomorrow,
            nowTime = LocalTime.MIN,
            seasonYear = catalog.seasonYear ?: tomorrow.year,
        ) ?: return null
        return PlannedPoolReminder(
            poolId = selection.pool.id,
            poolName = selection.pool.name,
            close = selection.close,
            notifyAt = selection.date.atTime(selection.notifyAt).atZone(zone),
            leadMinutes = selection.leadMinutes,
            condition = selection.candidate.condition,
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK)
    }

    companion object {
        const val UNIQUE_WORK = "swimweek_evening_pool_reminder"
        const val TAG_WORK = "pool_reminder"
        private const val TAG = "PoolReminderScheduler"
    }
}
