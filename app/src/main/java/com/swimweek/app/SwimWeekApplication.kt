package com.swimweek.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.swimweek.app.alert.PoolReminderNotifier
import com.swimweek.app.alert.PoolReminderScheduler
import com.swimweek.app.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Schedules background swim sync + midnight rollover
 * + evening pool close reminder.
 */
@HiltAndroidApp
class SwimWeekApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var poolReminderScheduler: PoolReminderScheduler

    @Inject
    lateinit var poolReminderNotifier: PoolReminderNotifier

    override fun onCreate() {
        super.onCreate()
        // Hilt injection is available after super.onCreate() for @HiltAndroidApp
        poolReminderNotifier.ensureChannel()
        syncScheduler.ensureScheduled()
        poolReminderScheduler.ensureScheduled()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
