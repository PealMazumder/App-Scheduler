package com.peal.appscheduler.ui.screens.installedApps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peal.appscheduler.domain.usecase.GetInstalledAppsUseCase
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
class InstalledAppsViewModel @Inject constructor(
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase
) : ViewModel() {

    private val _installedAppsScreenState = MutableStateFlow(InstalledAppsScreenState())
    val installedAppsScreenState: StateFlow<InstalledAppsScreenState> = _installedAppsScreenState

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            getInstalledAppsUseCase()
                .onStart {
                    _installedAppsScreenState.update { it.copy(isLoading = true) }
                }
                .catch { e -> e.printStackTrace() }
                .collect { installedApps ->
                    _installedAppsScreenState.update {
                        it.copy(
                            isLoading = false,
                            installedApps = installedApps
                        )
                    }
                }
        }
    }
}