package com.peal.appscheduler.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey


/**
 * Created by Peal Mazumder on 23/2/25.
 */

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val scheduledTime: Long,
    val status: String
)