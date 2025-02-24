package com.peal.appscheduler.ui.screens.deviceApps

import androidx.lifecycle.ViewModel
import com.peal.appscheduler.domain.model.DeviceAppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 23/2/25.
 */

@HiltViewModel
class SharedDeviceAppViewModel @Inject constructor() : ViewModel() {
    var appInfo: DeviceAppInfo? = null

    var scheduleTime: String? = null


    fun setSelectedAppInfo(appInfo: DeviceAppInfo) {
        this.appInfo = appInfo
    }

    fun setScheduledDateAndTime(time: String) {
        scheduleTime = time
    }

    fun clearScheduleTimeData() {
        scheduleTime = null
    }
}
