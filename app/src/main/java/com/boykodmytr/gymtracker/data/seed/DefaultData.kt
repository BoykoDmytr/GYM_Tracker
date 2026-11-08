package com.boykodmytr.gymtracker.data.seed

import com.boykodmytr.gymtracker.domain.model.BuiltInMeasurementTypes
import com.boykodmytr.gymtracker.domain.model.MeasurementUnit

data class SeedMeasurementType(val id: String, val name: String, val unit: MeasurementUnit)

/** Measurement types every install starts with; the program lives in [BuiltInPrograms]. */
object DefaultData {

    val measurementTypes = listOf(
        SeedMeasurementType(BuiltInMeasurementTypes.WEIGHT, "Вага", MeasurementUnit.KG),
        SeedMeasurementType("chest", "Обхват грудей", MeasurementUnit.CM),
        SeedMeasurementType("waist", "Обхват талії", MeasurementUnit.CM),
        SeedMeasurementType("hips", "Обхват стегон (таз)", MeasurementUnit.CM),
        SeedMeasurementType("biceps", "Обхват біцепса", MeasurementUnit.CM),
        SeedMeasurementType("forearm", "Обхват передпліччя", MeasurementUnit.CM),
        SeedMeasurementType("thigh", "Обхват стегна", MeasurementUnit.CM),
        SeedMeasurementType("calf", "Обхват гомілки", MeasurementUnit.CM),
        SeedMeasurementType("neck", "Обхват шиї", MeasurementUnit.CM),
        SeedMeasurementType("shoulders", "Обхват плечей", MeasurementUnit.CM),
        SeedMeasurementType("body_fat", "Відсоток жиру", MeasurementUnit.PERCENT),
    )
}
