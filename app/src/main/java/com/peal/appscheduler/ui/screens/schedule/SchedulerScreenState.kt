package com.peal.appscheduler.ui.screens.schedule

import androidx.compose.runtime.Immutable
import com.peal.appscheduler.ui.model.ScheduleAppInfoUi


/**
 * Created by Peal Mazumder on 23/2/25.
 */

@Immutable
data class SchedulerScreenState(
    val isLoading: Boolean = false,
    val scheduledAppInfo: ScheduleAppInfoUi? = null,
    val selectedDate: String? = null,
    val selectedTime: String? = null,
    val message: String? = null,
    val isEditable: Boolean = false
)