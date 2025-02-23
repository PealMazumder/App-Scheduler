package com.peal.appscheduler.ui.screens.deviceApps

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.peal.appscheduler.domain.model.DeviceAppInfo
import com.peal.appscheduler.ui.common.CommonCircularProgressIndicator


/**
 * Created by Peal Mazumder on 22/2/25.
 */

@Composable
fun DeviceAppsListScreen(
    modifier: Modifier = Modifier,
    deviceAppsScreenState: DeviceAppsScreenState,
    onNavigationEvent: (DeviceAppsNavigationEvent) -> Unit,
) {
    when {
        deviceAppsScreenState.isLoading -> CommonCircularProgressIndicator(modifier = modifier)
        deviceAppsScreenState.deviceApps.isNotEmpty() -> InstalledAppsList(
            modifier = modifier,
            installedApps = deviceAppsScreenState.deviceApps,
            onNavigationEvent = onNavigationEvent
        )
    }
}

@Composable
fun InstalledAppsList(
    modifier: Modifier,
    installedApps: List<DeviceAppInfo>,
    onNavigationEvent: (DeviceAppsNavigationEvent) -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        items(installedApps) { app ->
            InstalledAppItem(
                app,
                onNavigate = {
                    onNavigationEvent(DeviceAppsNavigationEvent.OnNavigateScheduler)
                }
            )
        }
    }
}