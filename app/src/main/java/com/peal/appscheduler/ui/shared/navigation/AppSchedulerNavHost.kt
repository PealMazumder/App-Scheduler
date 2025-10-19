package com.peal.appscheduler.ui.shared.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.peal.appscheduler.ui.screens.deviceApps.DeviceAppsListScreenRoute
import com.peal.appscheduler.ui.screens.home.HomeScreenRoute
import com.peal.appscheduler.ui.screens.schedule.SchedulerScreenRoute
import com.peal.appscheduler.ui.shared.viewModel.SharedDeviceAppViewModel


/**
 * Created by Peal Mazumder on 22/2/25.
 */

@Composable
fun AppSchedulerNavHost(
    modifier: Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val sharedViewModel: SharedDeviceAppViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = Screens.HomeScreen
    ) {

        composable<Screens.HomeScreen> {
            HomeScreenRoute(
                modifier = modifier,
                navController = navController,
            )
        }

        composable<Screens.DeviceAppsListScreen> {
            DeviceAppsListScreenRoute(
                modifier = modifier,
                navController = navController,
            )
        }

        composable<Screens.AppSchedulerScreen> { backStackEntry ->
            val route = backStackEntry.toRoute<Screens.AppSchedulerScreen>()
            SchedulerScreenRoute(
                modifier = modifier,
                navController = navController,
                route = route
            )
        }
    }
}