package com.boykodmytr.gymtracker.domain.transfer

import com.boykodmytr.gymtracker.domain.model.Measurement
import com.boykodmytr.gymtracker.domain.model.MeasurementType
import com.boykodmytr.gymtracker.domain.model.MeasurementUnit
import com.boykodmytr.gymtracker.domain.model.WorkoutSession
import java.time.LocalTime
import java.time.ZoneId

/**
 * How the file is laid out for the spreadsheet that will open it. Excel splits a double-clicked CSV
 * by the list separator of the Windows region: ";" with decimal comma for Ukrainian and most European
 * settings, "," with decimal point for English (US/UK). Google Sheets and LibreOffice read both.
 */
enum class CsvDialect(val delimiter: Char, val decimalComma: Boolean) {
    SEMICOLON(';', true),
    COMMA(',', false),
}

/**
 * Builds CSV text. A UTF-8 BOM is written first so Excel shows Cyrillic correctly; lines end with CRLF
 * as RFC 4180 and Excel expect. Dates are ISO (2026-05-12), which every spreadsheet recognises as a
 * date regardless of locale; times are HH:mm; weights are kilograms.
 */
object CsvExport {

    val WORKOUT_HEADERS = listOf(
        "Дата", "Час початку", "Тривалість (хв)", "Тренування", "Нотатки тренування", "Вправа", "План",
        "Підхід", "Вага (кг)", "Повторення", "RPE", "Відмова", "Нотатка підходу", "Нотатка вправи",
    )

    val BODY_HEADERS = listOf("Дата", "Параметр", "Значення", "Одиниця", "Нотатка")

    /** One row per set, sessions in date order. A workout without sets still gets one row. */
    fun workouts(sessions: List<WorkoutSession>, zone: ZoneId, dialect: CsvDialect): String {
        val rows = mutableListOf(WORKOUT_HEADERS)
        for (session in sessions.sortedWith(compareBy({ it.date }, { it.startedAt }))) {
            val localStart = session.startedAt.atZone(zone).toLocalTime()
            val sessionCells = listOf(
                session.date.format(Values.ISO_DATE_FORMAT),
                // Imported workouts without a known time start at midnight; do not invent "00:00".
                if (localStart == LocalTime.MIDNIGHT) "" else localStart.format(Values.TIME_FORMAT),
                session.duration?.toMinutes()?.coerceAtLeast(0)?.toString().orEmpty(),
                text(session.name),
                text(session.notes),
            )
            val exercises = session.exercises.filter { it.sets.isNotEmpty() }
            if (exercises.isEmpty()) {
                rows += sessionCells + List(WORKOUT_HEADERS.size - sessionCells.size) { "" }
                continue
            }
            for (exercise in exercises.sortedBy { it.orderIndex }) {
                val t = exercise.target
                val plan = "${range(t.setsMin, t.setsMax)}×${range(t.repsMin, t.repsMax)}"
                for (set in exercise.sets.sortedBy { it.setNumber }) {
                    rows += sessionCells + listOf(
                        text(exercise.exerciseName),
                        plan,
                        set.setNumber.toString(),
                        Values.formatNumber(set.weightKg, dialect.decimalComma),
                        set.reps.toString(),
                        set.rpe?.let { Values.formatNumber(it, dialect.decimalComma) }.orEmpty(),
                        if (set.isFailure) "так" else "",
                        text(set.note.orEmpty()),
                        text(exercise.notes),
                    )
                }
            }
        }
        return render(rows, dialect)
    }

    fun measurements(measurements: List<Measurement>, types: List<MeasurementType>, dialect: CsvDialect): String {
        val typesById = types.associateBy { it.id }
        val rows = mutableListOf(BODY_HEADERS)
        val ordered = measurements.sortedWith(compareBy<Measurement>({ it.date }, { typesById[it.typeId]?.orderIndex ?: Int.MAX_VALUE }))
        for (m in ordered) {
            val type = typesById[m.typeId] ?: continue
            rows += listOf(
                m.date.format(Values.ISO_DATE_FORMAT),
                text(type.name),
                Values.formatNumber(m.value, dialect.decimalComma),
                unitLabel(type.unit),
                text(m.note),
            )
        }
        return render(rows, dialect)
    }

    fun unitLabel(unit: MeasurementUnit): String = when (unit) {
        MeasurementUnit.KG -> "кг"
        MeasurementUnit.CM -> "см"
        MeasurementUnit.PERCENT -> "%"
    }

    private fun render(rows: List<List<String>>, dialect: CsvDialect): String =
        Csv.BOM + rows.joinToString(separator = "\r\n", postfix = "\r\n") { Csv.formatRow(it, dialect.delimiter) }

    private fun text(value: String): String = Csv.guardText(value.replace("\r\n", "\n"))

    // ASCII hyphen: "8–12" with an en dash reads fine, but some spreadsheets mangle non-ASCII in plans.
    private fun range(min: Int, max: Int) = if (min == max) "$min" else "$min-$max"
}
