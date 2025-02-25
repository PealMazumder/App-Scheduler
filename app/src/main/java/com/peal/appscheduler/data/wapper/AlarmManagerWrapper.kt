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

    fun scheduleApp(packageName: String, scheduleTime: Long, scheduleId: Long): Result<Unit> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    throw SecurityException("App doesn't have SCHEDULE_EXACT_ALARM permission")
                }
            }

            val intent = Intent(context, AppSchedulerReceiver::class.java).apply {
                putExtra("PACKAGE_NAME", packageName)
                putExtra("ALARM_ID", scheduleId)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context, scheduleId.toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExact(AlarmManager.RTC_WAKEUP, scheduleTime, pendingIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cancelSchedule(packageName: String, scheduleId: Long): Result<Unit> {
        return try {
            val intent = Intent(context, AppSchedulerReceiver::class.java).apply {
                putExtra("PACKAGE_NAME", packageName)
                putExtra("ALARM_ID", scheduleId)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context, scheduleId.toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.cancel(pendingIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateSchedule(packageName: String, newScheduleTime: Long, scheduleId: Long): Result<Unit> {
        return cancelSchedule(packageName, scheduleId).fold(
            onSuccess = {
                try {
                    scheduleApp(packageName, newScheduleTime, scheduleId)
                } catch (scheduleError: Exception) {
                    val rollbackResult = try {
                        scheduleApp(packageName, newScheduleTime, scheduleId)
                        Result.success(Unit)
                    } catch (rollbackError: Exception) {
                        Result.failure(Exception("Update and rollback both failed: ${scheduleError.message}, ${rollbackError.message}"))
                    }
                    rollbackResult
                }
            },
            onFailure = { cancelError ->
                Result.failure(Exception("Failed to cancel schedule: ${cancelError.message}"))
            }
        )
    }
}
