package com.peal.appscheduler.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peal.appscheduler.ui.mappers.toScheduleAppInfoUi
import com.peal.appscheduler.domain.usecase.GetScheduledAppUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 23/2/25.
 */

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getScheduledAppUseCase: GetScheduledAppUseCase,
    @ApplicationContext private val context: Context,
): ViewModel() {
    private val _homeState = MutableStateFlow(HomeContract.State())
    val homeState: StateFlow<HomeContract.State> = _homeState

    private val _homeEffect = Channel<HomeContract.Effect>(Channel.BUFFERED)
    val homeEffect = _homeEffect.receiveAsFlow()


    init {
        fetchScheduledApps()
    }

    private fun fetchScheduledApps() {
        viewModelScope.launch {
            getScheduledAppUseCase().collectLatest { scheduledApps ->
                // Mapping loads each app's icon via PackageManager, which is blocking I/O -
                // keep it off the main thread.
                val scheduleAppInfos = withContext(Dispatchers.IO) {
                    scheduledApps.map { it.toScheduleAppInfoUi(context) }
                }
                _homeState.update {
                    it.copy(
                        isLoading = false,
                        scheduledApps = scheduleAppInfos
                    )
                }
            }
        }
    }

    fun onIntent(intent: HomeContract.Intent) {
        when (intent) {
            HomeContract.Intent.OnNavigateInstalledApps -> {
                viewModelScope.launch {
                    _homeEffect.send(HomeContract.Effect.NavigateToInstalledApps)
                }
            }
            is HomeContract.Intent.OnNavigateScheduledApps -> {
                viewModelScope.launch {
                    _homeEffect.send(HomeContract.Effect.NavigateToScheduledApps(intent.appInfo))
                }
            }
        }
    }
}