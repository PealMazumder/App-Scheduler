package com.peal.appscheduler.domain.model

import android.graphics.drawable.Drawable


/**
 * Created by Peal Mazumder on 22/2/25.
 */

data class DeviceAppInfo(
    val id: Long = 0,
    val name: String,
    val packageName: String,
    val icon: Drawable?,
)