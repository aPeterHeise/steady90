package com.steady90.app

import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    @Test fun overlappingSleepCannotDoubleCount() {
        assertEquals(900L, Metrics.unionSeconds(listOf(0L to 600L, 300L to 900L, 300L to 900L)))
        assertEquals(1200L, Metrics.unionSeconds(listOf(0L to 600L, 900L to 1500L)))
        assertEquals(0L, Metrics.unionSeconds(listOf(10L to 0L)))
    }
    @Test fun missingHealthIsUnknownNotZero() {
        val day = DayLog("2026-11-03")
        assertNull(day.sleepMinutes); assertNull(day.steps)
        assertEquals("Not recorded", Metrics.hours(day.sleepMinutes))
        assertEquals(0, Metrics.actions(day))
        assertEquals(0, day.copy(manualSleepMinutes = 0, importedSleepMinutes = 480).sleepMinutes)
        assertEquals(480, day.copy(importedSleepMinutes = 480).sleepMinutes)
    }
    @Test fun manualOverridesImportsAndClearingRestoresImport() {
        val day = DayLog("2026-11-03", manualSteps = 400, importedSteps = 800)
        assertEquals(400L, day.steps); assertEquals(800L, day.copy(manualSteps = null).steps)
    }
    @Test fun timerSurvivesScreenOffAndCapsAtTwentyMinutes() {
        val timer = ReadingTimer(1_000, 1_000, 3, date = "2026-11-03")
        assertEquals(60, Metrics.timerSeconds(timer, 61_000, 3))
        assertEquals(1200, Metrics.timerSeconds(timer, 50_000_000, 3))
        assertEquals(0, Metrics.timerSeconds(timer, 500, 3))
    }
    @Test fun rebootCannotInventReadingTime() {
        val timer = ReadingTimer(1000, 1000, 3, pausedSeconds = 60, date = "2026-11-03")
        assertEquals(60, Metrics.timerSeconds(timer, 50_000_000, 4))
        assertEquals(60, Metrics.timerSeconds(timer.copy(running = false), 50_000_000, 3))
    }
    @Test fun alcoholUsesVolumeStrengthAndCount() {
        assertEquals(39.45, Metrics.alcoholGrams(500, 5.0, 2), 0.001)
        assertEquals(14.202, Metrics.alcoholGrams(150, 12.0, 1), 0.001)
    }
    @Test fun lapseDoesNotErasePriorDaysAndUnknownStaysUnknown() {
        val data = AppData().updateDay("2026-11-01") { it.copy(alcohol = "free", readingSeconds = 1200) }
            .updateDay("2026-11-02") { it.copy(alcohol = "drank") }
        assertEquals(1, data.days.values.count { it.alcohol == "free" })
        assertEquals(1200, data.days.values.sumOf { it.readingSeconds })
        assertEquals("", data.day("2026-11-03").alcohol)
    }
    @Test fun storageRoundTripPreservesPrivacySettingsNullsAndTimer() {
        val data = AppData(plan = Plan(onboarded = true, book = "A book — 🪴", lock = true),
            days = mapOf("2026-11-03" to DayLog("2026-11-03", importedSleepMinutes = 450, alcohol = "drank", alcoholGrams = 39.45, wins = "Kept going\nRead a page")),
            urges = listOf(Urge(1234, "Porn", "Stress", "Read", 8, 4)),
            timer = ReadingTimer(1234, 1000, 2, 20, false, "2026-11-03"))
        assertEquals(data, Codec.decode(Codec.encode(data)))
    }
    @Test fun savingSessionAndClearingTimerIsSingleDataUpdate() {
        val data = AppData(timer = ReadingTimer(1000, 1000, 3, date = "2026-11-03"))
        val updated = data.updateDay(data.timer!!.date) { it.copy(readingSeconds = it.readingSeconds + 1200) }.copy(timer = null)
        val reloaded = Codec.decode(Codec.encode(updated))
        assertNull(reloaded.timer); assertEquals(1200, reloaded.day("2026-11-03").readingSeconds)
    }
}
