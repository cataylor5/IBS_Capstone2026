package io.github.cataylor5.ibs

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class BusinessHoursTest {
    private fun at(local: String) = LocalDateTime.parse(local).atZone(ZoneId.of("America/New_York")).toInstant()
    private fun hours(day: Int, open: String? = "09:00:00", close: String? = "17:00:00") = BusinessHours(day, open, close)

    @Test fun showsClosingAndOpeningCountdownInNewYork() {
        val schedule = listOf(hours(1))
        assertEquals("Closes in 25 min", businessHoursCountdown(schedule, Instant.parse("2026-11-09T21:35:00Z")))
        assertEquals("Opens in 1 hr 10 min", businessHoursCountdown(schedule, at("2026-11-09T07:50:00")))
    }
    @Test fun checksPreviousDayForOvernightWindow() {
        val schedule = listOf(hours(7, "22:00", "02:00"))
        assertEquals("Closes in 1 hr", businessHoursCountdown(schedule, at("2026-11-09T01:00:00")))
        assertTrue(businessHoursCountdown(schedule, at("2026-11-09T02:00:00")).startsWith("Opens in"))
    }
    @Test fun looksAcrossWeekAndUsesIsoSundaySeven() {
        assertEquals("Opens in 2 days 13 hr", businessHoursCountdown(listOf(hours(1)), at("2026-11-13T20:00:00")))
        assertEquals("Closes in 7 hr", businessHoursCountdown(listOf(hours(7)), at("2026-11-08T10:00:00")))
    }
    @Test fun splitShiftsHaveRealGapAndAdjacentShiftsDoNotFalselyClose() {
        assertEquals("Opens in 30 min", businessHoursCountdown(
            listOf(hours(1, "09:00", "12:00"), hours(1, "13:00", "17:00")), at("2026-11-09T12:30:00")))
        assertEquals("Closes in 5 hr 1 min", businessHoursCountdown(
            listOf(hours(1, "09:00", "12:00"), hours(1, "12:00", "17:00")), at("2026-11-09T11:59:00")))
    }
    @Test fun unavailableOrAmbiguousSchedulesAreNotGuessed() {
        for (schedule in listOf(emptyList(), listOf(hours(0)), listOf(hours(8)),
            listOf(hours(1, null, "17:00")), listOf(hours(1, "oops", "17:00")),
            listOf(hours(1, "00:00", "00:00")), listOf(hours(1, null, null)))) {
            assertEquals("Hours unavailable", businessHoursCountdown(schedule, at("2026-11-09T10:00:00")))
        }
    }
    @Test fun dstSpringAndFallCountActualElapsedTime() {
        assertEquals("Closes in 1 hr 30 min", businessHoursCountdown(
            listOf(hours(6, "22:00", "04:00")), at("2026-03-08T01:30:00")))
        assertEquals("Closes in 2 hr 30 min", businessHoursCountdown(
            listOf(hours(6, "22:00", "02:00")), at("2026-11-01T00:30:00")))
    }
    @Test fun countdownRefreshesFromClockAndRoundsRemainingPartialMinuteUp() {
        val schedule = listOf(hours(1))
        assertEquals("Closes in 2 min", businessHoursCountdown(schedule, at("2026-11-09T16:58:01")))
        assertEquals("Closes in 1 min", businessHoursCountdown(schedule, at("2026-11-09T16:59:59")))
        assertEquals("Opens in 6 days 16 hr", businessHoursCountdown(schedule, at("2026-11-09T17:00:00")))
    }
    @Test fun postgresEndOfDayTwentyFourIsSupported() {
        assertEquals("Closes in 30 min", businessHoursCountdown(
            listOf(hours(1, "09:00:00", "24:00:00")), at("2026-11-09T23:30:00")))
    }
}
