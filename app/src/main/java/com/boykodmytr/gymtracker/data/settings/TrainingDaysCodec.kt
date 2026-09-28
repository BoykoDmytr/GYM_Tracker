package com.boykodmytr.gymtracker.data.settings

import com.boykodmytr.gymtracker.domain.model.TrainingDay
import java.time.DayOfWeek
import java.time.LocalTime

/** Serializes the weekly schedule as "1@18:00,3@18:00" (ISO day number @ local time). */
object TrainingDaysCodec {
    fun encode(days: List<TrainingDay>): String =
        days.sortedBy { it.dayOfWeek }.joinToString(",") { "${it.dayOfWeek.value}@${it.time}" }

    fun decode(raw: String): List<TrainingDay> =
        raw.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { token ->
                val parts = token.split("@")
                runCatching { TrainingDay(DayOfWeek.of(parts[0].trim().toInt()), LocalTime.parse(parts[1].trim())) }.getOrNull()
            }
            .distinctBy { it.dayOfWeek }
            .sortedBy { it.dayOfWeek }
}
