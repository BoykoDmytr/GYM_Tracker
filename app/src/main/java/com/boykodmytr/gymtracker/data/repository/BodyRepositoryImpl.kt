package com.boykodmytr.gymtracker.data.repository

import com.boykodmytr.gymtracker.core.common.newId
import com.boykodmytr.gymtracker.core.database.dao.BodyDao
import com.boykodmytr.gymtracker.core.database.entity.BodyMeasurementEntity
import com.boykodmytr.gymtracker.core.database.entity.MeasurementTypeEntity
import com.boykodmytr.gymtracker.core.database.entity.UserProfileEntity
import com.boykodmytr.gymtracker.domain.model.Measurement
import com.boykodmytr.gymtracker.domain.model.MeasurementType
import com.boykodmytr.gymtracker.domain.model.MeasurementUnit
import com.boykodmytr.gymtracker.domain.model.UserProfile
import com.boykodmytr.gymtracker.domain.repository.BodyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BodyRepositoryImpl @Inject constructor(
    private val bodyDao: BodyDao,
    private val clock: Clock,
) : BodyRepository {

    override fun observeProfile(): Flow<UserProfile> =
        bodyDao.observeProfile().map { it?.toDomain() ?: UserProfile() }

    override suspend fun saveProfile(profile: UserProfile) = bodyDao.upsertProfile(
        UserProfileEntity(
            name = profile.name.trim(),
            birthDate = profile.birthDate,
            heightCm = profile.heightCm,
            updatedAt = clock.instant(),
        ),
    )

    override fun observeMeasurementTypes(): Flow<List<MeasurementType>> =
        bodyDao.observeTypes().map { list -> list.map { it.toDomain() } }

    override suspend fun addMeasurementType(name: String, unit: MeasurementUnit): String {
        val now = clock.instant()
        val id = newId()
        bodyDao.insertTypes(
            listOf(MeasurementTypeEntity(id, name.trim(), unit, isBuiltIn = false, bodyDao.maxTypeOrder() + 1, now, now)),
        )
        return id
    }

    override suspend fun deleteMeasurementType(id: String) = bodyDao.deleteCustomType(id)

    override fun observeMeasurements(typeId: String): Flow<List<Measurement>> =
        bodyDao.observeMeasurements(typeId).map { list -> list.map { it.toDomain() } }

    override fun observeAllMeasurements(): Flow<List<Measurement>> =
        bodyDao.observeAllMeasurements().map { list -> list.map { it.toDomain() } }

    override suspend fun addMeasurement(typeId: String, value: Double, date: LocalDate, note: String) {
        val now = clock.instant()
        bodyDao.insertMeasurement(BodyMeasurementEntity(newId(), typeId, value, date, note.trim(), now, now))
    }

    override suspend fun updateMeasurement(measurement: Measurement) {
        val existing = bodyDao.getMeasurement(measurement.id) ?: return
        bodyDao.updateMeasurement(
            existing.copy(
                value = measurement.value,
                date = measurement.date,
                note = measurement.note.trim(),
                updatedAt = clock.instant(),
            ),
        )
    }

    override suspend fun deleteMeasurement(id: String) = bodyDao.deleteMeasurement(id)
}
