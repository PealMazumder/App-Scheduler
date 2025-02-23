package com.peal.appscheduler.domain.repository



/**
 * Created by Peal Mazumder on 23/2/25.
 */

interface ScheduleRepository {
    suspend fun addSchedule(schedule: AppSchedule): Long
}

data class AppSchedule(
    val packageName: String,
    val appName: String,
    val scheduledTime: Long,
    val status: String,
)

enum class ScheduleStatus {
    SCHEDULED,
    EXECUTED,
    CANCELLED,
    FAILED
}