package com.peal.appscheduler.domain.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class DateTimeExtensionsTest {

    @Test
    fun `toFormattedDate formats using the default pattern`() {
        val date = LocalDate.of(2026, 9, 5)
        assertEquals("September 05, 2026", date.toFormattedDate())
    }

    @Test
    fun `toFormattedTime formats using the default 12-hour pattern`() {
        assertEquals("07:30 AM", LocalTime.of(7, 30).toFormattedTime())
        assertEquals("07:30 PM", LocalTime.of(19, 30).toFormattedTime())
    }

    @Test
    fun `toLocalDate parses a string formatted with the same default pattern`() {
        val date = LocalDate.of(2026, 9, 5)
        val roundTripped = date.toFormattedDate().toLocalDate()
        assertEquals(date, roundTripped)
    }

    @Test
    fun `toLocalDate returns null instead of throwing on unparseable input`() {
        assertNull("not a date".toLocalDate())
    }

    @Test
    fun `toLocalTime parses a string formatted with the same default pattern`() {
        val time = LocalTime.of(13, 45)
        val roundTripped = time.toFormattedTime().toLocalTime()
        assertEquals(time, roundTripped)
    }

    @Test
    fun `toLocalTime returns null instead of throwing on unparseable input`() {
        assertNull("not a time".toLocalTime())
    }

    @Test
    fun `formatScheduledTime renders an epoch millis value in the given zone`() {
        val utcMidnight = LocalDate.of(2026, 9, 5).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val formatted = utcMidnight.formatScheduledTime(zoneId = ZoneOffset.UTC)
        assertEquals("September 05, 2026 | 12:00 AM", formatted)
    }

    @Test
    fun `toFormattedPattern renders an epoch millis value as a date string`() {
        val utcMidnight = LocalDate.of(2026, 9, 5).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals("September 05, 2026", utcMidnight.toFormattedPattern(zoneId = ZoneOffset.UTC))
    }

    @Test
    fun `toUtcEpochMillis is a no-op - epoch millis are already timezone-independent`() {
        // Documents a real, surprising defect: converting to a UTC ZonedDateTime and back
        // to epoch millis returns the exact same value, regardless of the system default
        // zone. The function's name implies a normalization that never happens.
        val original = System.currentTimeMillis()
        assertEquals(original, original.toUtcEpochMillis())

        val arbitraryZone = ZoneId.of("America/New_York")
        val zonedMillis = LocalDate.of(2026, 1, 15)
            .atTime(3, 0)
            .atZone(arbitraryZone)
            .toInstant()
            .toEpochMilli()
        assertEquals(zonedMillis, zonedMillis.toUtcEpochMillis())
    }
}
