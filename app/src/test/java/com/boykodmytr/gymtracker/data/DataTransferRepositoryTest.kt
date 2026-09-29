package com.boykodmytr.gymtracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boykodmytr.gymtracker.core.database.AppDatabase
import com.boykodmytr.gymtracker.data.seed.DataSeeder
import com.boykodmytr.gymtracker.domain.logic.ProgressMath
import com.boykodmytr.gymtracker.domain.logic.StatsCalculator
import com.boykodmytr.gymtracker.domain.model.BuiltInMeasurementTypes
import com.boykodmytr.gymtracker.domain.model.SetInput
import com.boykodmytr.gymtracker.domain.repository.BodyRepository
import com.boykodmytr.gymtracker.domain.repository.DataTransferRepository
import com.boykodmytr.gymtracker.domain.repository.ExerciseRepository
import com.boykodmytr.gymtracker.domain.repository.ProgramRepository
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import com.boykodmytr.gymtracker.domain.repository.WorkoutRepository
import com.boykodmytr.gymtracker.domain.transfer.BodyColumnMapping
import com.boykodmytr.gymtracker.domain.transfer.BodyImport
import com.boykodmytr.gymtracker.domain.transfer.ColumnMapping
import com.boykodmytr.gymtracker.domain.transfer.Csv
import com.boykodmytr.gymtracker.domain.transfer.CsvDialect
import com.boykodmytr.gymtracker.domain.transfer.ExerciseTarget
import com.boykodmytr.gymtracker.domain.transfer.ImportPlan
import com.boykodmytr.gymtracker.domain.transfer.ImportPlanner
import com.boykodmytr.gymtracker.domain.transfer.WorkoutFileParser
import com.boykodmytr.gymtracker.testing.TEST_START
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Old journal → app: imported workouts show up in history, statistics and exercise progress next to
 * workouts logged in the app; importing again adds nothing; undo removes exactly the import.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DataTransferRepositoryTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var seeder: DataSeeder
    @Inject lateinit var transfer: DataTransferRepository
    @Inject lateinit var workouts: WorkoutRepository
    @Inject lateinit var exercises: ExerciseRepository
    @Inject lateinit var programs: ProgramRepository
    @Inject lateinit var body: BodyRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var clock: Clock

    private val today = TEST_START.toLocalDate()

    /** Shaped like tools/journal_to_csv.py output: one row per set, names as written in the journal. */
    private val journal = """
        Дата;Час початку;Тривалість (хв);Тренування;Нотатки тренування;Вправа;План;Підхід;Вага (кг);Повторення;RPE;Відмова;Нотатка підходу;Нотатка вправи;Вага тіла (кг)
        2026-05-12;;;Push;;Жим штанги лежачи (обережно, без партнера);3×6-10;1;50;12;;;;;90
        2026-05-12;;;Push;;Жим штанги лежачи (обережно, без партнера);3×6-10;2;55;10;;;;;
        2026-05-12;;;Push;;Жим штанги лежачи (обережно, без партнера);3×6-10;3;60;9;;;;;
        2026-05-12;;;Push;;Тяга канату до низу на блоці (трицепс);3×10-12;1;16;12;;;;;
        2026-05-18;;;Push;Відкат;Жим штанги лежачи (обережно, без партнера);3×6-10;1;50;12;;;;;89,5
        2026-05-18;;;Push;Відкат;Жим штанги лежачи (обережно, без партнера);3×6-10;2;70;4;;;;;
        2026-05-20;;;Pull;;Молотки з гантелями;3×10-12;1;12;8;;;"2 гантелі по 12 кг; повторення «8(2)»";;90
        2026-05-20;;;Pull;;Підйом ніг у висі (прес/кор);3×15;1;0;12;;;;;
    """.trimIndent()

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            settings.ensureTrackingStartDate(today)
            seeder.seedIfNeeded()
        }
    }

    private suspend fun planFor(text: String): ImportPlan {
        val rows = Csv.parse(text)
        val mapping = ColumnMapping.detect(rows)!!
        val parsed = WorkoutFileParser(rows, mapping, "Імпорт").parse()
        val context = transfer.importContext()
        val matches = ImportPlanner.suggestMatches(parsed, context.exercises)
        return ImportPlanner.plan(parsed, matches, context.exercises, context.sessions, context.measurements.filter { it.typeId == BuiltInMeasurementTypes.WEIGHT })
    }

    private suspend fun exerciseId(name: String) = exercises.observeExercises().first().single { it.name == name }.id

    @Test
    fun importedJournalCountsInHistoryStatsAndProgress() = runBlocking {
        // One workout logged in the app today, to check old and new history mix correctly.
        val program = programs.observeActiveProgram().first()!!
        val sessionId = workouts.startSession(program.workouts.first().id)
        val benchEntry = workouts.observeSession(sessionId).first()!!.exercises.first { it.exerciseName == "Жим штанги лежачи" }
        workouts.logSet(benchEntry.id, SetInput(72.5, 5))
        workouts.finishSession(sessionId, clock.instant())

        val plan = planFor(journal)
        assertEquals(3, plan.sessions.size)
        assertEquals(listOf("Тяга канату до низу на блоці (трицепс)", "Підйом ніг у висі (прес/кор)"), plan.newExercises)
        val entry = transfer.applyWorkoutImport(plan, "journal.csv")
        assertEquals(3, entry.sessions)
        assertEquals(8, entry.sets)

        // History: all four workouts, imported ones on their own dates and without an invented time.
        val summaries = workouts.observeAllCompletedSummaries().first()
        assertEquals(4, summaries.size)
        val may12 = summaries.single { it.date == LocalDate.of(2026, 5, 12) }
        assertEquals("Push", may12.name)
        assertEquals(4, may12.setCount)
        assertNull(may12.duration)

        // Progress: the journal's bench press joined the app's "Жим штанги лежачи".
        val bench = exerciseId("Жим штанги лежачи")
        val sessions = ProgressMath.perSession(workouts.observeExerciseRecords(bench).first())
        assertEquals(listOf(LocalDate.of(2026, 5, 12), LocalDate.of(2026, 5, 18), today), sessions.map { it.date })
        assertEquals(listOf(60.0, 70.0, 72.5), sessions.map { it.bestWeightKg })
        assertEquals(72.5, ProgressMath.personalRecords(sessions).maxWeight!!.value, 0.0)
        val hammer = exerciseId("Молоткові згинання")
        assertEquals(1, workouts.observeExerciseRecords(hammer).first().size)

        // History rows use the standard name and remember the journal's spelling.
        val detail = workouts.observeSession(may12.id).first()!!
        val benchRow = detail.exercises.first()
        assertEquals("Жим штанги лежачи", benchRow.exerciseName)
        assertTrue(benchRow.notes.contains("Жим штанги лежачи (обережно, без партнера)"))
        assertEquals(listOf(3, 3, 6, 10), listOf(benchRow.target.setsMin, benchRow.target.setsMax, benchRow.target.repsMin, benchRow.target.repsMax))

        // Statistics count old workouts, but no missed days before the app was installed.
        val stats = StatsCalculator.compute(summaries, today, settings.settings.first().trainingDays, settings.settings.first().trackingStartDate)
        assertEquals(4, stats.total)
        assertEquals(0, stats.missedTotal)
        assertTrue(stats.totalVolumeKg > 50 * 12 + 55 * 10 + 60 * 9)

        // Body weight from the journal, one per date.
        val weights = body.observeMeasurements(BuiltInMeasurementTypes.WEIGHT).first()
        assertEquals(listOf(90.0, 89.5, 90.0), weights.sortedBy { it.date }.map { it.value })

        // The same file again adds nothing.
        val again = planFor(journal)
        assertTrue(again.sessions.isEmpty())
        assertEquals(3, again.duplicateSessions)
        assertTrue(again.bodyWeights.isEmpty())

        // Export contains old and new workouts and reads back as duplicates only.
        val exported = transfer.exportWorkouts(CsvDialect.SEMICOLON).text
        assertTrue(exported.startsWith("\uFEFF"))
        assertTrue(exported.contains("2026-05-12;;;Push;;Жим штанги лежачи;3×6-10;3;60;9;"))
        val roundTrip = planFor(exported)
        assertTrue(roundTrip.sessions.isEmpty())
        assertEquals(4, roundTrip.duplicateSessions)

        // Undo removes exactly the import: the app's own workout and seeded exercises stay.
        val undo = transfer.undo(entry.id)
        assertEquals(3, undo.sessionsRemoved)
        assertEquals(3, undo.measurementsRemoved)
        assertEquals(listOf(today), workouts.observeAllCompletedSummaries().first().map { it.date })
        val names = exercises.observeExercises().first().map { it.name }
        assertFalse(names.contains("Тяга канату до низу на блоці (трицепс)"))
        assertTrue(names.contains("Молоткові згинання"))
        assertTrue(body.observeMeasurements(BuiltInMeasurementTypes.WEIGHT).first().isEmpty())
        assertTrue(transfer.observeHistory().first().single { it.id == entry.id }.undone)
    }

    @Test
    fun userChoiceOverridesSuggestion() = runBlocking {
        val rows = Csv.parse(journal)
        val parsed = WorkoutFileParser(rows, ColumnMapping.detect(rows)!!, "Імпорт").parse()
        val context = transfer.importContext()
        val pushdown = context.exercises.single { it.name == "Розгинання рук на блоці" }
        val matches = ImportPlanner.suggestMatches(parsed, context.exercises).map {
            if (it.sourceName == "Тяга канату до низу на блоці (трицепс)") it.copy(target = ExerciseTarget.Existing(pushdown.id, pushdown.name)) else it
        }
        val plan = ImportPlanner.plan(parsed, matches, context.exercises, context.sessions, emptyList())
        transfer.applyWorkoutImport(plan, "journal.csv")
        assertEquals(1, workouts.observeExerciseRecords(pushdown.id).first().size)
        assertFalse(exercises.observeExercises().first().any { it.name == "Тяга канату до низу на блоці (трицепс)" })
    }

    @Test
    fun bodyMeasurementsRoundTrip() = runBlocking {
        body.addMeasurement(BuiltInMeasurementTypes.WEIGHT, 88.5, LocalDate.of(2026, 9, 1), "ранок; натще")
        body.addMeasurement("waist", 98.0, LocalDate.of(2026, 9, 1))
        val exported = transfer.exportMeasurements(CsvDialect.COMMA).text
        assertTrue(exported.contains("2026-09-01,Вага,88.5,кг,ранок; натще"))

        val rows = Csv.parse(exported + "2026-09-08,Обхват шиї,41,см,\r\n2026-09-08,Біцепс (напружений),38.5,см,\r\n")
        val context = transfer.importContext()
        val plan = BodyImport.plan(rows, BodyColumnMapping.detect(rows)!!, context.measurementTypes, context.measurements)
        assertEquals(2, plan.duplicates)
        assertEquals(2, plan.measurements.size)
        assertEquals(listOf("Біцепс (напружений)"), plan.newTypes.map { it.name })
        val entry = transfer.applyBodyImport(plan, "body.csv")
        val types = body.observeMeasurementTypes().first()
        val custom = types.single { it.name == "Біцепс (напружений)" }
        assertEquals(38.5, body.observeMeasurements(custom.id).first().single().value, 0.0)

        transfer.undo(entry.id)
        assertFalse(body.observeMeasurementTypes().first().any { it.name == "Біцепс (напружений)" })
        assertTrue(body.observeMeasurements("neck").first().isEmpty())
        assertEquals(1, body.observeMeasurements("waist").first().size)
    }

    @Test
    fun mergeMovesHistoryProgramAndPhotosAndUndoRestoresThem() = runBlocking {
        val source = exerciseId("Розгинання рук на верхньому блоці")
        val target = exerciseId("Розгинання рук на блоці")
        val program = programs.observeActiveProgram().first()!!
        val workoutB = program.workouts.single { w -> w.exercises.any { it.exercise.id == source } }
        val sessionId = workouts.startSession(workoutB.id)
        val row = workouts.observeSession(sessionId).first()!!.exercises.single { it.exerciseId == source }
        workouts.logSet(row.id, SetInput(25.0, 12))
        workouts.finishSession(sessionId, clock.instant())

        val preview = transfer.previewMerge(source, target)
        assertEquals(1, preview.sessions)
        assertEquals(1, preview.templates)
        assertEquals(0, preview.sessionsWithBoth)

        val entry = transfer.mergeExercises(source, target)
        assertEquals(1, entry.sessions)
        assertFalse(exercises.observeExercises().first().any { it.id == source })
        assertEquals(1, workouts.observeExerciseRecords(target).first().size)
        assertEquals("Розгинання рук на блоці", workouts.observeSession(sessionId).first()!!.exercises.single { it.id == row.id }.exerciseName)
        val templateAfter = programs.observeTemplate(workoutB.id).first()!!
        assertTrue(templateAfter.exercises.any { it.exercise.id == target })

        transfer.undo(entry.id)
        assertTrue(exercises.observeExercises().first().any { it.id == source && it.name == "Розгинання рук на верхньому блоці" })
        assertEquals(1, workouts.observeExerciseRecords(source).first().size)
        assertTrue(workouts.observeExerciseRecords(target).first().isEmpty())
        assertEquals("Розгинання рук на верхньому блоці", workouts.observeSession(sessionId).first()!!.exercises.single { it.id == row.id }.exerciseName)
        assertTrue(programs.observeTemplate(workoutB.id).first()!!.exercises.any { it.exercise.id == source })
    }
}
