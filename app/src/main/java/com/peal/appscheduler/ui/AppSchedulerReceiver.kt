package com.peal.appscheduler.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build


/**
 * Created by Peal Mazumder on 23/2/25.
 */

class AppSchedulerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val packageName = intent?.getStringExtra("PACKAGE_NAME") ?: return

        val serviceIntent = Intent(context, AppLaunchService::class.java).apply {
            putExtra("PACKAGE_NAME", packageName)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context?.startForegroundService(serviceIntent)
        } else {
            context?.startService(serviceIntent)
        }
    }
}