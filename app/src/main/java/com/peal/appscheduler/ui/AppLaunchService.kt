package com.peal.appscheduler.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.peal.appscheduler.R


/**
 * Created by Peal Mazumder on 23/2/25.
 */
class AppLaunchService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val packageName = intent?.getStringExtra("PACKAGE_NAME")
        packageName?.let { launchApp(it) }

        val notification = NotificationCompat.Builder(this, "APP_SCHEDULER_CHANNEL")
            .setContentTitle("Launching Scheduled App")
            .setContentText("Starting $packageName...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification) // Ensures no crash

        stopSelf()
        return START_NOT_STICKY
    }

    private fun launchApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            "APP_SCHEDULER_CHANNEL",
            "App Scheduler",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }
}
