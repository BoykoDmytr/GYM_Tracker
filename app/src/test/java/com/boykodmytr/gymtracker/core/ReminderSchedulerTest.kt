package com.boykodmytr.gymtracker.core

import com.boykodmytr.gymtracker.core.notifications.ReminderScheduler
import com.boykodmytr.gymtracker.domain.model.TrainingDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderSchedulerTest {
    private val zone = ZoneId.of("Europe/Kyiv")
    private val monday18 = TrainingDay(DayOfWeek.MONDAY, LocalTime.of(18, 0))

    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 9, day, hour, minute, 0, 0, zone)

    @Test
    fun `same day later today`() {
        assertEquals(at(28, 18), ReminderScheduler.nextOccurrence(monday18, at(28, 10)))
    }

    @Test
    fun `same day after the time moves to next week`() {
        assertEquals(at(28, 18).plusWeeks(1), ReminderScheduler.nextOccurrence(monday18, at(28, 18)))
        assertEquals(at(28, 18).plusWeeks(1), ReminderScheduler.nextOccurrence(monday18, at(28, 19, 30)))
    }

    @Test
    fun `other weekday`() {
        val friday = TrainingDay(DayOfWeek.FRIDAY, LocalTime.of(7, 15))
        assertEquals(ZonedDateTime.of(2026, 10, 2, 7, 15, 0, 0, zone), ReminderScheduler.nextOccurrence(friday, at(28, 10)))
    }
}
