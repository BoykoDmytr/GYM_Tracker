package com.boykodmytr.gymtracker.domain.transfer

import com.boykodmytr.gymtracker.domain.model.WeightUnit
import java.time.LocalDate
import java.time.LocalTime

enum class IssueSeverity {
    /** The row (or set) was not imported. */
    ERROR,

    /** Imported with a change, or skipped on purpose to protect existing data. */
    WARNING,

    /** Nothing lost; e.g. an exact duplicate that already is in the app. */
    INFO,
}

enum class IssueKind(val severity: IssueSeverity) {
    NO_DATE(IssueSeverity.ERROR),
    BAD_DATE(IssueSeverity.ERROR),
    NO_EXERCISE(IssueSeverity.ERROR),
    BAD_WEIGHT(IssueSeverity.ERROR),
    BAD_REPS(IssueSeverity.ERROR),
    MISSING_REPS(IssueSeverity.ERROR),
    BAD_TIME(IssueSeverity.WARNING),
    BAD_DURATION(IssueSeverity.WARNING),
    BAD_RPE(IssueSeverity.WARNING),
    BAD_FAILURE(IssueSeverity.WARNING),
    BAD_BODY_WEIGHT(IssueSeverity.WARNING),
    ANNOTATED_VALUE(IssueSeverity.WARNING),
    DUPLICATE_ROW(IssueSeverity.WARNING),
    SESSION_CONFLICT(IssueSeverity.WARNING),
    SESSION_DUPLICATE(IssueSeverity.INFO),

    /** Imported, but the app already has another workout that day: maybe the same one under another name. */
    SAME_DATE(IssueSeverity.WARNING),
    MEASUREMENT_DUPLICATE(IssueSeverity.INFO),
    BAD_VALUE(IssueSeverity.ERROR),
    NO_PARAMETER(IssueSeverity.ERROR),
}

/**
 * A problem found while reading or planning. [row] is the 1-based line of the file (null for issues
 * about a whole workout, which carry [date] and [workout] instead); [value] is the offending text.
 */
data class ImportIssue(
    val kind: IssueKind,
    val row: Int? = null,
    val value: String? = null,
    val date: LocalDate? = null,
    val workout: String? = null,
) {
    val severity: IssueSeverity get() = kind.severity
}

data class ParsedSet(
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Double?,
    val isFailure: Boolean,
    val note: String?,
    val row: Int,
)

data class ParsedExercise(
    val name: String,
    val note: String?,
    /** setsMin, setsMax, repsMin, repsMax from a "4 × 8-12" plan column. */
    val plan: IntArray?,
    val sets: List<ParsedSet>,
)

data class ParsedSession(
    val date: LocalDate,
    val startTime: LocalTime?,
    val durationMinutes: Long?,
    val name: String,
    val notes: String,
    val bodyWeightKg: Double?,
    val exercises: List<ParsedExercise>,
    val firstRow: Int,
) {
    val setCount: Int get() = exercises.sumOf { it.sets.size }
}

data class ParsedWorkoutFile(
    val sessions: List<ParsedSession>,
    val issues: List<ImportIssue>,
    /** Rows that looked like titles, repeated headers or empty plan lines. */
    val ignoredRows: Int,
)

/**
 * Turns spreadsheet rows into workouts. Understands one row per set (the app's own export) and one
 * row per exercise with per-set columns ("С1 вага", "С1 повт." …). The date may be a column or a
 * "Дата: 12.05.2026" line above a block of exercises, as in hand-made training journals.
 */
