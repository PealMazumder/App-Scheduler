package com.peal.appscheduler.ui.screens.schedule

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 23/2/25.
 */

@HiltViewModel
class SchedulerViewModel @Inject constructor() : ViewModel() {
    private val _schedulerScreenState = MutableStateFlow(SchedulerScreenState())
    val schedulerScreenState: StateFlow<SchedulerScreenState> = _schedulerScreenState
}