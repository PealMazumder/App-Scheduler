package com.peal.appscheduler.ui.screens.home

import com.peal.appscheduler.ui.model.ScheduleAppInfoUi


/**
 * Created by Peal Mazumder on 23/2/25.
 */

sealed class HomeNavigationEvent {
    data object OnNavigateInstalledApps : HomeNavigationEvent()
    data class OnNavigateScheduledApps(val appInfo: ScheduleAppInfoUi) : HomeNavigationEvent()
}