package com.peal.appscheduler.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peal.appscheduler.R
import com.peal.appscheduler.core.presentation.util.ObserveAsEvents
import com.peal.appscheduler.ui.shared.components.AppTopBar
import com.peal.appscheduler.ui.shared.navigation.Navigator
import com.peal.appscheduler.ui.shared.navigation.navigateToAppScheduler
import com.peal.appscheduler.ui.shared.navigation.navigateToDeviceAppsList

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    homeScreenState: HomeContract.State,
    onIntent: (HomeContract.Intent) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = stringResource(R.string.home_title)) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onIntent(HomeContract.Intent.OnNavigateInstalledApps) }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.home_empty_cta)
                )
            }
        }
    ) { paddingValues ->
        if (homeScreenState.scheduledApps.isEmpty()) {
            HomeEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onScheduleClick = { onIntent(HomeContract.Intent.OnNavigateInstalledApps) }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingValues.calculateTopPadding() + 12.dp,
                    bottom = paddingValues.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
}

@Composable
private fun HomeEmptyState(
    onScheduleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.clock_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            text = stringResource(R.string.home_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(
            onClick = onScheduleClick,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text(stringResource(R.string.home_empty_cta))
        }
    }
}
