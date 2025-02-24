package com.peal.appscheduler.ui.model

import android.graphics.drawable.Drawable


/**
 * Created by Peal Mazumder on 23/2/25.
 */
data class ScheduleAppInfoUi(
    val id : Long,
    val name: String,
    val packageName: String,
    val icon: Drawable?,
    val time: String,
    val utcScheduleTime: Long,
)
