package com.peal.appscheduler.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.peal.appscheduler.data.local.model.ScheduleEntity


/**
 * Created by Peal Mazumder on 23/2/25.
 */


@Dao
interface ScheduleDao {
    @Insert
    suspend fun insert(schedule: ScheduleEntity): Long

    @Query("SELECT * FROM schedules")
    suspend fun getAllScheduledApps(): List<ScheduleEntity>

    @Update
    suspend fun update(scheduleEntity: ScheduleEntity)

    @Query("UPDATE schedules SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)
}