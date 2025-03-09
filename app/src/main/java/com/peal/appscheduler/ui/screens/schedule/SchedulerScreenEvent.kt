package com.peal.appscheduler.ui.screens.schedule


/**
 * Created by Peal Mazumder on 6/3/25.
 */

sealed class SchedulerScreenEvent {
    data object AppScheduled : SchedulerScreenEvent()
    data object TimeConflict : SchedulerScreenEvent()
    data object UnknownError : SchedulerScreenEvent()
    data object MissingDateTime : SchedulerScreenEvent()
    data object PastDateTime : SchedulerScreenEvent()
    data object PreviousDateTime : SchedulerScreenEvent()
    data object ScheduleAlreadyHandled : SchedulerScreenEvent()
    data object ScheduleCancelled : SchedulerScreenEvent()
}