package com.peal.appscheduler.ui.navigation

import androidx.navigation.NavHostController
import com.peal.appscheduler.ui.screens.home.HomeNavigationEvent


/**
 * Created by Peal Mazumder on 23/2/25.
 */

class HomeScreenNavigation(private val navController: NavHostController) {
    fun onNavigation(homeNavigationEvent: HomeNavigationEvent) {
        when (homeNavigationEvent) {
            is HomeNavigationEvent.OnNavigateInstalledApps -> {
                navController.navigate(Screens.InstalledAppsListScreen)
            }
        }
    }
}