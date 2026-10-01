package com.boykodmytr.gymtracker.domain.transfer

import com.boykodmytr.gymtracker.domain.model.SetTarget

/** What a column of a program file can mean. */
enum class ProgramField(val synonyms: List<String>) {
    PROGRAM(listOf("програма", "назва програми", "program", "program name", "программа")),
    WORKOUT(listOf("тренування", "день", "workout", "day", "тренировка")),
    EXERCISE(listOf("вправа", "назва вправи", "exercise", "exercise name", "упражнение")),
    SETS(listOf("підходи", "підходів", "сети", "sets", "подходы")),
    REPS(listOf("повторення", "повторів", "повт", "reps", "повторения")),
    WEIGHT(listOf("вага", "вага кг", "робоча вага", "стартова вага", "weight", "weight kg", "вес")),
    REST(listOf("відпочинок", "відпочинок с", "відпочинок сек", "rest", "rest s", "rest sec", "отдых")),
    NOTE(listOf("нотатка", "нотатки", "примітка", "техніка", "note", "notes", "заметка")),
}

/**
 * Header line of a program file: workouts, exercises and set/rep ranges, but no date — a file with a date
 * column is workout history and goes to [ColumnMapping] instead.
 */
data class ProgramColumnMapping(val headerRow: Int, val fields: Map<ProgramField, Int>) {
    companion object {
        private val REQUIRED = listOf(ProgramField.WORKOUT, ProgramField.EXERCISE, ProgramField.SETS, ProgramField.REPS)

        fun detect(rows: List<List<String>>): ProgramColumnMapping? {
            for ((index, row) in rows.take(40).withIndex()) {
                val keys = row.map(HeaderText::key)
                if (keys.any { it == "дата" || it == "date" }) return null
                val fields = mutableMapOf<ProgramField, Int>()
                for ((column, key) in keys.withIndex()) {
                    if (key.isEmpty()) continue
                    val field = ProgramField.entries.firstOrNull { f -> f !in fields && f.synonyms.any { HeaderText.key(it) == key } }
                    if (field != null) fields[field] = column
                }
                if (REQUIRED.all { it in fields }) return ProgramColumnMapping(index, fields)
            }
            return null
        }
    }
}

data class ParsedProgramExercise(
    val name: String,
    val target: SetTarget,
    val restSeconds: Int?,
    val note: String,
    val row: Int,
)

data class ParsedProgramWorkout(val name: String, val exercises: List<ParsedProgramExercise>)

data class ParsedProgram(val name: String, val workouts: List<ParsedProgramWorkout>) {
    val exerciseCount: Int get() = workouts.sumOf { it.exercises.size }
}

data class ParsedProgramFile(val programs: List<ParsedProgram>, val issues: List<ImportIssue>)

/**
 * Reads a training program: one row per exercise, in the order they are done; rows with the same
 * workout name form one workout, workouts keep the order of their first row (that order is the
 * A → B → C rotation). Several program names in one file give several programs.
 */
object ProgramFileParser {

