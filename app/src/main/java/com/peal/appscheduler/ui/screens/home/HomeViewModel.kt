package com.peal.appscheduler.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peal.appscheduler.domain.mappers.toScheduleAppInfoUi
import com.peal.appscheduler.domain.usecase.GetScheduledAppUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    private val _homeEffect = MutableSharedFlow<HomeContract.Effect>()
    val homeEffect get() = _homeEffect.asSharedFlow()


    init {
        fetchScheduledApps()
    }

    private fun fetchScheduledApps() {
        viewModelScope.launch {
            getScheduledAppUseCase().collectLatest { scheduledApps ->
                _homeState.update {
                    it.copy(
                        isLoading = false,
                        scheduledApps = scheduledApps.map { it.toScheduleAppInfoUi(context) }
                    )
                }
            }
        }
    }

    fun onIntent(intent: HomeContract.Intent) {
        when (intent) {
            HomeContract.Intent.OnNavigateInstalledApps -> {
                viewModelScope.launch {
                    _homeEffect.emit(HomeContract.Effect.NavigateToInstalledApps)
                }
            }
            is HomeContract.Intent.OnNavigateScheduledApps -> {
                viewModelScope.launch {
                    _homeEffect.emit(HomeContract.Effect.NavigateToScheduledApps(intent.appInfo))
                }
            }
        }
    }
}