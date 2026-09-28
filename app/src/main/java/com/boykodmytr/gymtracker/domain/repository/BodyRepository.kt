package com.boykodmytr.gymtracker.domain.repository

import com.boykodmytr.gymtracker.domain.model.Measurement
import com.boykodmytr.gymtracker.domain.model.MeasurementType
import com.boykodmytr.gymtracker.domain.model.MeasurementUnit
import com.boykodmytr.gymtracker.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface BodyRepository {
    fun observeProfile(): Flow<UserProfile>
    suspend fun saveProfile(profile: UserProfile)

    fun observeMeasurementTypes(): Flow<List<MeasurementType>>
    suspend fun addMeasurementType(name: String, unit: MeasurementUnit): String
    suspend fun deleteMeasurementType(id: String)

    fun observeMeasurements(typeId: String): Flow<List<Measurement>>
    fun observeAllMeasurements(): Flow<List<Measurement>>
    suspend fun addMeasurement(typeId: String, value: Double, date: LocalDate, note: String = "")
    suspend fun updateMeasurement(measurement: Measurement)
    suspend fun deleteMeasurement(id: String)
}
