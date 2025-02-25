package com.peal.appscheduler.domain.mappers

import com.peal.appscheduler.domain.model.AppSchedule
import com.peal.appscheduler.domain.model.DeviceAppInfo
import com.peal.appscheduler.domain.utils.formatScheduledTime
import com.peal.appscheduler.ui.model.ScheduleAppInfoUi


/**
 * Created by Peal Mazumder on 24/2/25.
 */

fun AppSchedule.toScheduleAppInfoUi() = ScheduleAppInfoUi(
    id = this.id,
    name = this.appName,
    packageName = this.packageName,
    time = this.scheduledTime.formatScheduledTime(),
    utcScheduleTime = this.scheduledTime,
    icon = null,
    status = this.status,
)

fun ScheduleAppInfoUi.toDeviceAppInfo() = DeviceAppInfo(
    id = this.id,
    name = this.name,
    packageName = this.packageName,
    icon = null
)

