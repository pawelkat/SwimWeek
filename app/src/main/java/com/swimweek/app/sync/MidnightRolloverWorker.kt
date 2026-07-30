package com.swimweek.app.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.swimweek.app.BuildConfig
import com.swimweek.app.health.SwimDistanceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Flex midnight job: force week-identity resync, then reschedule next midnight.
 * Not the sole authority — repository/widget also recompute week identity.
 */
@HiltWorker
class MidnightRolloverWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: SwimDistanceRepository,
    private val syncScheduler: SyncScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            repository.sync(SyncReason.MIDNIGHT)
            syncScheduler.scheduleMidnightRollover()
            Result.success()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "midnight rollover failed: ${e.message}")
            }
            // Still try to reschedule so we don't lose the cadence
            syncScheduler.scheduleMidnightRollover()
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "MidnightRolloverWorker"
    }
}
