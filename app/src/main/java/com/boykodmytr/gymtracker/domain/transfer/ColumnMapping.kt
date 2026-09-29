package com.boykodmytr.gymtracker.domain.transfer

import java.util.Locale

/** What a column of a workout file can mean. Headers are matched by [synonyms] (see [HeaderText.key]). */
enum class WorkoutField(val required: Boolean, val synonyms: List<String>) {
    DATE(true, listOf("дата", "дата тренування", "день", "date", "workout date", "day")),
    START_TIME(false, listOf("час", "час початку", "початок", "start", "start time", "time", "время", "время начала")),
    DURATION(false, listOf("тривалість", "тривалість хв", "тривалість тренування", "duration", "duration min", "длительность", "время тренировки")),
    WORKOUT(false, listOf("тренування", "назва тренування", "програма", "workout", "workout name", "routine", "тренировка", "день тренування")),
    WORKOUT_NOTES(false, listOf("нотатки тренування", "примітки тренування", "коментар до тренування", "самопочуття", "workout notes", "workout note", "заметки тренировки")),
    EXERCISE(true, listOf("вправа", "назва вправи", "exercise", "exercise name", "упражнение", "movement")),
    PLAN(false, listOf("план", "сети × повт", "сети х повт", "підходи × повторення", "target", "plan", "sets x reps")),
    SET_NUMBER(false, listOf("підхід", "номер підходу", "сет", "set", "set order", "set number", "подход", "№ підходу")),
    WEIGHT(false, listOf("вага", "вага кг", "вага (кг)", "кг", "weight", "weight kg", "load", "вес", "вес кг")),
    REPS(false, listOf("повторення", "повторів", "повт", "кількість повторень", "reps", "repetitions", "повторы", "повторения")),
    RPE(false, listOf("rpe")),
    FAILURE(false, listOf("відмова", "до відмови", "failure", "to failure", "отказ")),
    SET_NOTE(false, listOf("нотатка підходу", "примітка", "примітки", "нотатка", "нотатки", "коментар", "note", "notes", "set note", "comment", "заметка", "заметки")),
    EXERCISE_NOTE(false, listOf("нотатка вправи", "нотатки вправи", "exercise note", "exercise notes")),
    BODY_WEIGHT(false, listOf("вага тіла", "вага тіла кг", "body weight", "bodyweight", "вес тела")),
}

enum class BodyField(val required: Boolean, val synonyms: List<String>) {
    DATE(true, listOf("дата", "date")),
    PARAMETER(true, listOf("параметр", "показник", "вимір", "measurement", "parameter", "metric", "type")),
    VALUE(true, listOf("значення", "value", "значение")),
    UNIT(false, listOf("одиниця", "одиниці", "unit", "units", "единица")),
    NOTE(false, listOf("нотатка", "примітка", "note", "notes", "комментарий")),
}

enum class SetValueKind { WEIGHT, REPS }

/** "С1 вага (кг)" / "С1 повт." style columns: one row holds all sets of an exercise. */
data class WideSetColumn(val setNumber: Int, val kind: SetValueKind)

/**
 * Which column holds what. [headerRow] is the index of the header line in the file (title lines above
 * it are skipped). Built by [ColumnMapping.detect] and adjustable by the user before importing.
 */
