package com.example.connect.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.connect.MainActivity
import com.example.connect.R

object SystemNotificationHelper {

    private const val CHANNEL_ID   = "connect_transfers"
    private const val CHANNEL_NAME = "File Transfers"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for incoming file transfers"
        }
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        mgr.createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true   // granted implicitly on older versions
    }

    /**
     * Posts a system notification for an incoming file.
     * Tapping it opens MainActivity — the in-app nav to Files
     * is handled by the existing pendingNavigationRoute mechanism.
     */
    fun postFileNotification(
        context:    Context,
        notifId:    Int,      // use transferId.hashCode()
        senderName: String,
        fileName:   String
    ) {
        if (!hasPermission(context)) return

        // Tap → open app (MainActivity picks up pendingNavigationRoute)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)   // swap for a proper icon if you have one
            .setContentTitle("Incoming file from $senderName")
            .setContentText(fileName)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notifId, notification)
    }
}