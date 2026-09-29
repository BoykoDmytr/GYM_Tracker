package com.boykodmytr.gymtracker.domain.transfer

import com.boykodmytr.gymtracker.domain.model.BuiltInMeasurementTypes
import com.boykodmytr.gymtracker.domain.model.MeasurementType
import com.boykodmytr.gymtracker.domain.model.MeasurementUnit
import java.time.LocalDate
import kotlin.math.abs

/** Where a parameter from the file goes: an existing type, or a new custom one. */
sealed interface MeasurementTarget {
    data class Existing(val typeId: String) : MeasurementTarget
    data class New(val name: String, val unit: MeasurementUnit) : MeasurementTarget
}

data class PlannedMeasurement(val target: MeasurementTarget, val date: LocalDate, val value: Double, val note: String)

data class BodyImportPlan(
    val measurements: List<PlannedMeasurement>,
    val newTypes: List<MeasurementTarget.New>,
    val duplicates: Int,
    val issues: List<ImportIssue>,
) {
    val isEmpty: Boolean get() = measurements.isEmpty()
}

/** Reads "Дата; Параметр; Значення; Одиниця; Нотатка" files, like the app's body export. */
object BodyImport {

    fun plan(
        rows: List<List<String>>,
        mapping: BodyColumnMapping,
        types: List<MeasurementType>,
        existing: List<ExistingMeasurement>,
    ): BodyImportPlan {
        val issues = mutableListOf<ImportIssue>()
        val planned = mutableListOf<PlannedMeasurement>()
        val newTypes = LinkedHashMap<String, MeasurementTarget.New>()
        var duplicates = 0
        val typesByKey = types.associateBy { HeaderText.key(it.name) } + types.associateBy { HeaderText.key(it.id) }
        val dateColumn = mapping.fields.getValue(BodyField.DATE)
        val order = Values.detectDateOrder(rows.map { it.cell(dateColumn) })

        fun cell(row: List<String>, field: BodyField) = mapping.fields[field]?.let { row.cell(it) }.orEmpty().trim()

        for (index in (mapping.headerRow + 1) until rows.size) {
            val row = rows[index]
            val line = index + 1
            if (row.all { it.isBlank() }) continue
            val dateRaw = cell(row, BodyField.DATE)
            val name = Csv.unguardText(cell(row, BodyField.PARAMETER))
            val valueRaw = cell(row, BodyField.VALUE)
            if (dateRaw.isEmpty() && name.isEmpty() && valueRaw.isEmpty()) continue
            val date = Values.date(dateRaw, order)
            if (date == null) {
                issues += if (dateRaw.isEmpty()) ImportIssue(IssueKind.NO_DATE, line) else ImportIssue(IssueKind.BAD_DATE, line, dateRaw)
                continue
            }
            if (name.isEmpty()) {
                issues += ImportIssue(IssueKind.NO_PARAMETER, line)
                continue
            }
            val value = Values.number(valueRaw)?.value?.takeIf { it > 0 }
            if (value == null) {
                issues += ImportIssue(IssueKind.BAD_VALUE, line, valueRaw)
                continue
            }
            val key = HeaderText.key(name)
            val type = typesByKey[key] ?: if (key in WEIGHT_NAMES) types.firstOrNull { it.id == BuiltInMeasurementTypes.WEIGHT } else null
            val target: MeasurementTarget = if (type != null) {
                MeasurementTarget.Existing(type.id)
            } else {
                newTypes.getOrPut(key) { MeasurementTarget.New(name, unitFrom(cell(row, BodyField.UNIT))) }
            }
            val known = target is MeasurementTarget.Existing &&
                existing.any { it.typeId == target.typeId && it.date == date && abs(it.value - value) < 0.005 }
            val repeated = planned.any { it.target == target && it.date == date && abs(it.value - value) < 0.005 }
            if (known || repeated) {
                duplicates++
                issues += ImportIssue(IssueKind.MEASUREMENT_DUPLICATE, line, valueRaw, date)
                continue
            }
            planned += PlannedMeasurement(target, date, value, Csv.unguardText(cell(row, BodyField.NOTE)))
        }
        return BodyImportPlan(planned, newTypes.values.toList(), duplicates, issues)
    }

    /** Unit of a new parameter from the file's unit column; girths (cm) when it says nothing else. */
    fun unitFrom(raw: String): MeasurementUnit {
        if ('%' in raw) return MeasurementUnit.PERCENT
        return when (HeaderText.key(raw)) {
            "кг", "kg", "kilograms", "кілограми" -> MeasurementUnit.KG
            "percent", "відсоток", "відсотки" -> MeasurementUnit.PERCENT
            else -> MeasurementUnit.CM
        }
    }

    private val WEIGHT_NAMES = setOf("вага", "вага тіла", "weight", "body weight", "bodyweight", "вес")
}