    fun parse(rows: List<List<String>>, mapping: ProgramColumnMapping, defaultProgramName: String): ParsedProgramFile {
        val issues = mutableListOf<ImportIssue>()
        val programs = LinkedHashMap<String, Pair<String, LinkedHashMap<String, Pair<String, MutableList<ParsedProgramExercise>>>>>()
        fun cell(row: List<String>, field: ProgramField) = mapping.fields[field]?.let { row.cell(it) }.orEmpty().trim()

        for (index in (mapping.headerRow + 1) until rows.size) {
            val row = rows[index]
            val line = index + 1
            if (row.all { it.isBlank() }) continue
            val workout = WorkoutFileParser.cleanName(cell(row, ProgramField.WORKOUT))
            val exercise = WorkoutFileParser.cleanName(cell(row, ProgramField.EXERCISE))
            if (workout.isEmpty() && exercise.isEmpty()) continue
            if (exercise.isEmpty()) { issues += ImportIssue(IssueKind.NO_EXERCISE, line); continue }
            if (workout.isEmpty()) { issues += ImportIssue(IssueKind.NO_WORKOUT, line, exercise); continue }

            val sets = range(cell(row, ProgramField.SETS))
            if (sets == null) { issues += ImportIssue(IssueKind.BAD_SETS, line, cell(row, ProgramField.SETS)); continue }
            val reps = range(cell(row, ProgramField.REPS))
            if (reps == null) { issues += ImportIssue(IssueKind.BAD_REPS, line, cell(row, ProgramField.REPS)); continue }

            val weightRaw = cell(row, ProgramField.WEIGHT)
            val weight = if (weightRaw.isEmpty()) null else Values.number(weightRaw)?.value?.takeIf { it >= 0 }
            if (weightRaw.isNotEmpty() && weight == null) { issues += ImportIssue(IssueKind.BAD_WEIGHT, line, weightRaw); continue }

            val restRaw = cell(row, ProgramField.REST)
            val rest = if (restRaw.isEmpty()) null else restSeconds(restRaw)
            if (restRaw.isNotEmpty() && rest == null) issues += ImportIssue(IssueKind.BAD_REST, line, restRaw)

            val programName = WorkoutFileParser.cleanName(cell(row, ProgramField.PROGRAM)).ifEmpty { defaultProgramName }
            val workouts = programs.getOrPut(HeaderText.key(programName)) { programName to LinkedHashMap() }.second
            val exercises = workouts.getOrPut(HeaderText.key(workout)) { workout to mutableListOf() }.second
            exercises += ParsedProgramExercise(
                name = exercise,
                target = SetTarget(sets.first, sets.second, reps.first, reps.second, weight?.takeIf { it > 0 }),
                restSeconds = rest,
                note = Csv.unguardText(cell(row, ProgramField.NOTE)),
                row = line,
            )
        }
        return ParsedProgramFile(
            programs = programs.values.map { (name, workouts) ->
                ParsedProgram(name, workouts.values.map { (workoutName, exercises) -> ParsedProgramWorkout(workoutName, exercises) })
            },
            issues = issues,
        )
    }

    private val RANGE = Regex("""^(\d{1,3})(?:\s*[-–—]\s*(\d{1,3}))?$""")

    /** "3" → 3..3, "2-3" / "8–12" → 2..3. */
    fun range(raw: String): Pair<Int, Int>? {
        val m = RANGE.matchEntire(raw.trim()) ?: return null
        val min = m.groupValues[1].toInt()
        val max = m.groupValues[2].toIntOrNull() ?: min
        return if (min in 1..max) min to max else null
    }

    private val MIN_SEC = Regex("""^(\d{1,2}):(\d{2})$""")
    private val MINUTES = Regex("""^(\d+(?:[.,]\d+)?)\s*(?:хв|мін|min|m)\.?$""", RegexOption.IGNORE_CASE)
    private val SECONDS = Regex("""^(\d{1,4})\s*(?:с|сек|s|sec)?\.?$""", RegexOption.IGNORE_CASE)

    /** "90", "90 с", "1:30", "2 хв", "2,5 хв" → seconds, within what the program editor accepts (5 s … 1 h). */
    fun restSeconds(raw: String): Int? {
        val text = raw.trim()
        val seconds = MIN_SEC.matchEntire(text)?.let { it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
            ?: MINUTES.matchEntire(text)?.let { (it.groupValues[1].replace(',', '.').toDouble() * 60).toInt() }
            ?: SECONDS.matchEntire(text)?.groupValues?.get(1)?.toInt()
        return seconds?.takeIf { it in 5..3600 }
    }

    fun exerciseCounts(file: ParsedProgramFile): Map<String, Int> {
        val counts = LinkedHashMap<String, Int>()
        file.programs.forEach { p -> p.workouts.forEach { w -> w.exercises.forEach { counts[it.name] = (counts[it.name] ?: 0) + 1 } } }
        return counts
    }
}
