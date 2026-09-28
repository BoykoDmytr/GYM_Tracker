package com.boykodmytr.gymtracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.boykodmytr.gymtracker.core.database.entity.BodyMeasurementEntity
import com.boykodmytr.gymtracker.core.database.entity.MeasurementTypeEntity
import com.boykodmytr.gymtracker.core.database.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyDao {
    @Query("SELECT * FROM user_profile WHERE id = ${UserProfileEntity.SINGLE_USER_ID}")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Upsert
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM measurement_type ORDER BY order_index, name")
    fun observeTypes(): Flow<List<MeasurementTypeEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTypes(types: List<MeasurementTypeEntity>)

    @Query("SELECT COALESCE(MAX(order_index), -1) FROM measurement_type")
    suspend fun maxTypeOrder(): Int

    @Query("DELETE FROM measurement_type WHERE id = :id AND is_built_in = 0")
    suspend fun deleteCustomType(id: String)

    @Query("SELECT * FROM body_measurement WHERE type_id = :typeId ORDER BY date DESC, created_at DESC")
    fun observeMeasurements(typeId: String): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM body_measurement ORDER BY date DESC, created_at DESC")
    fun observeAllMeasurements(): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM body_measurement WHERE id = :id")
    suspend fun getMeasurement(id: String): BodyMeasurementEntity?

    @Insert
    suspend fun insertMeasurement(measurement: BodyMeasurementEntity)

    @Update
    suspend fun updateMeasurement(measurement: BodyMeasurementEntity)

    @Query("DELETE FROM body_measurement WHERE id = :id")
    suspend fun deleteMeasurement(id: String)
}
