package com.peal.appscheduler.data.local

import androidx.room.Dao
import androidx.room.Insert
import com.peal.appscheduler.data.local.model.ScheduleEntity


/**
 * Created by Peal Mazumder on 23/2/25.
 */


@Dao
interface ScheduleDao {
    @Insert
    suspend fun insert(schedule: ScheduleEntity): Long
}