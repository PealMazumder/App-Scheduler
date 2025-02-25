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
        .withZone(ZoneId.of("UTC"))
    val instant = Instant.ofEpochMilli(this)
    return formatter.format(instant)
}

fun Long.toFormattedPattern(
    toPattern: String = "MMMM dd, yyyy"
): String {
    return try {
        val zonedDateTime = Instant.ofEpochMilli(this)
            .atZone(ZoneId.of("UTC"))

        val formatter = DateTimeFormatter.ofPattern(toPattern, Locale.getDefault())
        zonedDateTime.format(formatter)
    } catch (e: Exception) {
        e.printStackTrace()
        this.toString()
    }
}

fun String.toLocalTime(
    pattern: String = "hh:mm a",
): LocalTime {
    val formatter = DateTimeFormatter.ofPattern(pattern)
    val dateTime = LocalDateTime.parse(this, formatter)
    return dateTime.toLocalTime()
}

fun String.toLocalDate(
    pattern: String = "MMMM dd, yyyy",
): LocalDate {
    val formatter = DateTimeFormatter.ofPattern(pattern)
    return LocalDate.parse(this, formatter)
}

fun Long.toUtcEpochMillis(): Long {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.of("UTC"))
        .toInstant()
        .toEpochMilli()
}
