package com.boykodmytr.gymtracker.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.boykodmytr.gymtracker.domain.model.MeasurementUnit
import java.time.Instant
import java.time.LocalDate

/** Single-row table today; the id column leaves room for multiple local profiles or accounts later. */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = SINGLE_USER_ID,
    val name: String,
    @ColumnInfo(name = "birth_date") val birthDate: LocalDate?,
    @ColumnInfo(name = "height_cm") val heightCm: Double?,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
) {
    companion object {
        const val SINGLE_USER_ID = 1
    }
}

@Entity(tableName = "measurement_type")
data class MeasurementTypeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val unit: MeasurementUnit,
    @ColumnInfo(name = "is_built_in") val isBuiltIn: Boolean,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(
    tableName = "body_measurement",
    foreignKeys = [
        ForeignKey(
            entity = MeasurementTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["type_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["type_id", "date"])],
)
data class BodyMeasurementEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "type_id") val typeId: String,
    val value: Double,
    val date: LocalDate,
    val note: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
