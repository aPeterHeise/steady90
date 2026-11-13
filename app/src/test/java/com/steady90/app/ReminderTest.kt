package com.steady90.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class ReminderTest {
    @Test fun existingPreviewDataMigratesWithoutLosingHistory() {
        val old = JSONObject(Codec.encode(AppData(days = mapOf("2026-11-03" to DayLog("2026-11-03", readingSeconds = 1200, porn = "free")))))
        val plan = old.getJSONObject("plan")
        listOf("meditationGoal", "reminderEnabled", "reminderHour", "reminderMinute", "copingPlan").forEach { plan.remove(it) }
        old.getJSONArray("days").getJSONObject(0).remove("meditationMinutes")
        val result = Codec.decode(old.toString())
        assertFalse(result.plan.reminderEnabled)
        assertEquals(5, result.plan.meditationGoal)
        assertEquals(1200, result.day("2026-11-03").readingSeconds)
        assertEquals("free", result.day("2026-11-03").porn)
        assertEquals(0, result.day("2026-11-03").meditationMinutes)
    }
    @Test fun meditationAndReminderSettingsRoundTrip() {
        val data = AppData(plan = Plan(meditationGoal = 10, reminderEnabled = true, reminderHour = 22, reminderMinute = 15, copingPlan = "If bored, read."),
            days = mapOf("2026-11-03" to DayLog("2026-11-03", meditationMinutes = 4)))
        assertEquals(data, Codec.decode(Codec.encode(data)))
        assertEquals(1, Metrics.actions(data.day("2026-11-03")))
    }
    @Test fun beforeTimeSchedulesTodayAndAfterSchedulesTomorrow() {
        val now = ZonedDateTime.parse("2026-11-03T20:00:00+01:00[Europe/Berlin]")
        assertEquals("2026-11-03T21:00+01:00[Europe/Berlin]", ReminderTime.next(now, 21, 0).toString())
        assertEquals("2026-11-04T21:00+01:00[Europe/Berlin]", ReminderTime.next(now.plusHours(2), 21, 0).toString())
        assertEquals(now.plusDays(1), ReminderTime.next(now, 20, 0))
    }
    @Test fun springClockChangeUsesNextValidLocalTime() {
        val now = ZonedDateTime.parse("2026-03-29T01:00:00+01:00[Europe/Berlin]")
        assertEquals("2026-03-29T03:30+02:00[Europe/Berlin]", ReminderTime.next(now, 2, 30).toString())
    }
    @Test fun autumnClockChangeDoesNotRepeatAnAlreadyPassedTime() {
        val now = ZonedDateTime.parse("2026-10-25T02:45:00+02:00[Europe/Berlin]")
        assertEquals("2026-10-26T02:30+01:00[Europe/Berlin]", ReminderTime.next(now, 2, 30).toString())
    }
}
