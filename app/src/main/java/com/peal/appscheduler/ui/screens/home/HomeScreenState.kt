package com.peal.appscheduler.ui.screens.home

import androidx.compose.runtime.Immutable
import com.peal.appscheduler.ui.model.ScheduleAppInfoUi


/**
 * Created by Peal Mazumder on 23/2/25.
 */

@Immutable
data class HomeScreenState (
    val isLoading: Boolean = false,
    val scheduledApps: List<ScheduleAppInfoUi> = emptyList()
)