package com.swimweek.wear.data

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/** Weekly total as published by the phone. Distances are meters; [unit] is the phone's display unit. */
data class WeeklySwim(
    val distanceMeters: Double,
    val targetMeters: Double,
    val unit: String,
    val sessions: Int,
    val updatedAtEpochMs: Long,
) {
    companion object {
        fun fromDataMap(map: DataMap) = WeeklySwim(
            distanceMeters = map.getDouble(WearDataContract.KEY_DISTANCE_M, 0.0),
            targetMeters = map.getDouble(WearDataContract.KEY_TARGET_M, 0.0),
            unit = map.getString(WearDataContract.KEY_UNIT) ?: "METERS",
            sessions = map.getInt(WearDataContract.KEY_SESSIONS, 0),
            updatedAtEpochMs = map.getLong(WearDataContract.KEY_UPDATED_AT, 0L),
        )

        /** Latest DataItem from any connected phone, or null if never synced. */
        suspend fun load(context: Context): WeeklySwim? = runCatching {
            val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME)
                .path(WearDataContract.PATH_WEEKLY).build()
            val buffer = Wearable.getDataClient(context)
                .getDataItems(uri, com.google.android.gms.wearable.DataClient.FILTER_LITERAL).await()
            try {
                buffer.maxByOrNull { DataMapItem.fromDataItem(it).dataMap.getLong(WearDataContract.KEY_UPDATED_AT, 0L) }
                    ?.let { fromDataMap(DataMapItem.fromDataItem(it).dataMap) }
            } finally {
                buffer.release()
            }
        }.getOrNull()
    }
}
