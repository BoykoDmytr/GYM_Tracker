package com.boykodmytr.gymtracker.core.database

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/** Instants are stored as epoch millis, dates as epoch days: both sort and compare correctly in SQL. */
class Converters {
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
    @TypeConverter fun dateToLong(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun longToDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
}
