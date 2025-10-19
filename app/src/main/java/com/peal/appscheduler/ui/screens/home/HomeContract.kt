package com.peal.appscheduler.ui.screens.home

import androidx.compose.runtime.Immutable
import com.peal.appscheduler.ui.model.ScheduleAppInfoUi

object HomeContract {
    @Immutable
    data class State(
        val isLoading: Boolean = false,
        val scheduledApps: List<ScheduleAppInfoUi> = emptyList()
    )

    sealed class Intent {
        data object OnNavigateInstalledApps : Intent()
        data class OnNavigateScheduledApps(val appInfo: ScheduleAppInfoUi) : Intent()
    }

    sealed class Effect {
        data object NavigateToInstalledApps : Effect()
        data class NavigateToScheduledApps(val appInfo: ScheduleAppInfoUi) : Effect()
    }
}
