package com.peal.appscheduler.data.mappers

import com.peal.appscheduler.data.local.model.ScheduleEntity
import com.peal.appscheduler.domain.model.AppSchedule


/**
 * Created by Peal Mazumder on 23/2/25.
 */


fun AppSchedule.toEntity(): ScheduleEntity = ScheduleEntity(
    id = id,
    packageName = packageName,
    scheduledTime = scheduledTime,
    appName = appName,
    status = status
)


fun ScheduleEntity.toAppSchedule(): AppSchedule = AppSchedule(
    id = id,
    packageName = packageName,
    scheduledTime = scheduledTime,
    appName = appName,
    status = status
)

