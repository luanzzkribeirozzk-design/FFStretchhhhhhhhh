package com.lnstretch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

class StretchService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, "lnstretch_channel")
            .setContentTitle("LN Stretch Ativo")
            .setContentText("Tela esticada para Free Fire")
            .setSmallIcon(android.R.drawable.ic_menu_zoom)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "lnstretch_channel",
            "LN Stretch",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
