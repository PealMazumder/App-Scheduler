package com.peal.appscheduler.ui.screens.installedApps

import com.peal.appscheduler.ui.screens.home.HomeNavigationEvent


/**
 * Created by Peal Mazumder on 23/2/25.
 */

sealed class DeviceAppsNavigationEvent {
    data object OnNavigateScheduler : DeviceAppsNavigationEvent()
}