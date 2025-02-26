package com.peal.appscheduler.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.peal.appscheduler.R
import com.peal.appscheduler.domain.enums.ScheduleStatus
import com.peal.appscheduler.domain.repository.ScheduleRepository
import com.peal.appscheduler.utils.AppConstant.APP_SCHEDULER_CHANNEL
import com.peal.appscheduler.utils.AppConstant.EXTRA_PACKAGE_NAME
import com.peal.appscheduler.utils.AppConstant.EXTRA_SCHEDULE_ID
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 23/2/25.
 */
@AndroidEntryPoint
class AppLaunchService : Service() {
    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val packageName = intent?.getStringExtra(EXTRA_PACKAGE_NAME)
        val scheduleId = intent?.getLongExtra(EXTRA_SCHEDULE_ID, -1)
        Log.d("AppLaunchService", "Starting app: $packageName, scheduleId: $scheduleId")

        packageName?.let {
            launchApp(it)
            if (scheduleId != -1L) {
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        if (scheduleId != null) {
                            scheduleRepository.updateScheduleStatus(scheduleId, ScheduleStatus.EXECUTED.name)
                        }
                    } catch (e: Exception) {
                        Log.e("AppLaunchService", "Failed to update status: ${e.message}")
                    }
                }
            }
        }

        val notification = NotificationCompat.Builder(this, "APP_SCHEDULER_CHANNEL")
            .setContentTitle("Launching Scheduled App")
            .setContentText("Starting $packageName...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)

        stopSelf()
        return START_NOT_STICKY
    }

    private fun launchApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                APP_SCHEDULER_CHANNEL,
                "App Scheduler",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}

