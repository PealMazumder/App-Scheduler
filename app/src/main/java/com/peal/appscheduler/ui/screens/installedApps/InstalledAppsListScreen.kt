package com.peal.appscheduler.ui.screens.installedApps

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.peal.appscheduler.domain.model.InstalledAppInfo
import com.peal.appscheduler.ui.common.CommonCircularProgressIndicator


/**
 * Created by Peal Mazumder on 22/2/25.
 */

@Composable
fun InstalledAppsListScreen(
    modifier: Modifier = Modifier,
    installedAppsScreenState: InstalledAppsScreenState,
    onNavigationEvent: (DeviceAppsNavigationEvent) -> Unit,
) {
    when {
        installedAppsScreenState.isLoading -> CommonCircularProgressIndicator(modifier = modifier)
        installedAppsScreenState.installedApps.isNotEmpty() -> InstalledAppsList(
            modifier = modifier,
            installedApps = installedAppsScreenState.installedApps,
            onNavigationEvent = onNavigationEvent
        )
    }
}

@Composable
fun InstalledAppsList(
    modifier: Modifier,
    installedApps: List<InstalledAppInfo>,
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