package com.peal.appscheduler.ui.screens.schedule

import java.time.LocalDate
import java.time.LocalTime


/**
 * Created by Peal Mazumder on 23/2/25.
 */

sealed class SchedulerScreenIntent {
    data object ScheduleApp : SchedulerScreenIntent()
    data class OnDateSelected(val date: LocalDate) : SchedulerScreenIntent()
    data class OnTimeSelected(val time: LocalTime) : SchedulerScreenIntent()
}