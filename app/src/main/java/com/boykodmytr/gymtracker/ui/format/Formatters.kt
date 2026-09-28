package com.boykodmytr.gymtracker.ui.format

import com.boykodmytr.gymtracker.domain.model.SetTarget
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The UI is Ukrainian-only for now, so dates and numbers use the Ukrainian locale explicitly instead of
 * the device locale (an English phone would otherwise mix "Monday" into Ukrainian screens).
 * When more languages are added, switch this to the resolved app locale.
 */
val AppLocale: Locale = Locale.forLanguageTag("uk")

private fun decimalFormat(pattern: String) = DecimalFormat(pattern, DecimalFormatSymbols.getInstance(AppLocale))

object Fmt {
    private val number = decimalFormat("#,##0.##")
    private val oneDecimal = decimalFormat("#,##0.#")
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", AppLocale)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMMM yyyy", AppLocale)
    private val shortDate = DateTimeFormatter.ofPattern("dd.MM.yyyy", AppLocale)
    private val shortDayMonth = DateTimeFormatter.ofPattern("dd.MM", AppLocale)
    private val monthYear = DateTimeFormatter.ofPattern("LLLL yyyy", AppLocale)
    private val time = DateTimeFormatter.ofPattern("HH:mm", AppLocale)

    private val plain = decimalFormat("0.##")

    fun number(value: Double): String = number.format(value)

    /** No grouping separators: for editable text fields. */
    fun plain(value: Double): String = plain.format(value)
    fun oneDecimal(value: Double): String = oneDecimal.format(value)

    /** Weight value in the chosen unit, without the unit label: "72,5". */
    fun weight(kg: Double, unit: WeightUnit): String = number(unit.fromKg(kg))

    fun weightUnitLabel(unit: WeightUnit): String = when (unit) {
        WeightUnit.KG -> "кг"
        WeightUnit.LB -> "lb"
    }

    fun weightWithUnit(kg: Double, unit: WeightUnit): String = "${weight(kg, unit)} ${weightUnitLabel(unit)}"

    /** Large volumes read better rounded: "12 450 кг", "1,2 т". */
    fun volume(kg: Double, unit: WeightUnit): String {
        val value = unit.fromKg(kg)
        return if (unit == WeightUnit.KG && value >= 10_000) "${oneDecimal(value / 1000)} т" else "${decimalFormat("#,##0").format(value)} ${weightUnitLabel(unit)}"
    }

    fun range(min: Int, max: Int): String = if (min == max) "$min" else "$min–$max"

    /** "3×6–8", "2–3×10–15". */
    fun target(target: SetTarget): String = "${range(target.setsMin, target.setsMax)}×${range(target.repsMin, target.repsMax)}"

    /** Clock-style duration: "45:07" or "1:05:23". */
    fun clock(duration: Duration): String {
        val total = duration.seconds.coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun clockSeconds(seconds: Long): String = clock(Duration.ofSeconds(seconds))

    /** Human duration: "1 год 5 хв", "45 хв". */
    fun duration(duration: Duration): String {
        val minutes = duration.toMinutes().coerceAtLeast(0)
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "$h год $m хв"
            h > 0 -> "$h год"
            else -> "$m хв"
        }
    }

    /** Compact duration for tiles: "62 хв". */
    fun minutes(duration: Duration): String = "${duration.toMinutes().coerceAtLeast(0)} хв"

    fun restLabel(seconds: Int): String = if (seconds % 60 == 0) "${seconds / 60} хв" else if (seconds < 60) "$seconds с" else clockSeconds(seconds.toLong())

    fun dayMonth(date: LocalDate): String = dayMonth.format(date)
    fun dayMonthYear(date: LocalDate): String = dayMonthYear.format(date)
    fun shortDate(date: LocalDate): String = shortDate.format(date)
    fun shortDayMonth(date: LocalDate): String = shortDayMonth.format(date)
    fun monthYear(date: LocalDate): String = monthYear.format(date).replaceFirstChar { it.titlecase(AppLocale) }
    fun time(value: LocalTime): String = time.format(value)

    fun dayOfWeek(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, AppLocale)
    fun dayOfWeekShort(day: java.time.DayOfWeek): String =
        day.getDisplayName(TextStyle.SHORT, AppLocale).replaceFirstChar { it.titlecase(AppLocale) }
    fun dayOfWeekFull(day: java.time.DayOfWeek): String =
        day.getDisplayName(TextStyle.FULL, AppLocale).replaceFirstChar { it.titlecase(AppLocale) }

    /** "понеділок, 28 вересня" */
    fun fullDate(date: LocalDate): String = "${dayOfWeek(date)}, ${dayMonth(date)}"

    fun rpe(value: Double): String = oneDecimal(value)
}

/** Accepts both "72,5" and "72.5"; returns null for anything that is not a non-negative number. */
fun parseDecimal(text: String): Double? =
    text.filterNot { it.isWhitespace() || it == ' ' || it == ' ' }.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 && it.isFinite() }
