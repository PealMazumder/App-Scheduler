package com.peal.appscheduler.ui.screens.schedule

import androidx.compose.runtime.Immutable
import com.peal.appscheduler.ui.model.ScheduleAppInfoUi
import java.time.LocalDate
import java.time.LocalTime

object ScheduleContract {

    @Immutable
    data class State(
        val isLoading: Boolean = false,
        val scheduledAppInfo: ScheduleAppInfoUi? = null,
        val selectedDate: String? = null,
        val selectedTime: String? = null,
        val message: String? = null,
        val isEditable: Boolean = false
    )

    sealed class Intent {
        data object ScheduleApp : Intent()
        data class OnDateSelected(val date: LocalDate) : Intent()
        data class OnTimeSelected(val time: LocalTime) : Intent()
        data object CancelSchedule : Intent()
    }

    sealed class Effect {
        data object AppScheduled : Effect()
        data object TimeConflict : Effect()
        data object UnknownError : Effect()
        data object MissingDateTime : Effect()
        data object PastDateTime : Effect()
        data object PreviousDateTime : Effect()
        data object ScheduleAlreadyHandled : Effect()
        data object ScheduleCancelled : Effect()
    }
}