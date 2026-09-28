package com.boykodmytr.gymtracker.data

import com.boykodmytr.gymtracker.data.settings.TrainingDaysCodec
import com.boykodmytr.gymtracker.domain.model.AppSettings
import com.boykodmytr.gymtracker.domain.model.TrainingDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class TrainingDaysCodecTest {
    @Test
    fun `round trip`() {
        val days = AppSettings.DEFAULT_TRAINING_DAYS
        assertEquals("1@18:00,3@18:00,5@18:00", TrainingDaysCodec.encode(days))
        assertEquals(days, TrainingDaysCodec.decode(TrainingDaysCodec.encode(days)))
    }

    @Test
    fun `empty and malformed input`() {
        assertEquals(emptyList<TrainingDay>(), TrainingDaysCodec.decode(""))
        assertEquals(
            listOf(TrainingDay(DayOfWeek.TUESDAY, LocalTime.of(7, 30))),
            TrainingDaysCodec.decode("x@y,2@07:30,9@10:00"),
        )
    }
}
