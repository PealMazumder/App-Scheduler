package com.peal.appscheduler.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.peal.appscheduler.utils.AppConstant.EXTRA_PACKAGE_NAME
import com.peal.appscheduler.utils.AppConstant.EXTRA_SCHEDULE_ID


/**
 * Created by Peal Mazumder on 23/2/25.
 */

class AppSchedulerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val packageName = intent?.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1)

        val serviceIntent = Intent(context, AppLaunchService::class.java).apply {
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context?.startForegroundService(serviceIntent)
        } else {
            context?.startService(serviceIntent)
        }
    }
}