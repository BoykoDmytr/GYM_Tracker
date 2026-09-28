package com.boykodmytr.gymtracker.domain

import com.boykodmytr.gymtracker.domain.logic.StatsCalculator
import com.boykodmytr.gymtracker.domain.model.AppSettings
import com.boykodmytr.gymtracker.domain.model.SessionStatus
import com.boykodmytr.gymtracker.domain.model.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

class StatsCalculatorTest {
    private fun summary(date: LocalDate, minutes: Long, volume: Double): SessionSummary {
        val start = date.atTime(18, 0).toInstant(ZoneOffset.UTC)
        return SessionSummary("id$date", "A", date, start, start.plus(Duration.ofMinutes(minutes)), SessionStatus.COMPLETED, 7, 20, volume)
    }

    @Test
    fun `computes counts, averages and missed days`() {
        val today = LocalDate.of(2026, 9, 30) // Wednesday
        val sessions = listOf(
            summary(LocalDate.of(2026, 9, 28), 60, 5000.0), // Mon
            summary(LocalDate.of(2026, 9, 25), 80, 4000.0), // Fri previous week
        )
        val stats = StatsCalculator.compute(sessions, today, AppSettings.DEFAULT_TRAINING_DAYS, LocalDate.of(2026, 9, 21))
        assertEquals(1, stats.thisWeek)
        assertEquals(2, stats.thisMonth)
        assertEquals(2, stats.total)
        assertEquals(Duration.ofMinutes(70), stats.averageDuration)
        assertEquals(9000.0, stats.totalVolumeKg, 1e-9)
        // Mon 21 and Wed 23 missed; today (Wed 30) not counted yet
        assertEquals(2, stats.missedTotal)
        assertEquals(12, stats.weeklyCounts.size)
        assertEquals(1, stats.weeklyCounts.last().count)
    }
}
