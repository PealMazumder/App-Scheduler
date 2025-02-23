package com.peal.appscheduler.ui.screens.deviceApps

import com.peal.appscheduler.domain.model.DeviceAppInfo


/**
 * Created by Peal Mazumder on 23/2/25.
 */

sealed class DeviceAppsNavigationEvent {
    data class OnNavigateScheduler(val data: DeviceAppInfo) : DeviceAppsNavigationEvent()
}