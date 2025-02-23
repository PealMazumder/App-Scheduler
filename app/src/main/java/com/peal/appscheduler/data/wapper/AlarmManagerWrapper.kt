package com.peal.appscheduler.data.wapper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.peal.appscheduler.ui.AppSchedulerReceiver
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 23/2/25.
 */

class AlarmManagerWrapper @Inject constructor(
    private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleApp(packageName: String, scheduleTime: Long) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                throw SecurityException("App doesn't have SCHEDULE_EXACT_ALARM permission")
            }
        }

        val intent = Intent(context, AppSchedulerReceiver::class.java).apply {
            putExtra("PACKAGE_NAME", packageName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, scheduleTime, pendingIntent)
    }


    fun cancelSchedule(packageName: String) {
        val intent = Intent(context, AppSchedulerReceiver::class.java).apply {
            putExtra("PACKAGE_NAME", packageName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
    }
}
