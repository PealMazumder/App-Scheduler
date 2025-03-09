package com.peal.appscheduler.core.domain.util

/**
 * Created by Peal Mazumder on 8/3/25.
 */

enum class ScheduleError: Error {
    TIME_CONFLICT,
    UNKNOWN_ERROR,
    ALREADY_HANDLED,
    DATABASE_ERROR,
}
