package com.peal.appscheduler.ui.shared.navigation

import kotlinx.serialization.Serializable


/**
 * Created by Peal Mazumder on 22/2/25.
 */

@Serializable
sealed class Screens {
    @Serializable
    data object HomeScreen : Screens()

    @Serializable
    data object DeviceAppsListScreen : Screens()

    @Serializable
    data class AppSchedulerScreen(
        val id: Long,
        val name: String,
        val packageName: String,
        val time: String = "",
        val utcScheduleTime: Long? = null,
        val status: String? = null
    ) : Screens()
}

