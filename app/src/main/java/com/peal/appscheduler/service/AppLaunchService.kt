package com.peal.appscheduler.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 23/2/25.
 */
@AndroidEntryPoint
class AppLaunchService : Service() {
    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val notificationId = 1
    private var notificationBuilder: NotificationCompat.Builder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationBuilder = NotificationCompat.Builder(this, APP_SCHEDULER_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val packageName = intent?.getStringExtra(EXTRA_PACKAGE_NAME)
        val scheduleId = intent?.getLongExtra(EXTRA_SCHEDULE_ID, -1L)
        Log.d("AppLaunchService", "Starting app: $packageName, scheduleId: $scheduleId")

        if (packageName.isNullOrEmpty()) {
            Log.e("AppLaunchService", "Package name is null or empty")
            showNotification(
                getString(R.string.launching_scheduled_app),
                getString(R.string.processing_request)
            )
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isAppInstalled(packageName)) {
            Log.e("AppLaunchService", "App is not installed: $packageName")
            showNotification(
                getString(R.string.app_not_installed),
                getString(R.string.is_not_installed_on_this_device, packageName)
            )
            stopSelf()
            return START_NOT_STICKY
        }

        launchApp(packageName)

        if (scheduleId != null && scheduleId != -1L) {
            serviceScope.launch {
                try {
                    scheduleRepository.updateScheduleStatus(
                        scheduleId,
                        ScheduleStatus.EXECUTED.name
                    )
                } catch (e: Exception) {
                    Log.e("AppLaunchService", "Failed to update schedule status: ${e.message}")
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun launchApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                Log.e("AppLaunchService", "Unable to find launch intent for package: $packageName")
                showNotification(
                    getString(R.string.launch_failed),
                    getString(R.string.could_not_be_started, packageName)
                )
                return
            }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } catch (e: ActivityNotFoundException) {
            Log.e("AppLaunchService", getString(R.string.failed_to_launch_app, e.message))
            showNotification(
                title = getString(R.string.launch_failed),
                content = getString(R.string.could_not_be_started, packageName)
            )
        }
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun showNotification(title: String, content: String) {
        notificationBuilder?.setContentTitle(title)
            ?.setContentText(content)
            ?.setStyle(NotificationCompat.BigTextStyle().bigText(content))

        notificationBuilder?.let {
            startForeground(notificationId, it.build())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                APP_SCHEDULER_CHANNEL,
                getString(R.string.app_scheduler),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_for_app_launch_notifications)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}

