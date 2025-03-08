package com.peal.appscheduler.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.peal.appscheduler.ui.screens.deviceApps.DeviceAppsListScreen
import com.peal.appscheduler.ui.screens.deviceApps.DeviceAppsNavigationEvent
import com.peal.appscheduler.ui.screens.deviceApps.DeviceAppsViewModel
import com.peal.appscheduler.ui.screens.deviceApps.SharedDeviceAppViewModel
import com.peal.appscheduler.ui.screens.home.HomeNavigationEvent
import com.peal.appscheduler.ui.screens.home.HomeScreen
import com.peal.appscheduler.ui.screens.home.HomeViewModel
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
    val sharedViewModel: SharedDeviceAppViewModel = hiltViewModel()

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
                onNavigationEvent = { event ->
                    if (event is HomeNavigationEvent.OnNavigateScheduledApps) {
                        sharedViewModel.setSelectedAppInfo(event.appInfo)
                    }
                    navigation.onNavigation(event)
                }
            )
        }

        composable<Screens.DeviceAppsListScreen> {
            val deviceAppsViewModel: DeviceAppsViewModel = hiltViewModel()
            val deviceAppsScreenState by deviceAppsViewModel.deviceAppsScreenState.collectAsStateWithLifecycle()
            val navigation = DeviceAppsScreenNavigation(navController)
            DeviceAppsListScreen(
                modifier,
                deviceAppsScreenState,
                onNavigationEvent = { event ->
                    if (event is DeviceAppsNavigationEvent.OnNavigateScheduler) {
                        sharedViewModel.setSelectedAppInfo(event.data.toScheduleAppInfoUI())
                    }
                    navigation.onNavigation(event)
                }
            )
        }

        composable<Screens.AppSchedulerScreen> {
            val schedulerViewModel: SchedulerViewModel = hiltViewModel()
            val schedulerScreenState by schedulerViewModel.schedulerScreenState.collectAsStateWithLifecycle()
            val appScheduleInfo by sharedViewModel.appScheduleInfo.collectAsStateWithLifecycle()

            LaunchedEffect(appScheduleInfo) {
                appScheduleInfo?.let {
                    schedulerViewModel.updateAppInfo(it)
                }
            }
            SchedulerScreen(
                modifier,
                schedulerScreenState,
                onIntent = { schedulerViewModel.handleIntent(it) }
            )
        }
    }
}