class WorkoutFileParser(
    private val rows: List<List<String>>,
    private val mapping: ColumnMapping,
    private val defaultWorkoutName: String,
) {
    private val issues = mutableListOf<ImportIssue>()
    private var ignored = 0
    private val sessions = LinkedHashMap<SessionKey, SessionBuilder>()
    private val dateOrder = Values.detectDateOrder(mapping.fields[WorkoutField.DATE]?.let { col -> rows.map { it.cell(col) } }.orEmpty())

    private data class SessionKey(val date: LocalDate, val time: LocalTime?, val name: String)

    private class ExerciseBuilder(val name: String, var note: String?, var plan: IntArray?) {
        val sets = mutableListOf<ParsedSet>()
        val usedNumbers = mutableSetOf<Int>()
    }

    private class SessionBuilder(
        val date: LocalDate,
        val time: LocalTime?,
        val name: String,
        val firstRow: Int,
    ) {
        var duration: Long? = null
        var notes: String = ""
        var bodyWeight: Double? = null
        val exercises = LinkedHashMap<String, ExerciseBuilder>()
    }

    // Block context: "Дата: …", "Вага тіла: …", "Самопочуття: …" lines above a block of rows.
    private var contextDate: LocalDate? = null
    private var contextDateRaw: String? = null
    private var contextBodyWeight: Double? = null
    private var contextNotes: String? = null

    fun parse(): ParsedWorkoutFile {
        // A journal block may open with its "Дата: …" line above the column headers.
        for (index in 0 until mapping.headerRow) readContext(rows[index], index + 1)
        for (index in (mapping.headerRow + 1) until rows.size) {
            val row = rows[index]
            val line = index + 1
            if (row.all { it.isBlank() }) continue
            if (readContext(row, line)) {
                ignored++
                continue
            }
            if (isRepeatedHeader(row)) {
                ignored++
                continue
            }
            if (mapping.isWide) readWideRow(row, line) else readLongRow(row, line)
        }
        return ParsedWorkoutFile(
            sessions = sessions.values.map { it.build() },
            issues = issues,
            ignoredRows = ignored,
        )
    }

    private fun readLongRow(row: List<String>, line: Int) {
        val weightRaw = field(row, WorkoutField.WEIGHT)
        val repsRaw = field(row, WorkoutField.REPS)
        val exerciseName = cleanName(field(row, WorkoutField.EXERCISE))
        if (weightRaw.isBlank() && repsRaw.isBlank()) {
            // A session line without sets (e.g. an exported workout with nothing logged) or a stray label.
            if (exerciseName.isEmpty() && field(row, WorkoutField.DATE).isNotBlank()) session(row, line)
            else ignored++
            return
        }
        if (exerciseName.isEmpty()) {
            issues += ImportIssue(IssueKind.NO_EXERCISE, line)
            return
        }
        val session = session(row, line) ?: return
        val set = readSet(weightRaw, repsRaw, row, line) ?: return
        val requested = Values.integer(field(row, WorkoutField.SET_NUMBER))?.takeIf { it > 0 }
        addSet(session, exerciseName, row, set, requested, line)
    }

    private fun readWideRow(row: List<String>, line: Int) {
        val exerciseName = cleanName(field(row, WorkoutField.EXERCISE))
        val bySet = mapping.wideSets.entries.groupBy({ it.value.setNumber }, { it.key to it.value.kind })
        val filled = bySet.toSortedMap().mapNotNull { (number, columns) ->
            val weight = columns.firstOrNull { it.second == SetValueKind.WEIGHT }?.let { row.cell(it.first) }.orEmpty()
            val reps = columns.firstOrNull { it.second == SetValueKind.REPS }?.let { row.cell(it.first) }.orEmpty()
            if (weight.isBlank() && reps.isBlank()) null else Triple(number, weight, reps)
        }
        if (filled.isEmpty()) {
            // Planned exercise that was not done, a block title, or a spacer line.
            ignored++
            return
        }
        if (exerciseName.isEmpty()) {
            issues += ImportIssue(IssueKind.NO_EXERCISE, line)
            return
        }
        val session = session(row, line) ?: return
        for ((number, weight, reps) in filled) {
            val set = readSet(weight, reps, row, line, includeRowNote = false) ?: continue
            addSet(session, exerciseName, row, set, number, line)
        }
        // In one-row-per-exercise files the note column describes the exercise, not a set.
        field(row, WorkoutField.SET_NOTE).takeIf { it.isNotBlank() }?.let { note ->
            session.exercises[exerciseName]?.let { it.note = listOfNotNull(it.note, note.trim()).distinct().joinToString("; ") }
        }
    }

    private fun readSet(weightRaw: String, repsRaw: String, row: List<String>, line: Int, includeRowNote: Boolean = true): ParsedSet? {
        val notes = mutableListOf<String>()
        val weight: Double = if (weightRaw.isBlank()) {
            0.0
        } else {
            val parsed = Values.number(weightRaw)
            if (parsed == null || parsed.value < 0) {
                issues += ImportIssue(IssueKind.BAD_WEIGHT, line, weightRaw.trim())
                return null
            }
            if (parsed.annotation != null) {
                issues += ImportIssue(IssueKind.ANNOTATED_VALUE, line, weightRaw.trim())
                notes += "вага: ${weightRaw.trim()}"
            }
            if (mapping.weightInPounds) WeightUnit.LB.toKg(parsed.value) else parsed.value
        }
        if (repsRaw.isBlank()) {
            issues += ImportIssue(IssueKind.MISSING_REPS, line, weightRaw.trim())
            return null
        }
        val repsParsed = Values.number(repsRaw)
        val reps = repsParsed?.value?.takeIf { it >= 1 && it == Math.floor(it) && it < 10_000 }?.toInt()
        if (reps == null) {
            issues += ImportIssue(IssueKind.BAD_REPS, line, repsRaw.trim())
            return null
        }
        if (repsParsed.annotation != null) {
            issues += ImportIssue(IssueKind.ANNOTATED_VALUE, line, repsRaw.trim())
            notes += "повторення: ${repsRaw.trim()}"
        }
        val rpeRaw = field(row, WorkoutField.RPE)
        val rpe = if (rpeRaw.isBlank()) null else Values.number(rpeRaw)?.value?.takeIf { it in 1.0..10.0 }.also {
            if (it == null) issues += ImportIssue(IssueKind.BAD_RPE, line, rpeRaw.trim())
        }
        val failureRaw = field(row, WorkoutField.FAILURE)
        val failure = Values.boolean(failureRaw) ?: false.also { issues += ImportIssue(IssueKind.BAD_FAILURE, line, failureRaw.trim()) }
        if (includeRowNote) field(row, WorkoutField.SET_NOTE).trim().takeIf { it.isNotEmpty() }?.let { notes.add(0, Csv.unguardText(it)) }
        return ParsedSet(0, weight, reps, rpe, failure, notes.joinToString("; ").takeIf { it.isNotEmpty() }, line)
    }

    private fun addSet(session: SessionBuilder, exerciseName: String, row: List<String>, set: ParsedSet, requested: Int?, line: Int) {
        val exercise = session.exercises.getOrPut(exerciseName) {
            ExerciseBuilder(
                name = exerciseName,
                note = field(row, WorkoutField.EXERCISE_NOTE).trim().takeIf { it.isNotEmpty() }?.let(Csv::unguardText),
                plan = Values.plan(field(row, WorkoutField.PLAN)),
            )
        }
        val number = requested ?: ((exercise.usedNumbers.maxOrNull() ?: 0) + 1)
        if (!exercise.usedNumbers.add(number)) {
            issues += ImportIssue(IssueKind.DUPLICATE_ROW, line, "$exerciseName #$number")
            return
        }
        exercise.sets += set.copy(setNumber = number)
    }

    /** The session a row belongs to, created on first use; null (with an issue) when the row has no usable date. */
    private fun session(row: List<String>, line: Int): SessionBuilder? {
        val dateRaw = field(row, WorkoutField.DATE)
        val date = when {
            dateRaw.isNotBlank() -> Values.date(dateRaw, dateOrder) ?: run {
                issues += ImportIssue(IssueKind.BAD_DATE, line, dateRaw.trim())
                return null
            }
            contextDate != null -> contextDate!!
            else -> {
                issues += if (contextDateRaw.isNullOrBlank()) ImportIssue(IssueKind.NO_DATE, line) else ImportIssue(IssueKind.BAD_DATE, line, contextDateRaw)
                return null
            }
        }
        val timeRaw = field(row, WorkoutField.START_TIME)
        val time = when {
            timeRaw.isNotBlank() -> Values.time(timeRaw).also { if (it == null) issues += ImportIssue(IssueKind.BAD_TIME, line, timeRaw.trim()) }
            // Exports of other apps often put the time into the date: "2026-05-12 07:30:00".
            WorkoutField.START_TIME !in mapping.fields -> Values.time(dateRaw)
            else -> null
        }
        val name = cleanName(field(row, WorkoutField.WORKOUT)).ifEmpty { defaultWorkoutName }
        val key = SessionKey(date, time, HeaderText.key(name))
        val builder = sessions.getOrPut(key) { SessionBuilder(date, time, name, line) }

        if (builder.duration == null) {
            val durationRaw = field(row, WorkoutField.DURATION)
            if (durationRaw.isNotBlank()) {
                builder.duration = Values.durationMinutes(durationRaw).also { if (it == null) issues += ImportIssue(IssueKind.BAD_DURATION, line, durationRaw.trim()) }
            }
        }
        val notes = field(row, WorkoutField.WORKOUT_NOTES).trim().let(Csv::unguardText).ifEmpty { contextNotes.orEmpty() }
        if (notes.isNotEmpty() && notes !in builder.notes) builder.notes = listOf(builder.notes, notes).filter { it.isNotEmpty() }.joinToString("\n")
        if (builder.bodyWeight == null) {
            val bodyRaw = field(row, WorkoutField.BODY_WEIGHT)
            builder.bodyWeight = if (bodyRaw.isNotBlank()) {
                Values.number(bodyRaw)?.value?.takeIf { it in 20.0..400.0 }.also { if (it == null) issues += ImportIssue(IssueKind.BAD_BODY_WEIGHT, line, bodyRaw.trim()) }
            } else {
                contextBodyWeight
            }
        }
        return builder
    }

    /** Reads a "Дата: … Вага тіла: … Самопочуття: …" line. Returns true when the row was such a line. */
    private fun readContext(row: List<String>, line: Int): Boolean {
        val labels = row.withIndex()
            .map { (column, cell) -> column to HeaderText.key(cell) }
            .filter { (_, label) -> label in CONTEXT_LABELS }
        if (labels.isEmpty()) return false

        // The value is the next non-empty cell, unless that is already the next label.
        fun valueAfter(column: Int): String {
            val next = row.drop(column + 1).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            return if (HeaderText.key(next) in CONTEXT_LABELS) "" else next
        }

        // A new date starts a new block: forget the previous block's body weight and notes first.
        labels.firstOrNull { it.second in DATE_LABELS }?.let { (column, _) ->
            val value = valueAfter(column)
            contextDateRaw = value
            contextDate = if (value.isEmpty()) null else Values.date(value)
            contextBodyWeight = null
            contextNotes = null
        }
        for ((column, label) in labels) {
            val value = valueAfter(column)
            when (label) {
                in BODY_WEIGHT_LABELS -> contextBodyWeight = if (value.isEmpty()) {
                    null
                } else {
                    Values.number(value)?.value?.takeIf { it in 20.0..400.0 }.also {
                        if (it == null) issues += ImportIssue(IssueKind.BAD_BODY_WEIGHT, line, value)
                    }
                }
                in NOTES_LABELS -> contextNotes = value.takeIf { it.isNotEmpty() }
            }
        }
        return true
    }

    private fun isRepeatedHeader(row: List<String>): Boolean {
        val exerciseColumn = mapping.fields[WorkoutField.EXERCISE] ?: return false
        val key = HeaderText.key(row.cell(exerciseColumn))
        return key.isNotEmpty() && key == HeaderText.key(mapping.headers.getOrElse(exerciseColumn) { "" })
    }

    private fun field(row: List<String>, field: WorkoutField): String = mapping.fields[field]?.let { row.cell(it) }.orEmpty()

    private fun SessionBuilder.build() = ParsedSession(
        date = date,
        startTime = time,
        durationMinutes = duration,
        name = name,
        notes = notes,
        bodyWeightKg = bodyWeight,
        exercises = exercises.values.map { e ->
            // Numbers from the file may have gaps (a skipped column); the app keeps them 1..n.
            val sets = e.sets.sortedBy { it.setNumber }.mapIndexed { i, s -> s.copy(setNumber = i + 1) }
            ParsedExercise(e.name, e.note, e.plan, sets)
        },
        firstRow = firstRow,
    )

    companion object {
        private val DATE_LABELS = setOf("дата", "date")
        private val BODY_WEIGHT_LABELS = setOf("вага тіла", "body weight", "вес тела")
        private val NOTES_LABELS = setOf("самопочуття", "нотатки тренування", "how i felt")
        private val CONTEXT_LABELS = DATE_LABELS + BODY_WEIGHT_LABELS + NOTES_LABELS

        /** Trims and collapses inner whitespace; the name is otherwise kept exactly as written. */
        fun cleanName(raw: String): String = Csv.unguardText(raw.trim()).replace(Regex("""\s+"""), " ")
    }
}

internal fun List<String>.cell(index: Int): String = getOrElse(index) { "" }
