package com.peal.appscheduler.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peal.appscheduler.domain.mappers.toScheduleAppInfoUi
import com.peal.appscheduler.domain.usecase.GetScheduledAppUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
): ViewModel() {
    private val _homeState = MutableStateFlow(HomeScreenState())
    val homeState: StateFlow<HomeScreenState> = _homeState

    init {
        fetchScheduledApps()
    }

    private fun fetchScheduledApps() {
        viewModelScope.launch {
            getScheduledAppUseCase().collectLatest { scheduledApps ->
                _homeState.update {
                    it.copy(
                        isLoading = false,
                        scheduledApps = scheduledApps.map { it.toScheduleAppInfoUi() }
                    )
                }
            }
        }
    }
}