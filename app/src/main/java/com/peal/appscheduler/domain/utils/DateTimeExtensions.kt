package com.peal.appscheduler.domain.utils

import android.util.Log
import com.peal.appscheduler.utils.DateTimePatterns.MMMM_DD_YYYY
import com.peal.appscheduler.utils.DateTimePatterns.MMMM_DD_YYYY_TIME_12H
import com.peal.appscheduler.utils.DateTimePatterns.TIME_12H
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale


/**
 * Created by Peal Mazumder on 24/2/25.
 */

fun LocalDate.toFormattedDate(pattern: String = MMMM_DD_YYYY): String {
    val formatter = DateTimeFormatter.ofPattern(pattern)
    return this.format(formatter)
}

fun LocalTime.toFormattedTime(pattern: String = TIME_12H): String {
    val formatter = DateTimeFormatter.ofPattern(pattern)
    return this.format(formatter)
}

fun Long.formatScheduledTime(
    pattern: String = MMMM_DD_YYYY_TIME_12H
): String {
    val formatter = DateTimeFormatter.ofPattern(pattern)
        .withZone(ZoneId.systemDefault())
    val instant = Instant.ofEpochMilli(this)
    return formatter.format(instant)
}

fun Long.toFormattedPattern(
    toPattern: String = MMMM_DD_YYYY,
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

fun String.toLocalTime(pattern: String = TIME_12H): LocalTime? {
    return try {
        val formatter = DateTimeFormatter.ofPattern(pattern)
        LocalTime.parse(this, formatter)
    } catch (e: Exception) {
        Log.e("DateTimeUtils", "Failed to parse time: ${e.message}")
        null
    }
}

fun String.toLocalDate(pattern: String = MMMM_DD_YYYY): LocalDate? {
    return try {
        val formatter = DateTimeFormatter.ofPattern(pattern)
        LocalDate.parse(this, formatter)
    } catch (e: Exception) {
        Log.e("DateTimeUtils", "Failed to parse date: ${e.message}")
        null
    }
}

fun Long.toUtcEpochMillis(): Long {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.of("UTC"))
        .toInstant()
        .toEpochMilli()
}
