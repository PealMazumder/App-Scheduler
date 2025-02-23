package com.peal.appscheduler.data.repositoryImpl

import com.peal.appscheduler.data.local.ScheduleDao
import com.peal.appscheduler.data.mappers.toEntity
import com.peal.appscheduler.domain.repository.AppSchedule
import com.peal.appscheduler.domain.repository.ScheduleRepository
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 23/2/25.
 */

class ScheduleRepositoryImpl @Inject constructor(
    private val scheduleDao: ScheduleDao
) : ScheduleRepository {
    override suspend fun addSchedule(schedule: AppSchedule): Long =
        scheduleDao.insert(schedule.toEntity())
}