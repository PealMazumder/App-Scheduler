package com.peal.appscheduler.domain.utils

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale


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

fun String.toFormattedPattern(
    fromPattern: String = "MMMM dd, yyyy | hh:mm a",
    toPattern: String = "MMMM dd, yyyy"
): String {
    return try {
        val fromFormatter = DateTimeFormatter.ofPattern(fromPattern, Locale.getDefault())
        val toFormatter = DateTimeFormatter.ofPattern(toPattern, Locale.getDefault())
        val dateTime = LocalDateTime.parse(this, fromFormatter)
        dateTime.format(toFormatter)
    } catch (e: Exception) {
        e.printStackTrace()
        this
    }
}