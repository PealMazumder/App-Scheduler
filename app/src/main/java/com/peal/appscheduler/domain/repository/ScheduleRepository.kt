package com.peal.appscheduler.domain.repository

import com.peal.appscheduler.domain.model.AppSchedule
import kotlinx.coroutines.flow.Flow


/**
 * Created by Peal Mazumder on 23/2/25.
 */

interface ScheduleRepository {
    suspend fun addSchedule(schedule: AppSchedule): Long

    fun getAllScheduledApps(): Flow<List<AppSchedule>>

    suspend fun updateSchedule(newSchedule: AppSchedule)

    suspend fun updateScheduleStatus(id: Long, status: String)
}