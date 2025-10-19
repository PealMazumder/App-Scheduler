package com.peal.appscheduler.ui.shared.navigation

import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.peal.appscheduler.ui.model.ScheduleAppInfoUi
import com.peal.appscheduler.ui.shared.navigation.Screens.AppSchedulerScreen

fun NavHostController.navigateToDeviceAppsList() {
    this.navigate(Screens.DeviceAppsListScreen)
}

fun NavController.navigateToAppScheduler(appInfoUi: ScheduleAppInfoUi) {
    this.navigate(
        AppSchedulerScreen(
            id = appInfoUi.id,
            name = appInfoUi.name,
            packageName = appInfoUi.packageName,
            time = appInfoUi.time,
            utcScheduleTime = appInfoUi.utcScheduleTime,
            status = appInfoUi.status
        )
    )
}
