package com.peal.appscheduler.ui.screens.installedApps

import androidx.compose.runtime.Immutable
import com.peal.appscheduler.domain.model.InstalledAppInfo


/**
 * Created by Peal Mazumder on 22/2/25.
 */

@Immutable
data class InstalledAppsScreenState(
    val isLoading: Boolean = false,
    val installedApps: List<InstalledAppInfo> = emptyList(),
)
