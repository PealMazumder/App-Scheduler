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
import com.peal.appscheduler.ui.screens.installedApps.InstalledAppsListScreen
import com.peal.appscheduler.ui.screens.installedApps.InstalledAppsViewModel


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
        startDestination = Screens.InstalledAppsListScreen
    ) {
        composable<Screens.InstalledAppsListScreen> {
            val installedAppsViewModel: InstalledAppsViewModel = hiltViewModel()
            val installedAppsScreenState by installedAppsViewModel.installedAppsScreenState.collectAsStateWithLifecycle()
            InstalledAppsListScreen(
                modifier,
                installedAppsScreenState
            )
        }
    }
}