package com.peal.appscheduler.domain.model

import android.graphics.drawable.Drawable
import com.peal.appscheduler.ui.model.ScheduleAppInfoUi


/**
 * Created by Peal Mazumder on 22/2/25.
 */

data class DeviceAppInfo(
    val id: Long = 0,
    val name: String,
    val packageName: String,
    val icon: Drawable?,
) {
    fun toScheduleAppInfoUI(): ScheduleAppInfoUi {
        return ScheduleAppInfoUi(
            id = this.id,
            name = this.name,
            packageName = this.packageName,
            icon = this.icon,
        )
    }
}