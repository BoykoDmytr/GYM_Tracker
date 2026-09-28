package com.boykodmytr.gymtracker.domain.logic

import com.boykodmytr.gymtracker.domain.model.TrainingDay
import com.boykodmytr.gymtracker.domain.model.WorkoutTemplate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Scheduling rules.
 *
 * Workouts rotate A → B → C → A regardless of the weekday. A fixed "Monday = A" mapping breaks as soon
 * as a day is missed (you would do A, then C, and never B), so the next workout is always the one
 * after the last completed workout of the program.
 */
object ScheduleCalculator {

    fun isTrainingDay(date: LocalDate, days: List<TrainingDay>): Boolean =
        days.any { it.dayOfWeek == date.dayOfWeek }

    /** First training day on or after [from]; null when no training days are configured. */
    fun nextTrainingDate(from: LocalDate, days: List<TrainingDay>): LocalDate? {
        if (days.isEmpty()) return null
        return (0L..6L).map { from.plusDays(it) }.first { isTrainingDay(it, days) }
    }

    fun nextTemplate(templates: List<WorkoutTemplate>, lastCompletedTemplateId: String?): WorkoutTemplate? {
        if (templates.isEmpty()) return null
        val ordered = templates.sortedBy { it.orderIndex }
        val lastIndex = ordered.indexOfFirst { it.id == lastCompletedTemplateId }
        return if (lastIndex == -1) ordered.first() else ordered[(lastIndex + 1) % ordered.size]
    }

    /**
     * Training days in [start, endExclusive) with no completed workout on that date.
     * A workout moved to another day therefore still marks the planned day as missed – simple and
     * predictable, and it matches what the calendar shows.
     */
    fun missedDates(
        start: LocalDate,
        endExclusive: LocalDate,
        days: List<TrainingDay>,
        completedDates: Set<LocalDate>,
    ): List<LocalDate> {
        if (days.isEmpty() || !start.isBefore(endExclusive)) return emptyList()
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { it.isBefore(endExclusive) }
            .filter { isTrainingDay(it, days) && it !in completedDates }
            .toList()
    }

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * Number of consecutive weeks (Mon–Sun) in which at least [targetPerWeek] workouts were completed.
     * A daily streak makes no sense for a 3×/week program, so the streak is counted in weeks.
     * The current week only extends the streak once its target is reached; an unfinished current week
     * does not break it.
     */
    fun weeklyStreak(completedDates: Collection<LocalDate>, today: LocalDate, targetPerWeek: Int): Int {
        if (targetPerWeek <= 0 || completedDates.isEmpty()) return 0
        val perWeek = completedDates.groupingBy { weekStart(it) }.eachCount()
        var week = weekStart(today)
        var streak = 0
        if ((perWeek[week] ?: 0) >= targetPerWeek) streak++
        week = week.minusWeeks(1)
        while ((perWeek[week] ?: 0) >= targetPerWeek) {
            streak++
            week = week.minusWeeks(1)
        }
        return streak
    }
}
