package com.peal.appscheduler.ui.screens.deviceApps

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peal.appscheduler.R
import com.peal.appscheduler.core.presentation.util.ObserveAsEvents
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

    ObserveAsEvents(events = deviceAppsViewModel.effect) { effect ->
        when (effect) {
            is DeviceAppsContract.Effect.NavigateToScheduler -> {
                navigator.navigateToAppScheduler(effect.data.toScheduleAppInfoUI())
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
        deviceAppsScreenState.errorMessage != null -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = deviceAppsScreenState.errorMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        deviceAppsScreenState.deviceApps.isEmpty() -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        else -> InstalledAppsList(
            modifier = modifier,
            installedApps = deviceAppsScreenState.deviceApps,
            onIntent = onIntent
        )
    }
}

@Composable
fun InstalledAppsList(
    modifier: Modifier = Modifier,
    installedApps: List<DeviceAppInfo>,
    onIntent: (DeviceAppsContract.Intent) -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        items(
            items = installedApps,
            key = { it.packageName }
        ) { app ->
            InstalledAppItem(
                app,
                onClick = {
                    onIntent(DeviceAppsContract.Intent.OnNavigateScheduler(it))
                }
            )
        }
    }
}