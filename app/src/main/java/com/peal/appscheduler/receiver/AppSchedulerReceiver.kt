package com.peal.appscheduler.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.peal.appscheduler.domain.utils.isAndroidOOrLater
import com.peal.appscheduler.service.AppLaunchService
import com.peal.appscheduler.service.RescheduleService
import com.peal.appscheduler.utils.AppConstant.ACTION_SCHEDULE_APP
import com.peal.appscheduler.utils.AppConstant.EXTRA_PACKAGE_NAME
import com.peal.appscheduler.utils.AppConstant.EXTRA_SCHEDULE_ID


/**
 * Created by Peal Mazumder on 23/2/25.
 */

class AppSchedulerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, RescheduleService::class.java)
            if (isAndroidOOrLater()) {
                context?.startForegroundService(serviceIntent)
            } else {
                context?.startService(serviceIntent)
            }

            return
        } else if (intent?.action == ACTION_SCHEDULE_APP) {
            // Validate this broadcast came from our own AlarmManager PendingIntent,
            // not from an external app spoofing the action.
            if (context != null && intent.`package` != null && intent.`package` != context.packageName) {
                return
            }

            val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
            val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1)

            val serviceIntent = Intent(context, AppLaunchService::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            }

            if (isAndroidOOrLater()) {
                context?.startForegroundService(serviceIntent)
            } else {
                context?.startService(serviceIntent)
            }
        }
    }
}