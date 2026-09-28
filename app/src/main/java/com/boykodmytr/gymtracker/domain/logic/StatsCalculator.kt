package com.boykodmytr.gymtracker.domain.logic

import com.boykodmytr.gymtracker.domain.model.SessionSummary
import com.boykodmytr.gymtracker.domain.model.TrainingDay
import java.time.Duration
import java.time.LocalDate

data class WeekCount(val weekStart: LocalDate, val count: Int)

data class TrainingStats(
    val thisWeek: Int,
    val thisMonth: Int,
    val total: Int,
    val averageDuration: Duration?,
    val totalVolumeKg: Double,
    val monthVolumeKg: Double,
    val totalDuration: Duration,
    val missedLast30Days: Int,
    val missedTotal: Int,
    val weeklyStreak: Int,
    val weeklyCounts: List<WeekCount>,
)

object StatsCalculator {

    fun compute(
        completed: List<SessionSummary>,
        today: LocalDate,
        trainingDays: List<TrainingDay>,
        trackingStart: LocalDate?,
        weeksInChart: Int = 12,
    ): TrainingStats {
        val weekStart = ScheduleCalculator.weekStart(today)
        val monthStart = today.withDayOfMonth(1)
        val durations = completed.mapNotNull { it.duration }.filter { !it.isNegative }
        val dates = completed.map { it.date }.toSet()
        val start = trackingStart ?: completed.minOfOrNull { it.date } ?: today
        val missed = ScheduleCalculator.missedDates(start, today, trainingDays, dates)
        val thirtyDaysAgo = today.minusDays(30)

        val perWeek = completed.groupingBy { ScheduleCalculator.weekStart(it.date) }.eachCount()
        val weekly = (weeksInChart - 1 downTo 0).map { back ->
            val week = weekStart.minusWeeks(back.toLong())
            WeekCount(week, perWeek[week] ?: 0)
        }

        return TrainingStats(
            thisWeek = completed.count { !it.date.isBefore(weekStart) && !it.date.isAfter(today) },
            thisMonth = completed.count { !it.date.isBefore(monthStart) && !it.date.isAfter(today) },
            total = completed.size,
            averageDuration = if (durations.isEmpty()) null else durations.reduce(Duration::plus).dividedBy(durations.size.toLong()),
            totalVolumeKg = completed.sumOf { it.volumeKg },
            monthVolumeKg = completed.filter { !it.date.isBefore(monthStart) }.sumOf { it.volumeKg },
            totalDuration = durations.fold(Duration.ZERO, Duration::plus),
            missedLast30Days = missed.count { !it.isBefore(thirtyDaysAgo) },
            missedTotal = missed.size,
            weeklyStreak = ScheduleCalculator.weeklyStreak(completed.map { it.date }, today, trainingDays.size),
            weeklyCounts = weekly,
        )
    }
}
