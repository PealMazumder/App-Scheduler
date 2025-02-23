package com.peal.appscheduler.ui.navigation

import androidx.navigation.NavHostController
import com.peal.appscheduler.ui.screens.deviceApps.DeviceAppsNavigationEvent


/**
 * Created by Peal Mazumder on 23/2/25.
 */

class DeviceAppsScreenNavigation(private val navController: NavHostController) {
    fun onNavigation(deviceAppsNavigationEvent: DeviceAppsNavigationEvent) {
        when (deviceAppsNavigationEvent) {
            is DeviceAppsNavigationEvent.OnNavigateScheduler -> {
                navController.navigate(Screens.AppSchedulerScreen)
            }
        }
    }
}