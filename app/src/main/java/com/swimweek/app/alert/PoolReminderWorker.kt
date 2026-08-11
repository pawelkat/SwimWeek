package com.swimweek.app.alert

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.swimweek.app.BuildConfig
import com.swimweek.app.data.PreferencesStore
import com.swimweek.app.domain.EveningPoolSelector
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalTime

/**
 * Fires the evening pool reminder notification, then reschedules the next one.
 */
@HiltWorker
class PoolReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val notifier: PoolReminderNotifier,
    private val scheduler: PoolReminderScheduler,
    private val preferencesStore: PreferencesStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = preferencesStore.get()
            if (!prefs.eveningPoolReminderEnabled) {
                scheduler.cancel()
                return Result.success()
            }

            val poolId = inputData.getString(KEY_POOL_ID)
            val poolName = inputData.getString(KEY_POOL_NAME)
            val closeText = inputData.getString(KEY_CLOSE)
            val lead = inputData.getLong(KEY_LEAD_MINUTES, EveningPoolSelector.DEFAULT_LEAD_MINUTES)

            if (!poolId.isNullOrBlank() && !poolName.isNullOrBlank() && !closeText.isNullOrBlank()) {
                val close = EveningPoolSelector.parseHm(closeText) ?: LocalTime.MIDNIGHT
                notifier.showEveningReminder(
                    poolId = poolId,
                    poolName = poolName,
                    close = close,
                    leadMinutes = lead,
                )
            }

            // Plan the next evening (skip re-firing today)
            scheduler.scheduleNext(afterNotification = true)
            Result.success()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "pool reminder failed: ${e.message}")
            }
            // Still try to keep the cadence
            runCatching { scheduler.scheduleNext(afterNotification = true) }
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "PoolReminderWorker"
        const val KEY_POOL_ID = "pool_id"
        const val KEY_POOL_NAME = "pool_name"
        const val KEY_CLOSE = "close"
        const val KEY_LEAD_MINUTES = "lead_minutes"
    }
}
