package com.swimweek.app.alert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.swimweek.app.MainActivity
import com.swimweek.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PoolReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.alert_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    fun canPost(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showEveningReminder(
        poolId: String,
        poolName: String,
        close: LocalTime,
        leadMinutes: Long,
    ) {
        ensureChannel()
        if (!canPost()) return

        val timeFmt = DateTimeFormatter.ofPattern("H:mm")
        val closeText = close.format(timeFmt)
        val title = context.getString(R.string.alert_evening_title, poolName)
        val body = context.getString(
            R.string.alert_evening_body,
            closeText,
            leadMinutes,
        )

        val openApp = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_RECOMMENDATIONS
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_FROM_ALERT, true)
                putExtra(EXTRA_OPEN_RECOMMENDATIONS, true)
                putExtra(EXTRA_POOL_ID, poolId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_swim)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied at runtime
        }
    }

    companion object {
        const val CHANNEL_ID = "evening_pool_reminder"
        const val NOTIFICATION_ID = 7101
        const val REQUEST_OPEN = 7102
        const val EXTRA_FROM_ALERT = "from_pool_alert"
        const val EXTRA_OPEN_RECOMMENDATIONS = "open_pool_recommendations"
        const val EXTRA_POOL_ID = "pool_id"
        const val ACTION_OPEN_RECOMMENDATIONS = "com.swimweek.app.OPEN_POOL_RECOMMENDATIONS"
    }
}
