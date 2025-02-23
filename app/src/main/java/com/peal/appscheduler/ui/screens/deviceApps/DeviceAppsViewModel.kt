package com.peal.appscheduler.ui.screens.deviceApps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peal.appscheduler.domain.usecase.GetDeviceAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 22/2/25.
 */

@HiltViewModel
class DeviceAppsViewModel @Inject constructor(
    private val getInstalledAppsUseCase: GetDeviceAppsUseCase
) : ViewModel() {

    private val _deviceAppsScreenState = MutableStateFlow(DeviceAppsScreenState())
    val deviceAppsScreenState: StateFlow<DeviceAppsScreenState> = _deviceAppsScreenState

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            getInstalledAppsUseCase()
                .onStart {
                    _deviceAppsScreenState.update { it.copy(isLoading = true) }
                }
                .catch { e -> e.printStackTrace() }
                .collect { deviceApps ->
                    _deviceAppsScreenState.update {
                        it.copy(
                            isLoading = false,
                            deviceApps = deviceApps
                        )
                    }
                }
        }
    }
}