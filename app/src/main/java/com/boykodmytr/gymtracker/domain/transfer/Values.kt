package com.boykodmytr.gymtracker.domain.transfer

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A number read from a cell, with whatever the author wrote around it (e.g. "(2)" for two dumbbells). */
data class CellNumber(val value: Double, val annotation: String? = null)

/**
 * Forgiving readers for values typed by hand into spreadsheets: decimal comma or point, units,
 * non-breaking spaces, "16(2)" / "16×2" notation, several date styles.
 */
object Values {
    // 62,5 | 62.5 | 1 250,5 | -3 ; optional unit; optional "(2)", "×2", "x2" multiplier note.
    private val NUMBER = Regex(
        """^([+-]?\d{1,3}(?:[ \u00A0\u202F]\d{3})+(?:[.,]\d+)?|[+-]?\d+(?:[.,]\d+)?|[+-]?[.,]\d+)\s*(кг|kg|повт\.?|разів|рази|раз|reps?)?\s*(\(\s*\d+\s*\)|[x×хX]\s*\d+)?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /** Parses a plain or annotated number; null for text that is not a number (e.g. "*", "16 + 101"). */
    fun number(raw: String): CellNumber? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val match = NUMBER.matchEntire(text) ?: return null
        val digits = match.groupValues[1].replace(Regex("[ \u00A0\u202F]"), "").replace(',', '.')
        val value = digits.toDoubleOrNull() ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        val annotation = match.groupValues[3].takeIf { it.isNotEmpty() }?.let { text }
        return CellNumber(value, annotation)
    }

    fun integer(raw: String): Int? {
        val parsed = number(raw) ?: return null
        val value = parsed.value
        return if (value == Math.floor(value) && value >= Int.MIN_VALUE && value <= Int.MAX_VALUE) value.toInt() else null
    }

    fun boolean(raw: String): Boolean? = when (raw.trim().lowercase(Locale.ROOT)) {
        "так", "т", "да", "yes", "y", "true", "1", "+", "x", "х", "✓", "✔", "відмова", "failure" -> true
        "ні", "н", "нет", "no", "n", "false", "0", "-", "" -> false
        else -> null
    }

    enum class DateOrder { DAY_FIRST, MONTH_FIRST }

    private val ISO_DATE = Regex("""^(\d{4})[-./](\d{1,2})[-./](\d{1,2})(?:[ T].*)?$""")
    private val NUMERIC_DATE = Regex("""^(\d{1,2})[./-](\d{1,2})[./-](\d{2}|\d{4})(?:[ ,T].*)?$""")
    private val EXCEL_SERIAL = Regex("""^\d{5}(?:[.,]\d+)?$""")
    private val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)

    /**
     * Dates as spreadsheets export them: 2026-05-12, 12.05.2026, 12/05/2026, 12.05.26, with or without
     * a time part, or an Excel serial day number. Slash dates are day-first unless [order] says the
     * whole column is month-first (see [detectDateOrder]).
     */
    fun date(raw: String, order: DateOrder = DateOrder.DAY_FIRST): LocalDate? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        ISO_DATE.matchEntire(text)?.let { m ->
            return safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        NUMERIC_DATE.matchEntire(text)?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            val year = m.groupValues[3].toInt().let { if (m.groupValues[3].length == 2) 2000 + it else it }
            return if (order == DateOrder.MONTH_FIRST) safeDate(year, a, b) else safeDate(year, b, a)
        }
        if (EXCEL_SERIAL.matches(text)) {
            val days = text.replace(',', '.').toDouble().toLong()
            if (days in 20_000..80_000) return EXCEL_EPOCH.plusDays(days)
        }
        return null
    }

    /** Month-first only when some value cannot be day-first (e.g. 05/25/2026) and none contradicts it. */
    fun detectDateOrder(values: List<String>): DateOrder {
        var monthFirstEvidence = false
        for (value in values) {
            val m = NUMERIC_DATE.matchEntire(value.trim()) ?: continue
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            if (a > 12 && b <= 12) return DateOrder.DAY_FIRST
            if (b > 12 && a <= 12) monthFirstEvidence = true
        }
        return if (monthFirstEvidence) DateOrder.MONTH_FIRST else DateOrder.DAY_FIRST
    }

    private val TIME = Regex("""^(?:\d{4}-\d{2}-\d{2}[ T]|\d{1,2}[./]\d{1,2}[./]\d{2,4}\s+)?(\d{1,2})[:.](\d{2})(?::(\d{2}))?$""")

    /** "18:05", "7:30", "18:05:12", or the time part of "2026-05-12 18:05". */
    fun time(raw: String): LocalTime? {
        val m = TIME.matchEntire(raw.trim()) ?: return null
        val hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].toInt()
        val second = m.groupValues[3].toIntOrNull() ?: 0
        return if (hour in 0..23 && minute in 0..59 && second in 0..59) LocalTime.of(hour, minute, second) else null
    }

    private val HOURS_MINUTES = Regex("""^(\d{1,2}):(\d{2})(?::(\d{2}))?$""")
    private val WORDS_DURATION = Regex("""^(?:(\d+)\s*(?:год|г|h|hr|hours?)\.?)?\s*(?:(\d+)\s*(?:хв|м|m|min|mins|minutes?)\.?)?$""", RegexOption.IGNORE_CASE)

    /** Workout duration in minutes: "65", "65 хв", "1:05", "1:05:00", "1 год 5 хв". */
    fun durationMinutes(raw: String): Long? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        number(text)?.let { return if (it.value >= 0) Math.round(it.value) else null }
        HOURS_MINUTES.matchEntire(text)?.let { m ->
            val hours = m.groupValues[1].toLong()
            val minutes = m.groupValues[2].toLong()
            val seconds = m.groupValues[3].toLongOrNull() ?: 0
            return if (minutes < 60 && seconds < 60) hours * 60 + minutes + if (seconds >= 30) 1 else 0 else null
        }
        WORDS_DURATION.matchEntire(text)?.let { m ->
            if (m.groupValues[1].isEmpty() && m.groupValues[2].isEmpty()) return null
            return (m.groupValues[1].toLongOrNull() ?: 0) * 60 + (m.groupValues[2].toLongOrNull() ?: 0)
        }
        return null
    }

    private val PLAN = Regex("""^(\d+)(?:\s*[-–]\s*(\d+))?\s*[x×хX*]\s*(\d+)(?:\s*[-–]\s*(\d+))?.*$""")

    /** "4 × 8-12", "2–3×10–15", "3x8" → sets and reps ranges; the rest of the text is ignored. */
    fun plan(raw: String): IntArray? {
        val m = PLAN.matchEntire(raw.trim()) ?: return null
        val setsMin = m.groupValues[1].toInt()
        val setsMax = m.groupValues[2].toIntOrNull() ?: setsMin
        val repsMin = m.groupValues[3].toInt()
        val repsMax = m.groupValues[4].toIntOrNull() ?: repsMin
        if (setsMin !in 1..setsMax || repsMin !in 1..repsMax) return null
        return intArrayOf(setsMin, setsMax, repsMin, repsMax)
    }

    val ISO_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** "62,5" / "62.5" without trailing zeros; the decimal mark matches the file's separator. */
    fun formatNumber(value: Double, decimalComma: Boolean): String {
        val rounded = Math.round(value * 1000) / 1000.0
        val text = if (rounded == Math.floor(rounded) && Math.abs(rounded) < 1e15) {
            rounded.toLong().toString()
        } else {
            java.math.BigDecimal(rounded.toString()).stripTrailingZeros().toPlainString()
        }
        return if (decimalComma) text.replace('.', ',') else text
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        if (year in 1900..2200 && month in 1..12 && day in 1..31) runCatching { LocalDate.of(year, month, day) }.getOrNull() else null
}
