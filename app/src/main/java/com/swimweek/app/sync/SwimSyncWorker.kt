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

@HiltWorker
class SwimSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: SwimDistanceRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reasonName = inputData.getString(KEY_REASON) ?: SyncReason.PERIODIC.name
        val reason = runCatching { SyncReason.valueOf(reasonName) }.getOrDefault(SyncReason.PERIODIC)
        return try {
            repository.sync(reason)
            Result.success()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "sync failed: ${e.message}")
            }
            // Transient — retry with backoff
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_REASON = "reason"
        private const val TAG = "SwimSyncWorker"
    }
}
