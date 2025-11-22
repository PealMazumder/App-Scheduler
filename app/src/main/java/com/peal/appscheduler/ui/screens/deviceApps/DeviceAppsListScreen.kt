package com.peal.appscheduler.ui.screens.deviceApps

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peal.appscheduler.domain.model.DeviceAppInfo
import com.peal.appscheduler.ui.shared.components.CommonCircularProgressIndicator
import com.peal.appscheduler.ui.shared.navigation.Navigator
import com.peal.appscheduler.ui.shared.navigation.navigateToAppScheduler


/**
 * Created by Peal Mazumder on 22/2/25.
 */


@Composable
fun DeviceAppsListScreenRoute(
    modifier: Modifier = Modifier,
    navigator: Navigator,
    deviceAppsViewModel: DeviceAppsViewModel = hiltViewModel(),
) {
    val deviceAppsScreenState by deviceAppsViewModel.deviceAppsScreenState.collectAsStateWithLifecycle()

    LaunchedEffect(
        key1 = Unit
    ) {
        deviceAppsViewModel.effect.collect { effect ->
            when (effect) {
                is DeviceAppsContract.Effect.NavigateToScheduler -> {
                    navigator.navigateToAppScheduler(effect.data.toScheduleAppInfoUI())
                }
            }
        }
    }

    DeviceAppsListScreen(
        modifier = modifier,
        deviceAppsScreenState = deviceAppsScreenState,
        onIntent = deviceAppsViewModel::onIntent
    )
}
@Composable
fun DeviceAppsListScreen(
    modifier: Modifier = Modifier,
    deviceAppsScreenState: DeviceAppsContract.State,
    onIntent: (DeviceAppsContract.Intent) -> Unit,
) {
    when {
        deviceAppsScreenState.isLoading -> CommonCircularProgressIndicator(modifier = modifier)
        deviceAppsScreenState.deviceApps.isNotEmpty() -> InstalledAppsList(
            modifier = modifier,
            installedApps = deviceAppsScreenState.deviceApps,
            onIntent = onIntent
        )
    }
}

@Composable
fun InstalledAppsList(
    modifier: Modifier,
    installedApps: List<DeviceAppInfo>,
    onIntent: (DeviceAppsContract.Intent) -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        items(installedApps) { app ->
            InstalledAppItem(
                app,
                onClick = {
                    onIntent(DeviceAppsContract.Intent.OnNavigateScheduler(it))
                }
            )
        }
    }
}