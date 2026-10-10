package com.swimweek.app.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.swimweek.app.BuildConfig
import com.swimweek.app.data.PreferencesStore
import com.swimweek.app.domain.WeeklySwimSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Publishes the current weekly total + target to the paired watch via the Wearable Data Layer.
 * The `:wear` complication reads this DataItem. Only derived totals are shared (no sessions,
 * no raw Health Connect records). Failures (no watch, no Play services) are swallowed.
 */
@Singleton
class WearSyncPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesStore: PreferencesStore,
) {
    suspend fun publish(summary: WeeklySwimSummary) {
        runCatching {
            val prefs = preferencesStore.get()
            val request = PutDataMapRequest.create(WearDataContract.PATH_WEEKLY).apply {
                dataMap.putDouble(WearDataContract.KEY_DISTANCE_M, summary.totalDistanceMeters)
                dataMap.putDouble(WearDataContract.KEY_TARGET_M, prefs.weeklyTargetMeters)
                dataMap.putString(WearDataContract.KEY_UNIT, prefs.distanceUnit.name)
                dataMap.putInt(WearDataContract.KEY_SESSIONS, summary.sessionCount)
                dataMap.putLong(WearDataContract.KEY_UPDATED_AT, summary.lastSyncedAt.toEpochMilli())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
        }.onFailure { e ->
            if (BuildConfig.DEBUG) Log.d(TAG, "wear publish skipped: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "WearSyncPublisher"
    }
}
