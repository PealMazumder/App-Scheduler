package com.peal.appscheduler.data.repositoryImpl

import com.peal.appscheduler.data.local.ScheduleDao
import com.peal.appscheduler.data.mappers.toAppSchedule
import com.peal.appscheduler.data.mappers.toEntity
import com.peal.appscheduler.domain.model.AppSchedule
import com.peal.appscheduler.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 23/2/25.
 */

class ScheduleRepositoryImpl @Inject constructor(
    private val scheduleDao: ScheduleDao
) : ScheduleRepository {
    override suspend fun addSchedule(schedule: AppSchedule): Long =
        scheduleDao.insert(schedule.toEntity())

    override fun getAllScheduledApps(): Flow<List<AppSchedule>> {
        return scheduleDao.getAllScheduledApps().map { list ->
            list.map { it.toAppSchedule() }
        }
    }

    override suspend fun updateSchedule(newSchedule: AppSchedule) {
        scheduleDao.update(newSchedule.toEntity())
    }

    override suspend fun updateScheduleStatus(id: Long, status: String) {
        scheduleDao.updateStatus(id, status)
    }
}