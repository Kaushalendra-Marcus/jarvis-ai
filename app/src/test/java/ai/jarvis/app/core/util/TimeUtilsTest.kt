package ai.jarvis.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilsTest {

    @Test
    fun parseHourAndMinute_standard12HourAM() {
        val (hour, minute) = TimeUtils.parseHourAndMinute("9:30 AM")
        assertEquals(9, hour)
        assertEquals(30, minute)
    }

    @Test
    fun parseHourAndMinute_standard12HourPM() {
        val (hour, minute) = TimeUtils.parseHourAndMinute("2:15 PM")
        assertEquals(14, hour)
        assertEquals(15, minute)
    }

    @Test
    fun parseHourAndMinute_naturalLanguageMorning() {
        val (hour, minute) = TimeUtils.parseHourAndMinute("every morning at 9 am")
        assertEquals(9, hour)
        assertEquals(0, minute)
    }

    @Test
    fun parseHourAndMinute_24HourTime() {
        val (hour, minute) = TimeUtils.parseHourAndMinute("18:45")
        assertEquals(18, hour)
        assertEquals(45, minute)
    }

    @Test
    fun parseHourAndMinute_fallbackDefaultsTo9AM() {
        val (hour, minute) = TimeUtils.parseHourAndMinute("some unparseable random string")
        assertEquals(9, hour)
        assertEquals(0, minute)
    }

    @Test
    fun tryParseHourAndMinute_rejectsInvalidOrMissingTime() {
        assertEquals(null, TimeUtils.tryParseHourAndMinute("schedule this later"))
        assertEquals(null, TimeUtils.tryParseHourAndMinute("25:90"))
        assertEquals(9 to 30, TimeUtils.tryParseHourAndMinute("9:30 AM"))
    }

    @Test
    fun formatHourMinute_uses12HourClock() {
        assertEquals("12:00 AM", TimeUtils.formatHourMinute(0, 0))
        assertEquals("9:05 AM", TimeUtils.formatHourMinute(9, 5))
        assertEquals("12:30 PM", TimeUtils.formatHourMinute(12, 30))
        assertEquals("6:45 PM", TimeUtils.formatHourMinute(18, 45))
        assertEquals("11:59 PM", TimeUtils.formatHourMinute(23, 59))
    }

    @Test
    fun formatTime_uses12HourClock() {
        val defaultZone = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        try {
            // 2026-11-24 14:05 UTC and 2026-11-24 00:05 UTC.
            assertEquals("2:05 PM", TimeUtils.formatTime(1795529100000L))
            assertEquals("12:05 AM", TimeUtils.formatTime(1795478700000L))
        } finally {
            java.util.TimeZone.setDefault(defaultZone)
        }
    }
}
