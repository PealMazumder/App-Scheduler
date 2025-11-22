package com.peal.appscheduler.ui.shared.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable


/**
 * Created by Peal Mazumder on 22/2/25.
 */

@Serializable
data object HomeScreen : NavKey

@Serializable
data object DeviceAppsListScreen : NavKey

@Serializable
data class AppSchedulerScreen(
    val id: Long,
    val name: String,
    val packageName: String,
    val time: String = "",
    val utcScheduleTime: Long? = null,
    val status: String? = null
) : NavKey
