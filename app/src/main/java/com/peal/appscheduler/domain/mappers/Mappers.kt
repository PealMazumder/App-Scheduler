package com.peal.appscheduler.domain.mappers

import android.content.Context
import com.peal.appscheduler.domain.model.AppSchedule
import com.peal.appscheduler.domain.model.DeviceAppInfo
import com.peal.appscheduler.domain.utils.formatScheduledTime
import com.peal.appscheduler.ui.model.ScheduleAppInfoUi
import com.peal.appscheduler.ui.utils.getAppIconDrawable


/**
 * Created by Peal Mazumder on 24/2/25.
 */

fun AppSchedule.toScheduleAppInfoUi(context: Context): ScheduleAppInfoUi {
    val iconDrawable = context.getAppIconDrawable(this.packageName)
    return ScheduleAppInfoUi(
        id = this.id,
        name = this.appName,
        packageName = this.packageName,
        icon = iconDrawable,
        time = this.scheduledTime.formatScheduledTime(),
        utcScheduleTime = this.scheduledTime,
        status = this.status,
    )
}

fun ScheduleAppInfoUi.toDeviceAppInfo() = DeviceAppInfo(
    id = this.id,
    name = this.name,
    packageName = this.packageName,
    icon = this.icon
)

