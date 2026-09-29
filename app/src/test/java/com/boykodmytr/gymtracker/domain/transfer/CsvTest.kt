package com.boykodmytr.gymtracker.domain.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class CsvTest {

    @Test
    fun `quotes, doubled quotes, separators and line breaks inside fields`() {
        val text = "a;b;c\r\n\"x;y\";\"say \"\"hi\"\"\";\"line1\nline2\"\r\n"
        assertEquals(listOf(listOf("a", "b", "c"), listOf("x;y", "say \"hi\"", "line1\nline2")), Csv.parse(text, ';'))
    }

    @Test
    fun `bom is dropped and a missing last line break is fine`() {
        assertEquals(listOf(listOf("Дата", "Вправа"), listOf("2026-05-12", "Жим")), Csv.parse("\uFEFFДата,Вправа\n2026-05-12,Жим"))
    }

    @Test
    fun `empty fields are kept`() {
        assertEquals(listOf(listOf("", "a", "", "")), Csv.parse(";a;;\n", ';'))
    }

    @Test
    fun `delimiter detection ignores separators inside quotes`() {
        assertEquals(';', Csv.detectDelimiter("Дата;Вправа;Нотатка\n2026-05-12;Жим;\"добре, легко, швидко\"\n"))
        assertEquals(',', Csv.detectDelimiter("date,exercise,weight\n2026-05-12,Bench,62.5\n"))
        assertEquals('\t', Csv.detectDelimiter("date\texercise\tweight\n2026-05-12\tBench\t62,5\n"))
    }

    @Test
    fun `writer quotes only when needed and round trips`() {
        val fields = listOf("Жим", "так; ні", "він сказав \"ще\"", "два\nрядки", " пробіл", "62,5")
        val line = Csv.formatRow(fields, ';')
        assertEquals("Жим;\"так; ні\";\"він сказав \"\"ще\"\"\";\"два\nрядки\";\" пробіл\";62,5", line)
        assertEquals(listOf(fields), Csv.parse(line, ';'))
    }

    @Test
    fun `formula-like text is guarded and restored`() {
        for (text in listOf("=SUM(A1)", "+380", "@cmd", "-cmd|x", "'=a")) {
            val guarded = Csv.guardText(text)
            assertEquals("'$text", guarded)
            assertEquals(text, Csv.unguardText(guarded))
        }
        for (text in listOf("-2 кг", "- легко", "звичайний текст", "'цитата", "")) {
            assertEquals(text, Csv.guardText(text))
            assertEquals(text, Csv.unguardText(text))
        }
    }

    @Test
    fun `numbers with comma, units and multiplier notes`() {
        assertEquals(CellNumber(62.5), Values.number("62,5"))
        assertEquals(CellNumber(62.5), Values.number(" 62.5 "))
        assertEquals(CellNumber(102.0), Values.number("102 кг"))
        assertEquals(CellNumber(1250.5), Values.number("1 250,5"))
        assertEquals(CellNumber(16.0, "16(2)"), Values.number("16(2)"))
        assertEquals(CellNumber(8.5, "8,5(2)"), Values.number("8,5(2)"))
        assertEquals(CellNumber(20.0, "20 × 2"), Values.number("20 × 2"))
        assertNull(Values.number("*"))
        assertNull(Values.number("*-1"))
        assertNull(Values.number("16 + 101"))
        assertNull(Values.number("16(2)гирі"))
        assertNull(Values.number("abc"))
        assertNull(Values.number(""))
    }

    @Test
    fun `dates in common spreadsheet styles`() {
        val may12 = LocalDate.of(2026, 5, 12)
        assertEquals(may12, Values.date("2026-05-12"))
        assertEquals(may12, Values.date("12.05.2026"))
        assertEquals(may12, Values.date("12/05/2026"))
        assertEquals(may12, Values.date("12.05.26"))
        assertEquals(may12, Values.date("2026-05-12 18:05"))
        assertEquals(may12, Values.date("2026-05-12T18:05:00"))
        assertEquals(may12, Values.date("46154")) // Excel serial day
        assertEquals(may12, Values.date("05/12/2026", Values.DateOrder.MONTH_FIRST))
        assertNull(Values.date("31.02.2026"))
        assertNull(Values.date("вчора"))
    }

    @Test
    fun `slash date order is decided per column`() {
        assertEquals(Values.DateOrder.MONTH_FIRST, Values.detectDateOrder(listOf("05/12/2026", "05/25/2026")))
        assertEquals(Values.DateOrder.DAY_FIRST, Values.detectDateOrder(listOf("12/05/2026", "25/05/2026")))
        assertEquals(Values.DateOrder.DAY_FIRST, Values.detectDateOrder(listOf("01/02/2026")))
    }

    @Test
    fun `times and durations`() {
        assertEquals(LocalTime.of(18, 5), Values.time("18:05"))
        assertEquals(LocalTime.of(7, 30), Values.time("7:30"))
        assertEquals(LocalTime.of(18, 5, 12), Values.time("2026-05-12 18:05:12"))
        assertNull(Values.time("25:00"))
        assertEquals(65L, Values.durationMinutes("65"))
        assertEquals(65L, Values.durationMinutes("65 хв"))
        assertEquals(65L, Values.durationMinutes("1:05"))
        assertEquals(65L, Values.durationMinutes("1:05:00"))
        assertEquals(65L, Values.durationMinutes("1 год 5 хв"))
        assertNull(Values.durationMinutes("довго"))
    }

    @Test
    fun `plans like 4 x 8-12`() {
        assertEquals(listOf(4, 4, 8, 12), Values.plan("4 × 8-12")?.toList())
        assertEquals(listOf(2, 3, 10, 15), Values.plan("2–3×10–15")?.toList())
        assertEquals(listOf(3, 3, 10, 12), Values.plan("3 × 10-12 (на сторону)")?.toList())
        assertNull(Values.plan("багато"))
    }

    @Test
    fun `number formatting follows the dialect`() {
        assertEquals("62,5", Values.formatNumber(62.5, decimalComma = true))
        assertEquals("62.5", Values.formatNumber(62.5, decimalComma = false))
        assertEquals("60", Values.formatNumber(60.0, decimalComma = true))
        assertEquals("0,454", Values.formatNumber(0.45359237, decimalComma = true))
    }

    @Test
    fun `header keys fold latin look-alikes and punctuation`() {
        assertEquals(HeaderText.key("С1 вага (кг)"), HeaderText.key("C1 вага, кг"))
        assertEquals("тривалість хв", HeaderText.key("Тривалість (хв)"))
        assertEquals(HeaderText.key("П'ятниця"), HeaderText.key("Пʼятниця"))
    }
}
