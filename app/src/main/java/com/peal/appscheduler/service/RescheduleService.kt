package com.peal.appscheduler.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.peal.appscheduler.R
import com.peal.appscheduler.domain.enums.ScheduleStatus
import com.peal.appscheduler.domain.repository.AlarmManagerRepository
import com.peal.appscheduler.domain.repository.ScheduleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 9/3/25.
 */

@AndroidEntryPoint
class RescheduleService : Service() {

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    @Inject
    lateinit var alarmManagerRepository: AlarmManagerRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val notificationId = 1
    private val notificationChannelId = "RescheduleServiceChannel"

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        reschedulePendingApps()
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle(getString(R.string.rescheduling_apps))
            .setContentText(getString(R.string.your_app_is_rescheduling_scheduled_tasks))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            )
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Reschedule Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for rescheduling tasks"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun reschedulePendingApps() {
        val currentTime = System.currentTimeMillis()

        serviceScope.launch {
            try {
                // Use .first() for a one-shot read so stopSelf() is reachable.
                // .collect {} on a Room observable Flow never completes.
                val schedules = scheduleRepository
                    .getScheduledAppsToReschedule(ScheduleStatus.SCHEDULED.name)
                    .first()

                schedules.forEach { schedule ->
                    if (schedule.scheduledTime < currentTime) {
                        scheduleRepository.updateScheduleStatus(
                            schedule.id,
                            ScheduleStatus.FAILED.name
                        )
                    } else {
                        alarmManagerRepository.scheduleApp(
                            schedule.packageName,
                            schedule.scheduledTime,
                            schedule.id
                        )
                    }
                    Log.d("RescheduleService", "Rescheduled app launch for package: ${schedule.packageName} at ${schedule.scheduledTime}")
                }
            } catch (e: Exception) {
                Log.e("RescheduleService", "Error during rescheduling: ${e.message}")
            } finally {
                stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

