package com.peal.appscheduler.ui.screens.deviceApps

import androidx.lifecycle.ViewModel
import com.peal.appscheduler.ui.model.ScheduleAppInfoUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 23/2/25.
 */

class SharedDeviceAppViewModel @Inject constructor() : ViewModel() {

    private val _appScheduleInfo = MutableStateFlow<ScheduleAppInfoUi?>(null)
    val appScheduleInfo: StateFlow<ScheduleAppInfoUi?> = _appScheduleInfo

    fun setSelectedAppInfo(appScheduleInfo: ScheduleAppInfoUi) {
        _appScheduleInfo.value = appScheduleInfo
    }
}
