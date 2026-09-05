package com.peal.appscheduler.ui.screens.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peal.appscheduler.ui.shared.navigation.Navigator
import com.peal.appscheduler.ui.shared.navigation.navigateToAppScheduler
import com.peal.appscheduler.ui.shared.navigation.navigateToDeviceAppsList
import com.peal.appscheduler.core.presentation.util.ObserveAsEvents


/**
 * Created by Peal Mazumder on 23/2/25.
 */


@Composable
fun HomeScreenRoute(
    modifier: Modifier = Modifier,
    navigator: Navigator,
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
    val homeScreenState by homeViewModel.homeState.collectAsStateWithLifecycle()

    ObserveAsEvents(events = homeViewModel.homeEffect) { effect ->
        when (effect) {
            is HomeContract.Effect.NavigateToInstalledApps -> {
                navigator.navigateToDeviceAppsList()
            }

            is HomeContract.Effect.NavigateToScheduledApps -> {
                navigator.navigateToAppScheduler(effect.appInfo)
            }
        }
    }

    HomeScreen(
        modifier = modifier,
        homeScreenState = homeScreenState,
        onIntent = homeViewModel::onIntent
    )
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    homeScreenState: HomeContract.State,
    onIntent: (HomeContract.Intent) -> Unit
) {
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onIntent(HomeContract.Intent.OnNavigateInstalledApps) }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add"
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(
                items = homeScreenState.scheduledApps,
                key = { it.id }
            ) { schedule ->
                ScheduledAppItem(
                    app = schedule,
                    onClick = {
                        onIntent(HomeContract.Intent.OnNavigateScheduledApps(schedule))
                    }
                )
            }
        }
    }
}