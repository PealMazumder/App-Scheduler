package com.peal.appscheduler.ui.screens.deviceApps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peal.appscheduler.R
import com.peal.appscheduler.core.presentation.util.ObserveAsEvents
import com.peal.appscheduler.domain.model.DeviceAppInfo
import com.peal.appscheduler.ui.shared.components.AppTopBar
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
        onIntent = deviceAppsViewModel::onIntent,
        onBack = { navigator.goBack() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceAppsListScreen(
    modifier: Modifier = Modifier,
    deviceAppsScreenState: DeviceAppsContract.State,
    onIntent: (DeviceAppsContract.Intent) -> Unit,
    onBack: () -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.choose_app_title),
                onBack = onBack,
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!deviceAppsScreenState.isLoading && deviceAppsScreenState.errorMessage == null &&
                deviceAppsScreenState.deviceApps.isNotEmpty()
            ) {
                AppSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            when {
                deviceAppsScreenState.isLoading -> CommonCircularProgressIndicator(
                    modifier = Modifier.fillMaxSize()
                )

                deviceAppsScreenState.errorMessage != null -> ErrorState(
                    message = deviceAppsScreenState.errorMessage,
                    onRetry = { onIntent(DeviceAppsContract.Intent.Retry) },
                    modifier = Modifier.fillMaxSize()
                )

                deviceAppsScreenState.deviceApps.isEmpty() -> EmptyState(
                    title = stringResource(R.string.no_apps_found_title),
                    message = stringResource(R.string.no_apps_installed_message),
                    modifier = Modifier.fillMaxSize()
                )

                else -> {
                    val filteredApps = remember(query, deviceAppsScreenState.deviceApps) {
                        if (query.isBlank()) {
                            deviceAppsScreenState.deviceApps
                        } else {
                            deviceAppsScreenState.deviceApps.filter {
                                it.name.contains(query, ignoreCase = true)
                            }
                        }
                    }

                    if (filteredApps.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.no_apps_found_title),
                            message = stringResource(R.string.no_apps_found_message),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        InstalledAppsList(
                            installedApps = filteredApps,
                            onIntent = onIntent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.search_apps_placeholder)) },
        leadingIcon = {
            Icon(imageVector = Icons.Filled.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.clear_search)
                    )
                }
            }
        },
        singleLine = true,
        shape = OutlinedTextFieldDefaults.shape,
    )
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.something_went_wrong),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 24.dp)) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.retry),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun InstalledAppsList(
    modifier: Modifier = Modifier,
    installedApps: List<DeviceAppInfo>,
    onIntent: (DeviceAppsContract.Intent) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
