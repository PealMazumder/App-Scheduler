package com.peal.appscheduler.ui.screens.deviceApps

import androidx.lifecycle.ViewModel
import com.peal.appscheduler.domain.model.DeviceAppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 23/2/25.
 */

@HiltViewModel
class SharedDeviceAppViewModel @Inject constructor() : ViewModel() {
    private val _selectedAppInfo = MutableStateFlow<DeviceAppInfo?>(null)
    val selectedAppInfo: StateFlow<DeviceAppInfo?> = _selectedAppInfo

    fun setSelectedAppInfo(appInfo: DeviceAppInfo) {
        _selectedAppInfo.value = appInfo
    }
}
