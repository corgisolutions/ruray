package io.github.corgisolutions.ruray.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.corgisolutions.ruray.MainActivity
import io.github.corgisolutions.ruray.R
import io.github.corgisolutions.ruray.utils.LocaleHelper

object UpdateNotificationManager {

    private const val CHANNEL_ID = "update_channel"
    private const val NOTIFICATION_ID = 9999

    fun createChannel(context: Context) {
        val localizedContext = LocaleHelper.onAttach(context)
        val channel = NotificationChannel(
            CHANNEL_ID,
            localizedContext.getString(R.string.update_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = localizedContext.getString(R.string.update_channel_desc)
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showUpdateNotification(context: Context, newVersion: String) {
        createChannel(context)

        val localizedContext = LocaleHelper.onAttach(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("show_update_dialog", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(localizedContext.getString(R.string.update_notification_title))
            .setContentText(localizedContext.getString(R.string.update_notification_text, newVersion))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
    }
}