package com.peal.appscheduler.ui.shared.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.peal.appscheduler.ui.screens.deviceApps.DeviceAppsListScreenRoute
import com.peal.appscheduler.ui.screens.home.HomeScreenRoute
import com.peal.appscheduler.ui.screens.schedule.SchedulerScreenRoute


/**
 * Created by Peal Mazumder on 22/2/25.
 */

@Composable
fun AppSchedulerNavHost(
    modifier: Modifier = Modifier
) {
    val navigationState = rememberNavigationState(startRoute = HomeScreen)

    val navigator = remember(navigationState) { Navigator(navigationState) }

    val entryProvider = entryProvider {
        entry<HomeScreen> {
            HomeScreenRoute(
                modifier = modifier,
                navigator = navigator,
            )
        }

        entry<DeviceAppsListScreen> {
            DeviceAppsListScreenRoute(
                modifier = modifier,
                navigator = navigator,
            )
        }

        entry<AppSchedulerScreen> { key ->
            SchedulerScreenRoute(
                modifier = modifier,
                route = key
            )
        }
    }

    NavDisplay(
        entries = navigationState.toEntries(entryProvider),
        onBack = { navigator.goBack() },
        sceneStrategy = remember { DialogSceneStrategy() }
    )
}