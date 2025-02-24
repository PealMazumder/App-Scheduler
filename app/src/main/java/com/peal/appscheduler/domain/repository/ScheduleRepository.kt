package com.peal.appscheduler.domain.repository

import com.peal.appscheduler.domain.model.AppSchedule


/**
 * Created by Peal Mazumder on 23/2/25.
 */

interface ScheduleRepository {
    suspend fun addSchedule(schedule: AppSchedule): Long

    suspend fun getAllScheduledApps(): List<AppSchedule>

    suspend fun updateSchedule(newSchedule: AppSchedule)
}