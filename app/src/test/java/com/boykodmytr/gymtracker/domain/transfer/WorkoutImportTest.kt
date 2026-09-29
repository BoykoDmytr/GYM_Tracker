package com.boykodmytr.gymtracker.domain.transfer

import com.boykodmytr.gymtracker.domain.model.ExerciseStatus
import com.boykodmytr.gymtracker.domain.model.SessionExercise
import com.boykodmytr.gymtracker.domain.model.SessionStatus
import com.boykodmytr.gymtracker.domain.model.SetLog
import com.boykodmytr.gymtracker.domain.model.SetTarget
import com.boykodmytr.gymtracker.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WorkoutImportTest {
    private val zone = ZoneId.of("Europe/Kyiv")

    private fun read(text: String, defaultName: String = "Імпорт"): ParsedWorkoutFile {
        val rows = Csv.parse(text)
        val mapping = ColumnMapping.detect(rows) ?: error("no header row")
        return WorkoutFileParser(rows, mapping, defaultName).parse()
    }

    private fun kinds(file: ParsedWorkoutFile) = file.issues.map { it.kind }

    // --- Export and re-import ---------------------------------------------------------------

    private fun appSession(id: String, date: LocalDate, notes: String = ""): WorkoutSession {
        val start = date.atTime(18, 5).atZone(zone).toInstant()
        fun sets(seId: String, vararg values: Pair<Double, Int>) = values.mapIndexed { i, (w, r) ->
            SetLog("$seId-$i", seId, i + 1, w, r, if (i == 0) 8.5 else null, i == values.lastIndex, if (i == 0) "легко, без болю; «ok»" else null, start)
        }
        return WorkoutSession(
            id = id, programId = "p", templateId = "t", name = "Full Body A", date = date, startedAt = start,
            endedAt = start.plusSeconds(65 * 60), status = SessionStatus.COMPLETED, notes = notes, currentExerciseId = null, rest = null,
            exercises = listOf(
                SessionExercise("$id-1", id, "bench", "Жим штанги лежачи", 0, SetTarget(3, 3, 6, 8), null, ExerciseStatus.COMPLETED, "", sets("$id-1", 60.0 to 8, 62.5 to 7, 62.5 to 6)),
                SessionExercise("$id-2", id, "abs", "Прес", 1, SetTarget(2, 3, 10, 15), null, ExerciseStatus.COMPLETED, "", sets("$id-2", 0.0 to 15, 0.0 to 12)),
                SessionExercise("$id-3", id, "row", "Тяга штанги в нахилі", 2, SetTarget(3, 3, 8, 10), null, ExerciseStatus.SKIPPED, "", emptyList()),
            ),
        )
    }

    @Test
    fun `export is Excel friendly`() {
        val csv = CsvExport.workouts(listOf(appSession("s1", LocalDate.of(2026, 9, 21), "=важко")), zone, CsvDialect.SEMICOLON)
        assertTrue(csv.startsWith("\uFEFFДата;Час початку;Тривалість (хв);Тренування;"))
        val lines = csv.removeSuffix("\r\n").split("\r\n")
        assertEquals(1 + 5, lines.size) // header + 3 bench + 2 abs; the skipped exercise has no sets
        assertEquals("2026-09-21;18:05;65;Full Body A;'=важко;Жим штанги лежачи;3×6-8;1;60;8;8,5;;\"легко, без болю; «ok»\";", lines[1])
        assertEquals("2026-09-21;18:05;65;Full Body A;'=важко;Жим штанги лежачи;3×6-8;2;62,5;7;;;;", lines[2])
        assertTrue(lines[3].contains(";62,5;6;;так;"))
        val comma = CsvExport.workouts(listOf(appSession("s1", LocalDate.of(2026, 9, 21))), zone, CsvDialect.COMMA)
        assertTrue(comma.contains(",62.5,7,"))
    }

    @Test
    fun `exported file reads back identically`() {
        val original = listOf(appSession("s1", LocalDate.of(2026, 9, 21), "Нотатка: \"ok\""), appSession("s2", LocalDate.of(2026, 9, 23)))
        for (dialect in CsvDialect.entries) {
            val file = read(CsvExport.workouts(original, zone, dialect))
            assertEquals(emptyList<ImportIssue>(), file.issues)
            assertEquals(2, file.sessions.size)
            val s = file.sessions[0]
            assertEquals(LocalDate.of(2026, 9, 21), s.date)
            assertEquals(LocalTime.of(18, 5), s.startTime)
            assertEquals(65L, s.durationMinutes)
            assertEquals("Full Body A", s.name)
            assertEquals("Нотатка: \"ok\"", s.notes)
            assertEquals(listOf("Жим штанги лежачи", "Прес"), s.exercises.map { it.name })
            val bench = s.exercises[0]
            assertEquals(listOf(3, 3, 6, 8), bench.plan?.toList())
            assertEquals(listOf(60.0, 62.5, 62.5), bench.sets.map { it.weightKg })
            assertEquals(listOf(8, 7, 6), bench.sets.map { it.reps })
            assertEquals(8.5, bench.sets[0].rpe!!, 0.0)
            assertEquals(listOf(false, false, true), bench.sets.map { it.isFailure })
            assertEquals("легко, без болю; «ok»", bench.sets[0].note)
            assertEquals(listOf(0.0, 0.0), s.exercises[1].sets.map { it.weightKg })
        }
    }

    @Test
    fun `reimporting an export finds only duplicates`() {
        val sessions = listOf(appSession("s1", LocalDate.of(2026, 9, 21)))
        val file = read(CsvExport.workouts(sessions, zone, CsvDialect.SEMICOLON))
        val existing = listOf(ExistingExercise("bench", "Жим штанги лежачи"), ExistingExercise("abs", "Прес"))
        val matches = ImportPlanner.suggestMatches(file, existing)
        assertTrue(matches.all { it.reason == MatchReason.SAME_NAME })
        val existingSessions = sessions.map { s ->
            ExistingSession(s.id, s.date, LocalTime.of(18, 5), s.name, s.exercises.flatMap { e -> e.sets.map { ExistingSet(e.exerciseId, it.weightKg, it.reps) } })
        }
        val plan = ImportPlanner.plan(file, matches, existing, existingSessions, emptyList())
        assertTrue(plan.sessions.isEmpty())
        assertEquals(1, plan.duplicateSessions)
        assertEquals(0, plan.conflictingSessions)
    }

    @Test
    fun `same workout with different data is a conflict and is not touched`() {
        val file = read(CsvExport.workouts(listOf(appSession("s1", LocalDate.of(2026, 9, 21))), zone, CsvDialect.SEMICOLON))
        val existing = listOf(ExistingExercise("bench", "Жим штанги лежачи"), ExistingExercise("abs", "Прес"))
        val other = ExistingSession("x", LocalDate.of(2026, 9, 21), null, "full body a", listOf(ExistingSet("bench", 100.0, 1)))
        val plan = ImportPlanner.plan(file, ImportPlanner.suggestMatches(file, existing), existing, listOf(other), emptyList())
        assertTrue(plan.sessions.isEmpty())
        assertEquals(1, plan.conflictingSessions)
        assertTrue(plan.issues.any { it.kind == IssueKind.SESSION_CONFLICT && it.date == LocalDate.of(2026, 9, 21) })
    }

    @Test
    fun `another workout on the same day is imported with a warning`() {
        val file = read(CsvExport.workouts(listOf(appSession("s1", LocalDate.of(2026, 9, 21))), zone, CsvDialect.SEMICOLON))
        val existing = listOf(ExistingExercise("bench", "Жим штанги лежачи"), ExistingExercise("abs", "Прес"))
        val other = ExistingSession("x", LocalDate.of(2026, 9, 21), null, "Push", listOf(ExistingSet("bench", 100.0, 1)))
        val plan = ImportPlanner.plan(file, ImportPlanner.suggestMatches(file, existing), existing, listOf(other), emptyList())
        assertEquals(1, plan.sessions.size)
        val warning = plan.issues.single { it.kind == IssueKind.SAME_DATE }
        assertEquals("Push", warning.value)
        assertEquals("Full Body A", warning.workout)
    }

    // --- Foreign files --------------------------------------------------------------------------

    @Test
    fun `journal block layout with per-set columns`() {
        val text = """
            ПОНЕДІЛОК — PUSH — груди, плечі, трицепс;;;;;;;;;;;
            ;;;;;;;;;;;
            ТИЖДЕНЬ 1;;;Дата:;12.05.2026;;Вага тіла:;90 кг;;Самопочуття:;;
            Вправа;Сети × повт.;RIR;С1 вага (кг);С1 повт.;С2 вага (кг);С2 повт.;С3 вага (кг);С3 повт.;С4 вага (кг);С4 повт.;Нотатки
            Жим штанги лежачи (обережно, без партнера);3 × 6-10;2-3;50;12;55;10;60;9;62,5;7;штанга зачепилась
            Махи гантелями в сторони;4 × 12-15;0-1;5(2);15;8,5(2);15;;;;;
            Підйом ніг у висі (прес/кор);3 × 15;0-1;;12;;15;;10;;;
            Розгинання ніг на HouseFit;4 × 10-15;1-2;*-1;15;;;;;;;* це максимальна вага
            Гирьовий свінг;3 × 15-20;1-2;;;;;;;;;
            ;;;;;;;;;;;
            ТИЖДЕНЬ 2;;;Дата:;18.05.2026;;Вага тіла:;89,5;;Самопочуття:;Відкат по всьому;
            Вправа;Сети × повт.;RIR;С1 вага (кг);С1 повт.;С2 вага (кг);С2 повт.;С3 вага (кг);С3 повт.;С4 вага (кг);С4 повт.;Нотатки
            Жим штанги лежачи (обережно, без партнера);3 × 6-10;2-3;50;12;55;12;;;;;
            ТИЖДЕНЬ 3;;;Дата:;;;Вага тіла:;;;Самопочуття:;;
            Вправа;Сети × повт.;RIR;С1 вага (кг);С1 повт.;С2 вага (кг);С2 повт.;С3 вага (кг);С3 повт.;С4 вага (кг);С4 повт.;Нотатки
            Жим штанги лежачи (обережно, без партнера);3 × 6-10;2-3;;;;;;;;;
        """.trimIndent()
        val file = read(text, defaultName = "Понеділок")
        assertEquals(2, file.sessions.size)
        val week1 = file.sessions[0]
        assertEquals(LocalDate.of(2026, 5, 12), week1.date)
        assertEquals("Понеділок", week1.name)
        assertEquals(90.0, week1.bodyWeightKg!!, 0.0)
        assertNull(week1.startTime)
        assertEquals(listOf("Жим штанги лежачи (обережно, без партнера)", "Махи гантелями в сторони", "Підйом ніг у висі (прес/кор)"), week1.exercises.map { it.name })
        val bench = week1.exercises[0]
        assertEquals(listOf(50.0, 55.0, 60.0, 62.5), bench.sets.map { it.weightKg })
        assertEquals(listOf(12, 10, 9, 7), bench.sets.map { it.reps })
        assertEquals("штанга зачепилась", bench.note)
        assertEquals(listOf(3, 3, 6, 10), bench.plan?.toList())
        val raises = week1.exercises[1]
        assertEquals(listOf(5.0, 8.5), raises.sets.map { it.weightKg })
        assertEquals("вага: 5(2)", raises.sets[0].note)
        assertEquals(listOf(0.0, 0.0, 0.0), week1.exercises[2].sets.map { it.weightKg })
        // "*-1" is not a number: reported, not guessed.
        val badWeight = file.issues.single { it.kind == IssueKind.BAD_WEIGHT }
        assertEquals("*-1", badWeight.value)
        assertEquals(8, badWeight.row)

        val week2 = file.sessions[1]
        assertEquals(LocalDate.of(2026, 5, 18), week2.date)
        assertEquals("Відкат по всьому", week2.notes)
        assertEquals(89.5, week2.bodyWeightKg!!, 0.0)
        assertFalse(kinds(file).contains(IssueKind.NO_DATE))
    }

    @Test
    fun `row problems are reported with line numbers and the rest is imported`() {
        val text = """
            Дата,Вправа,Підхід,Вага,Повторення
            2026-05-12,Жим штанги лежачи,1,60,8
            2026-05-12,Жим штанги лежачи,2,abc,8
            2026-05-12,Жим штанги лежачи,3,60,
            2026-05-12,Жим штанги лежачи,4,60,0
            ,Жим штанги лежачи,1,60,8
            31.02.2026,Жим штанги лежачи,1,60,8
            2026-05-12,,5,60,8
            2026-05-12,Жим штанги лежачи,1,62.5,6
            2026-05-12,Жим штанги лежачи,5,-5,8
        """.trimIndent()
        val file = read(text)
        assertEquals(1, file.sessions.size)
        assertEquals(listOf(60.0), file.sessions[0].exercises[0].sets.map { it.weightKg })
        assertEquals(
            listOf(
                IssueKind.BAD_WEIGHT to 3, IssueKind.MISSING_REPS to 4, IssueKind.BAD_REPS to 5, IssueKind.NO_DATE to 6,
                IssueKind.BAD_DATE to 7, IssueKind.NO_EXERCISE to 8, IssueKind.DUPLICATE_ROW to 9, IssueKind.BAD_WEIGHT to 10,
            ),
            file.issues.map { it.kind to it.row },
        )
    }

    @Test
    fun `english export from another app with pounds`() {
        val text = """
            Date,Workout Name,Duration,Exercise Name,Set Order,Weight (lbs),Reps,Notes,Workout Notes,RPE
            2026-05-12 07:30:00,Push Day,1h 5m,Bench Press,1,135,10,,good day,
            2026-05-12 07:30:00,Push Day,1h 5m,Bench Press,2,145,8,,good day,9
        """.trimIndent()
        val file = read(text)
        val s = file.sessions.single()
        assertEquals(LocalTime.of(7, 30), s.startTime)
        assertEquals(65L, s.durationMinutes)
        assertEquals("good day", s.notes)
        assertEquals(61.235, s.exercises[0].sets[0].weightKg, 0.001)
        val match = ImportPlanner.suggestMatches(file, listOf(ExistingExercise("bench", "Жим штанги лежачи"))).single()
        assertEquals(ExerciseTarget.Existing("bench", "Жим штанги лежачи"), match.target)
        assertEquals(MatchReason.ALIAS, match.reason)
    }

    @Test
    fun `aliases merge wording differences but never different exercises`() {
        val names = listOf(
            "Жим штанги лежачи (обережно, без партнера)", "Жим лежачи", "Жим гантелями лежачи (пласка лавка)",
            "Згинання ніг на HouseFit", "Розгинання ніг на HouseFit", "Жим штанги над головою", "Жим штанги стоячи над головою",
            "Тяга канату до низу на блоці (трицепс)", "Тяга вертикальної палки до низу на блоці (трицепс)", "Підйом EZ-штанги на брахіаліз",
        )
        val file = ParsedWorkoutFile(
            sessions = listOf(ParsedSession(LocalDate.of(2026, 5, 12), null, null, "Пн", "", null, names.map { ParsedExercise(it, null, null, listOf(ParsedSet(1, 10.0, 10, null, false, null, 2))) }, 2)),
            issues = emptyList(),
            ignoredRows = 0,
        )
        val existing = listOf(
            ExistingExercise("bench", "Жим штанги лежачи"), ExistingExercise("db", "Жим гантелей лежачи"),
            ExistingExercise("ez", "Згинання рук з EZ-штангою"), ExistingExercise("push", "Розгинання рук на блоці"),
        )
        val targets = ImportPlanner.suggestMatches(file, existing).associate { it.sourceName to it.target }
        assertEquals(ExerciseTarget.Existing("bench", "Жим штанги лежачи"), targets["Жим штанги лежачи (обережно, без партнера)"])
        assertEquals(ExerciseTarget.Existing("bench", "Жим штанги лежачи"), targets["Жим лежачи"])
        assertEquals(ExerciseTarget.Existing("db", "Жим гантелей лежачи"), targets["Жим гантелями лежачи (пласка лавка)"])
        assertEquals(ExerciseTarget.New("Згинання ніг на HouseFit"), targets["Згинання ніг на HouseFit"])
        assertEquals(ExerciseTarget.New("Розгинання ніг на HouseFit"), targets["Розгинання ніг на HouseFit"])
        assertEquals(ExerciseTarget.New("Жим штанги стоячи над головою"), targets["Жим штанги над головою"])
        assertEquals(ExerciseTarget.New("Жим штанги стоячи над головою"), targets["Жим штанги стоячи над головою"])
        // Probably the same as "Розгинання рук на блоці", but attachments differ: the user decides.
        assertEquals(ExerciseTarget.New("Тяга канату до низу на блоці (трицепс)"), targets["Тяга канату до низу на блоці (трицепс)"])
        assertEquals(ExerciseTarget.New("Тяга вертикальної палки до низу на блоці (трицепс)"), targets["Тяга вертикальної палки до низу на блоці (трицепс)"])
        assertEquals(ExerciseTarget.New("Підйом EZ-штанги на брахіаліз"), targets["Підйом EZ-штанги на брахіаліз"])

        val plan = ImportPlanner.plan(file, ImportPlanner.suggestMatches(file, existing), existing, emptyList(), emptyList())
        // Both overhead-press spellings become one new exercise.
        assertEquals(1, plan.newExercises.count { it == "Жим штанги стоячи над головою" })
        assertEquals(6, plan.newExercises.size)
    }

    @Test
    fun `body weight from the workout file is imported once per date`() {
        val text = """
            Дата;Тренування;Вправа;Вага;Повторення;Вага тіла
            12.05.2026;Push;Жим;50;10;90
            15.05.2026;Pull;Тяга;40;10;91
            15.05.2026;Pull;Тяга;45;8;91
        """.trimIndent()
        val file = read(text)
        val plan = ImportPlanner.plan(file, ImportPlanner.suggestMatches(file, emptyList()), emptyList(), emptyList(), listOf(ExistingMeasurement("weight", LocalDate.of(2026, 5, 12), 90.0)))
        assertEquals(listOf(LocalDate.of(2026, 5, 15) to 91.0), plan.bodyWeights)
        assertEquals(2, plan.sessions.size)
    }

    @Test
    fun `file without exercise or reps columns is rejected`() {
        assertNull(ColumnMapping.detect(Csv.parse("Дата;Вага;Нотатка\n2026-05-12;102;\n")))
        val mapping = ColumnMapping.fromHeader(0, listOf("Дата", "Вага"))
        assertEquals(listOf(WorkoutField.EXERCISE, WorkoutField.REPS), mapping.missingRequired())
    }

    @Test
    fun `body weight header does not take the weight column`() {
        val mapping = ColumnMapping.fromHeader(0, listOf("Вага тіла", "Вправа", "Вага, кг", "Повторення"))
        assertEquals(0, mapping.fields[WorkoutField.BODY_WEIGHT])
        assertEquals(2, mapping.fields[WorkoutField.WEIGHT])
    }
}
