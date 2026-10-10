package com.swimweek.wear.data

import android.content.ComponentName
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.swimweek.wear.complication.WeeklySwimComplicationService

/** Phone published a new weekly total → ask the watch face to re-request our complication. */
class WeeklyDataListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val relevant = dataEvents.any {
            it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WearDataContract.PATH_WEEKLY
        }
        if (relevant) {
            ComplicationDataSourceUpdateRequester.create(
                this,
                ComponentName(this, WeeklySwimComplicationService::class.java),
            ).requestUpdateAll()
        }
    }
}
