package com.peal.appscheduler.domain.utils


/**
 * Created by Peal Mazumder on 24/2/25.
 */

sealed class ScheduleResult<out T> {
    data class Success<out T>(val data: T) : ScheduleResult<T>()
    data class Error(val message: String) : ScheduleResult<Nothing>()
}