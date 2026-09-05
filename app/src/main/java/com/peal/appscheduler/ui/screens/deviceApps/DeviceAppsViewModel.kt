package com.peal.appscheduler.ui.screens.deviceApps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peal.appscheduler.domain.usecase.GetDeviceAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
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

    private val _deviceAppsScreenState = MutableStateFlow(DeviceAppsContract.State())
    val deviceAppsScreenState: StateFlow<DeviceAppsContract.State> = _deviceAppsScreenState

    private val _effect = Channel<DeviceAppsContract.Effect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            getInstalledAppsUseCase()
                .onStart {
                    _deviceAppsScreenState.update { it.copy(isLoading = true) }
                }
                .catch { e ->
                    _deviceAppsScreenState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to load apps"
                        )
                    }
                }
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

    fun onIntent(intent: DeviceAppsContract.Intent) {
        when (intent) {
            is DeviceAppsContract.Intent.OnNavigateScheduler -> {
                viewModelScope.launch {
                    _effect.send(
                        DeviceAppsContract.Effect.NavigateToScheduler(intent.data)
                    )
                }
            }
        }
    }
}