package com.peal.appscheduler.ui.shared.navigation

import com.peal.appscheduler.ui.model.ScheduleAppInfoUi

fun Navigator.navigateToDeviceAppsList() {
    this.navigate(DeviceAppsListScreen)
}

fun Navigator.navigateToAppScheduler(appInfoUi: ScheduleAppInfoUi) {
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
