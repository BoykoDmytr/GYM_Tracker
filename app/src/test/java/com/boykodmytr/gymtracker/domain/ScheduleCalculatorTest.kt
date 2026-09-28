package com.boykodmytr.gymtracker.domain

import com.boykodmytr.gymtracker.domain.logic.ScheduleCalculator
import com.boykodmytr.gymtracker.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ScheduleCalculatorTest {
    private val monWedFri = AppSettings.DEFAULT_TRAINING_DAYS
    private val monday = LocalDate.of(2026, 9, 28)

    @Test
    fun `rotation starts with the first workout`() {
        val templates = listOf(template("B", 1), template("A", 0), template("C", 2))
        assertEquals("A", ScheduleCalculator.nextTemplate(templates, null)?.id)
    }

    @Test
    fun `rotation continues after the last completed workout and wraps around`() {
        val templates = listOf(template("A", 0), template("B", 1), template("C", 2))
        assertEquals("B", ScheduleCalculator.nextTemplate(templates, "A")?.id)
        assertEquals("C", ScheduleCalculator.nextTemplate(templates, "B")?.id)
        assertEquals("A", ScheduleCalculator.nextTemplate(templates, "C")?.id)
    }

    @Test
    fun `unknown last template restarts rotation`() {
        val templates = listOf(template("A", 0), template("B", 1))
        assertEquals("A", ScheduleCalculator.nextTemplate(templates, "deleted")?.id)
        assertNull(ScheduleCalculator.nextTemplate(emptyList(), "A"))
    }

    @Test
    fun `next training date skips rest days`() {
        assertEquals(monday, ScheduleCalculator.nextTrainingDate(monday, monWedFri))
        assertEquals(monday.plusDays(2), ScheduleCalculator.nextTrainingDate(monday.plusDays(1), monWedFri))
        // Saturday -> next Monday
        assertEquals(monday.plusDays(7), ScheduleCalculator.nextTrainingDate(monday.plusDays(5), monWedFri))
        assertNull(ScheduleCalculator.nextTrainingDate(monday, emptyList()))
    }

    @Test
    fun `missed dates are scheduled days without a workout, end exclusive`() {
        val completed = setOf(monday, monday.plusDays(4)) // Mon and Fri done, Wed missed
        val missed = ScheduleCalculator.missedDates(monday, monday.plusDays(7), monWedFri, completed)
        assertEquals(listOf(monday.plusDays(2)), missed)
        // today (end) is never counted as missed
        assertEquals(emptyList<LocalDate>(), ScheduleCalculator.missedDates(monday, monday, monWedFri, emptySet()))
    }

    @Test
    fun `weekly streak counts full weeks and tolerates an unfinished current week`() {
        val prevWeek = monday.minusWeeks(1)
        val twoWeeksAgo = monday.minusWeeks(2)
        val dates = listOf(
            twoWeeksAgo, twoWeeksAgo.plusDays(2), twoWeeksAgo.plusDays(4),
            prevWeek, prevWeek.plusDays(2), prevWeek.plusDays(4),
            monday, // current week: 1 of 3 so far
        )
        assertEquals(2, ScheduleCalculator.weeklyStreak(dates, monday.plusDays(1), 3))
        val finishedCurrent = dates + listOf(monday.plusDays(2), monday.plusDays(4))
        assertEquals(3, ScheduleCalculator.weeklyStreak(finishedCurrent, monday.plusDays(5), 3))
    }

    @Test
    fun `weekly streak breaks on an incomplete past week`() {
        val prevWeek = monday.minusWeeks(1)
        val dates = listOf(prevWeek, prevWeek.plusDays(2)) // only 2 of 3
        assertEquals(0, ScheduleCalculator.weeklyStreak(dates, monday, 3))
    }
}
