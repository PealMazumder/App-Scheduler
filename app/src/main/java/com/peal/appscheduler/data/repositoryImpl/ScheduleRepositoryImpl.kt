package com.peal.appscheduler.data.repositoryImpl

import com.peal.appscheduler.core.domain.util.ScheduleError
import com.peal.appscheduler.data.local.ScheduleDao
import com.peal.appscheduler.data.mappers.toAppSchedule
import com.peal.appscheduler.data.mappers.toEntity
import com.peal.appscheduler.domain.model.AppSchedule
import com.peal.appscheduler.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import com.peal.appscheduler.core.domain.util.Result
import com.peal.appscheduler.domain.enums.ScheduleStatus

/**
 * Created by Peal Mazumder on 23/2/25.
 */

class ScheduleRepositoryImpl @Inject constructor(
    private val scheduleDao: ScheduleDao
) : ScheduleRepository {
    override suspend fun addSchedule(schedule: AppSchedule): Result<Long, ScheduleError> {
        val conflictCount = scheduleDao.isScheduleConflicting(schedule.scheduledTime, ScheduleStatus.SCHEDULED.name)

        return if (conflictCount > 0) {
            Result.Failure(ScheduleError.TIME_CONFLICT)
        } else {
            val scheduleId = scheduleDao.insert(schedule.toEntity())
            Result.Success(scheduleId)
        }
    }


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

    override fun getScheduledAppsToReschedule(
        status: String,
    ): Flow<List<AppSchedule>> {
        return scheduleDao.getScheduledAppsToReschedule(status).map { list ->
            list.map { it.toAppSchedule() }
        }
    }
}