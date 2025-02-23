package com.peal.appscheduler.ui.screens.home


/**
 * Created by Peal Mazumder on 23/2/25.
 */

sealed class HomeNavigationEvent {
    data object OnNavigateInstalledApps : HomeNavigationEvent()
}