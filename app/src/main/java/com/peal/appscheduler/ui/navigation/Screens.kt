package com.peal.appscheduler.ui.navigation

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
    data object AppSchedulerScreen : Screens()
}

