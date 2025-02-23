package com.peal.appscheduler.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.peal.appscheduler.data.local.model.ScheduleEntity


/**
 * Created by Peal Mazumder on 23/2/25.
 */

@Database(entities = [ScheduleEntity::class], version = 1)
abstract class SchedulerAppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
}