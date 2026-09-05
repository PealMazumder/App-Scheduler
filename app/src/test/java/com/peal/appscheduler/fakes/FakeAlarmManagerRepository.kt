package com.peal.appscheduler.fakes

import com.peal.appscheduler.domain.repository.AlarmManagerRepository

class FakeAlarmManagerRepository : AlarmManagerRepository {
    var scheduleAppResult: Result<Unit> = Result.success(Unit)
    var cancelResult: Result<Unit> = Result.success(Unit)
    var updateScheduleResult: Result<Unit> = Result.success(Unit)
    var scheduleAppCalls = 0
    var cancelCalls = 0
    var updateScheduleCalls = 0

    override fun scheduleApp(packageName: String, scheduleTime: Long, scheduleId: Long): Result<Unit> {
        scheduleAppCalls++
        return scheduleAppResult
    }

    override fun cancelSchedule(packageName: String, scheduleId: Long): Result<Unit> {
        cancelCalls++
        return cancelResult
    }

    override fun updateSchedule(packageName: String, newScheduleTime: Long, scheduleId: Long): Result<Unit> {
        updateScheduleCalls++
        return updateScheduleResult
    }
}
