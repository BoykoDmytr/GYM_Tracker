package com.boykodmytr.gymtracker.domain.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgramImportTest {

    private val csv = """
        Програма;Тренування;Вправа;Підходи;Повторення;Вага (кг);Відпочинок (с);Нотатка
        Верх / Низ;Верх-А;Жим лежачи;3;5-7;60;150;"Хват ширше плечей; лопатки зведені"
        Верх / Низ;Верх-А;Махи в сторони;3;8–12;7,5;1:30;
        Верх / Низ;Низ;Болгарські присіди;3;8-12;16;2 хв;на ногу
        Верх / Низ;Низ;Підйоми ніг у висі;2-3;8-12;;;
        Верх / Низ;Верх-А;Тріцепс на блоці;3;6-10;12;90;
        Верх / Низ;;Без тренування;3;10;;;
        Верх / Низ;Низ;Погані підходи;три;10;;;
        Верх / Низ;Низ;Поганий відпочинок;3;10;;довго;
    """.trimIndent()

    private fun parse(text: String = csv): ParsedProgramFile {
        val rows = Csv.parse(text)
        return ProgramFileParser.parse(rows, ProgramColumnMapping.detect(rows)!!, "Файл")
    }

    @Test
    fun `workouts keep file order and collect their exercises`() {
        val file = parse()
        val program = file.programs.single()
        assertEquals("Верх / Низ", program.name)
        assertEquals(listOf("Верх-А", "Низ"), program.workouts.map { it.name })
        assertEquals(listOf("Жим лежачи", "Махи в сторони", "Тріцепс на блоці"), program.workouts[0].exercises.map { it.name })
        val bench = program.workouts[0].exercises[0]
        assertEquals(listOf(3, 3, 5, 7), with(bench.target) { listOf(setsMin, setsMax, repsMin, repsMax) })
        assertEquals(60.0, bench.target.weightKg!!, 0.0)
        assertEquals(150, bench.restSeconds)
        assertEquals("Хват ширше плечей; лопатки зведені", bench.note)
        assertEquals(90, program.workouts[0].exercises[1].restSeconds)
        assertEquals(7.5, program.workouts[0].exercises[1].target.weightKg!!, 0.0)
        val low = program.workouts[1].exercises
        assertEquals(120, low[0].restSeconds)
        assertEquals(listOf(2, 3, 8, 12), with(low[1].target) { listOf(setsMin, setsMax, repsMin, repsMax) })
        assertNull(low[1].target.weightKg)
        assertNull(low[1].restSeconds)
    }

    @Test
    fun `bad rows are reported, a bad rest only drops the rest`() {
        val file = parse()
        assertEquals(
            listOf(IssueKind.NO_WORKOUT to 7, IssueKind.BAD_SETS to 8, IssueKind.BAD_REST to 9),
            file.issues.map { it.kind to it.row },
        )
        assertEquals("Поганий відпочинок", file.programs.single().workouts[1].exercises.last().name)
    }

    @Test
    fun `history files are not taken for programs`() {
        val export = Csv.parse(CsvExport.WORKOUT_HEADERS.joinToString(";"))
        assertNull(ProgramColumnMapping.detect(export))
        val journalHeader = Csv.parse("Вправа;Сети × повт.;RIR;С1 вага (кг);С1 повт.")
        assertNull(ProgramColumnMapping.detect(journalHeader))
    }

    @Test
    fun `program name falls back to the file name`() {
        val rows = Csv.parse("Тренування;Вправа;Підходи;Повторення\nA;Присід;3;5\n")
        val file = ProgramFileParser.parse(rows, ProgramColumnMapping.detect(rows)!!, "Моя програма")
        assertEquals("Моя програма", file.programs.single().name)
    }

    @Test
    fun `rest formats`() {
        assertEquals(90, ProgramFileParser.restSeconds("90"))
        assertEquals(90, ProgramFileParser.restSeconds("90 с"))
        assertEquals(90, ProgramFileParser.restSeconds("1:30"))
        assertEquals(150, ProgramFileParser.restSeconds("2,5 хв"))
        assertNull(ProgramFileParser.restSeconds("2-3 хв"))
    }
}
