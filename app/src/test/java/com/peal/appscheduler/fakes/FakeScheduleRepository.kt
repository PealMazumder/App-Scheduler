package com.peal.appscheduler.fakes

import com.peal.appscheduler.core.domain.util.Result
import com.peal.appscheduler.core.domain.util.ScheduleError
import com.peal.appscheduler.domain.model.AppSchedule
import com.peal.appscheduler.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeScheduleRepository : ScheduleRepository {
    var addScheduleResult: Result<Long, ScheduleError> = Result.Success(1L)
    var updateScheduleCalls = 0
    var lastUpdatedSchedule: AppSchedule? = null
    var updateStatusCalls = 0
    var lastStatus: String? = null
    var throwOnUpdateStatus = false

    override suspend fun addSchedule(schedule: AppSchedule) = addScheduleResult

    override fun getAllScheduledApps(): Flow<List<AppSchedule>> = MutableSharedFlow()

    override suspend fun updateSchedule(newSchedule: AppSchedule) {
        updateScheduleCalls++
        lastUpdatedSchedule = newSchedule
    }

    override suspend fun updateScheduleStatus(id: Long, status: String) {
        if (throwOnUpdateStatus) throw IllegalStateException("DB write failed")
        updateStatusCalls++
        lastStatus = status
    }

    override fun getScheduledAppsToReschedule(status: String): Flow<List<AppSchedule>> = MutableSharedFlow()
}
