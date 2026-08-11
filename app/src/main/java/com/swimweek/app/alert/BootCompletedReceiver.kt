package com.swimweek.app.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.swimweek.app.di.swimWeekEntryPoint

/**
 * Reschedule evening pool reminder after device reboot.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pending = goAsync()
        try {
            context.applicationContext.swimWeekEntryPoint()
                .poolReminderScheduler()
                .ensureScheduled()
        } finally {
            pending.finish()
        }
    }
}
