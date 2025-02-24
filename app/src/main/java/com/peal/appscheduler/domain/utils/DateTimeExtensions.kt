package com.peal.appscheduler.domain.utils

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


/**
 * Created by Peal Mazumder on 24/2/25.
 */

fun LocalDate.toFormattedDate(): String {
    val formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy")
    return this.format(formatter)
}

fun LocalTime.toFormattedTime(): String {
    val formatter = DateTimeFormatter.ofPattern("hh:mm a")
    return this.format(formatter)
}

fun Long.formatScheduledTime(): String {
    val formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy | hh:mm a")
        .withZone(ZoneId.systemDefault())

    return formatter.format(Instant.ofEpochMilli(this))
}