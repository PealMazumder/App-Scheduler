package com.peal.appscheduler.ui.screens.deviceApps

import androidx.compose.runtime.Immutable
import com.peal.appscheduler.domain.model.DeviceAppInfo

object DeviceAppsContract {

    @Immutable
    data class State(
        val isLoading: Boolean = false,
        val deviceApps: List<DeviceAppInfo> = emptyList(),
        val errorMessage: String? = null,
    )

    sealed class Intent {
        data class OnNavigateScheduler(val data: DeviceAppInfo) : Intent()
        data object Retry : Intent()
    }

    sealed class Effect {
        data class NavigateToScheduler(val data: DeviceAppInfo) : Effect()
    }
}