data class ColumnMapping(
    val headerRow: Int,
    val headers: List<String>,
    val fields: Map<WorkoutField, Int>,
    val wideSets: Map<Int, WideSetColumn>,
    /** Weights in pounds (header says lb/lbs) are converted to kg. */
    val weightInPounds: Boolean = false,
) {
    val isWide: Boolean get() = wideSets.isNotEmpty()

    /** Column-level problems that make importing impossible, as user-facing text. */
    fun missingRequired(): List<WorkoutField> = buildList {
        if (WorkoutField.EXERCISE !in fields) add(WorkoutField.EXERCISE)
        if (WorkoutField.REPS !in fields && wideSets.values.none { it.kind == SetValueKind.REPS }) add(WorkoutField.REPS)
    }

    fun withField(field: WorkoutField, column: Int?): ColumnMapping {
        val updated = fields.filterValues { it != column }.toMutableMap()
        if (column == null) updated.remove(field) else updated[field] = column
        return copy(fields = updated, wideSets = if (column == null) wideSets else wideSets - column)
    }

    companion object {
        private const val MAX_HEADER_SEARCH = 40

        /**
         * Finds the header line: the first of the first [MAX_HEADER_SEARCH] rows naming an exercise
         * column and reps (as a column or per-set columns). Returns null when no line qualifies.
         */
        fun detect(rows: List<List<String>>): ColumnMapping? {
            for ((index, row) in rows.take(MAX_HEADER_SEARCH).withIndex()) {
                val mapping = fromHeader(index, row)
                if (mapping.missingRequired().isEmpty()) return mapping
            }
            return null
        }

        fun fromHeader(headerRow: Int, header: List<String>): ColumnMapping {
            val fields = mutableMapOf<WorkoutField, Int>()
            val wide = mutableMapOf<Int, WideSetColumn>()
            val keys = header.map(HeaderText::key)
            // Exact synonyms first, so "Вага тіла" never takes the "Вага" slot or the other way round.
            for ((column, key) in keys.withIndex()) {
                if (key.isEmpty()) continue
                val field = WorkoutField.entries.firstOrNull { f -> f !in fields && f.synonyms.any { HeaderText.key(it) == key } }
                if (field != null) {
                    fields[field] = column
                    continue
                }
                wideSetColumn(key)?.let { wide[column] = it }
            }
            // Then loose matches ("Вага, кг", "Weight (lbs)") for fields still missing.
            for ((column, key) in keys.withIndex()) {
                if (key.isEmpty() || column in fields.values || column in wide) continue
                val field = WorkoutField.entries.firstOrNull { f ->
                    f !in fields && f.synonyms.any { s -> HeaderText.key(s).let { syn -> syn.length >= 3 && key.startsWith(syn) } }
                } ?: continue
                if (field == WorkoutField.WEIGHT && key.startsWith(HeaderText.key("вага тіла"))) continue
                fields[field] = column
            }
            val pounds = fields[WorkoutField.WEIGHT]?.let { HeaderText.mentionsPounds(header[it]) } ?: false ||
                wide.any { (column, set) -> set.kind == SetValueKind.WEIGHT && HeaderText.mentionsPounds(header[column]) }
            return ColumnMapping(headerRow, header, fields, wide, pounds)
        }

        private val SET_NUMBER_PREFIX = Regex("""^(?:с|c|s|сет|set|підхід|подход|p|п)\s*(\d{1,2})\s*(.*)$""")
        private val SET_NUMBER_SUFFIX = Regex("""^(.*?)\s*(\d{1,2})$""")

        /** "с1 вага кг", "set 2 reps", "вага 3", "повт 4" → set number and kind. */
        internal fun wideSetColumn(key: String): WideSetColumn? {
            val (number, rest) = SET_NUMBER_PREFIX.matchEntire(key)?.let { it.groupValues[1].toInt() to it.groupValues[2] }
                ?: SET_NUMBER_SUFFIX.matchEntire(key)?.let { it.groupValues[2].toInt() to it.groupValues[1] }
                ?: return null
            if (number !in 1..30) return null
            val kind = when {
                WEIGHT_WORDS.any { rest.startsWith(it) } -> SetValueKind.WEIGHT
                REPS_WORDS.any { rest.startsWith(it) } -> SetValueKind.REPS
                else -> return null
            }
            return WideSetColumn(number, kind)
        }

        private val WEIGHT_WORDS = listOf("вага", "weight", "кг", "kg", "вес", "lb")
        private val REPS_WORDS = listOf("повт", "reps", "rep", "раз", "повтор")
    }
}

/** Body measurement files: date, parameter name, value, unit, note. */
data class BodyColumnMapping(val headerRow: Int, val fields: Map<BodyField, Int>) {
    companion object {
        fun detect(rows: List<List<String>>): BodyColumnMapping? {
            for ((index, row) in rows.take(40).withIndex()) {
                val keys = row.map(HeaderText::key)
                val fields = mutableMapOf<BodyField, Int>()
                for ((column, key) in keys.withIndex()) {
                    val field = BodyField.entries.firstOrNull { f -> f !in fields && f.synonyms.any { HeaderText.key(it) == key } } ?: continue
                    fields[field] = column
                }
                if (BodyField.entries.filter { it.required }.all { it in fields }) return BodyColumnMapping(index, fields)
            }
            return null
        }
    }
}

object HeaderText {
    /**
     * Comparison key for headers and names: lower case, Latin look-alikes inside Cyrillic words
     * folded to Cyrillic, apostrophes unified, punctuation dropped, spaces collapsed.
     */
    fun key(text: String): String {
        val lower = text.lowercase(Locale.ROOT).replace('ё', 'е').replace('ʼ', '\'').replace('’', '\'').replace('`', '\'')
        val folded = if (lower.any { it in 'Ѐ'..'ӿ' }) lower.map { LOOKALIKES[it] ?: it }.joinToString("") else lower
        return folded
            .replace(Regex("""[^\p{L}\p{N}']+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    fun mentionsPounds(header: String): Boolean = Regex("""\b(lb|lbs|фунт\w*)\b""", RegexOption.IGNORE_CASE).containsMatchIn(header)

    private val LOOKALIKES = mapOf('a' to 'а', 'c' to 'с', 'e' to 'е', 'i' to 'і', 'o' to 'о', 'p' to 'р', 'x' to 'х', 'y' to 'у', 'k' to 'к', 'm' to 'м', 't' to 'т', 'h' to 'н', 'b' to 'в')
}
