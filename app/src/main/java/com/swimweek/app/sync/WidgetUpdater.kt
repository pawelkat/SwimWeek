package com.swimweek.app.sync

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.swimweek.app.widget.SwimWeekWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Triggers Glance widget redraws after cache writes.
 * Called from repository / future WorkManager — not from pure aggregation.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun updateAll() {
        SwimWeekWidget().updateAll(context)
    }
}
