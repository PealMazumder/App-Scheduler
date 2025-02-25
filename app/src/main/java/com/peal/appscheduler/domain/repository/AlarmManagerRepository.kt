package com.peal.appscheduler.domain.repository


/**
 * Created by Peal Mazumder on 25/2/25.
 */
interface AlarmManagerRepository {
    fun scheduleApp(packageName: String, scheduleTime: Long, scheduleId: Long): Result<Unit>
    fun cancelSchedule(packageName: String, scheduleId: Long): Result<Unit>
    fun updateSchedule(packageName: String, newScheduleTime: Long, scheduleId: Long): Result<Unit>
}