package com.peal.appscheduler.ui.screens.deviceApps

import androidx.compose.runtime.Immutable
import com.peal.appscheduler.domain.model.DeviceAppInfo


/**
 * Created by Peal Mazumder on 22/2/25.
 */

@Immutable
data class DeviceAppsScreenState(
    val isLoading: Boolean = false,
    val deviceApps: List<DeviceAppInfo> = emptyList(),
)
