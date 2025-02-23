package com.peal.appscheduler.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.peal.appscheduler.ui.screens.home.HomeScreen
import com.peal.appscheduler.ui.screens.home.HomeViewModel
import com.peal.appscheduler.ui.screens.installedApps.InstalledAppsListScreen
import com.peal.appscheduler.ui.screens.installedApps.InstalledAppsViewModel
import com.peal.appscheduler.ui.screens.schedule.SchedulerScreen
import com.peal.appscheduler.ui.screens.schedule.SchedulerViewModel


/**
 * Created by Peal Mazumder on 22/2/25.
 */

@Composable
fun NavGraph(
    modifier: Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Screens.HomeScreen
    ) {

        composable<Screens.HomeScreen> {
            val homeViewModel: HomeViewModel = hiltViewModel()
            val homeScreenState by homeViewModel.homeState.collectAsStateWithLifecycle()
            val navigation = HomeScreenNavigation(navController)
            HomeScreen(
                modifier,
                homeScreenState,
                onNavigationEvent = {
                    navigation.onNavigation(it)
                }
            )
        }

        composable<Screens.InstalledAppsListScreen> {
            val installedAppsViewModel: InstalledAppsViewModel = hiltViewModel()
            val installedAppsScreenState by installedAppsViewModel.installedAppsScreenState.collectAsStateWithLifecycle()
            val navigation = DeviceAppsScreenNavigation(navController)
            InstalledAppsListScreen(
                modifier,
                installedAppsScreenState,
                onNavigationEvent = {
                    navigation.onNavigation(it)
                }
            )
        }

        composable<Screens.AppSchedulerScreen> {
            val schedulerViewModel: SchedulerViewModel = hiltViewModel()
            val schedulerScreenState by schedulerViewModel.schedulerScreenState.collectAsStateWithLifecycle()
            SchedulerScreen(
                modifier,
                schedulerScreenState
            )
        }
    }
}