package com.peal.appscheduler.data.mappers

import com.peal.appscheduler.data.local.model.ScheduleEntity
import com.peal.appscheduler.domain.repository.AppSchedule


/**
 * Created by Peal Mazumder on 23/2/25.
 */


fun AppSchedule.toEntity(): ScheduleEntity = ScheduleEntity(
    packageName = packageName,
    scheduledTime = scheduledTime,
    appName = appName,
    status = status
)

