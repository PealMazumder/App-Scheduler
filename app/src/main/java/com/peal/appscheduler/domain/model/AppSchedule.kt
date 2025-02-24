package com.peal.appscheduler.domain.model


/**
 * Created by Peal Mazumder on 24/2/25.
 */

data class AppSchedule(
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val scheduledTime: Long,
    val status: String,
)