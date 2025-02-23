package com.peal.appscheduler.ui.screens.schedule

import androidx.compose.runtime.Immutable
import com.peal.appscheduler.domain.model.InstalledAppInfo


/**
 * Created by Peal Mazumder on 23/2/25.
 */

@Immutable
data class SchedulerScreenState(
    val isLoading: Boolean = false,
    val appInfo: InstalledAppInfo? = null,
    val selectedDate: String? = null,
    val selectedTime: String? = null,
)