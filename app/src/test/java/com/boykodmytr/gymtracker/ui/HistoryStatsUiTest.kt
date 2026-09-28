package com.boykodmytr.gymtracker.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boykodmytr.gymtracker.MainActivity
import com.boykodmytr.gymtracker.core.database.AppDatabase
import com.boykodmytr.gymtracker.data.seed.DataSeeder
import com.boykodmytr.gymtracker.domain.model.BuiltInMeasurementTypes
import com.boykodmytr.gymtracker.domain.repository.BodyRepository
import com.boykodmytr.gymtracker.domain.repository.ProgramRepository
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import com.boykodmytr.gymtracker.testing.TEST_START
import com.boykodmytr.gymtracker.testing.seedHistory
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HistoryStatsUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var seeder: DataSeeder
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var programs: ProgramRepository
    @Inject lateinit var body: BodyRepository
    @Inject lateinit var settings: SettingsRepository

    private val today = TEST_START.toLocalDate()

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            settings.ensureTrackingStartDate(today.minusWeeks(6).minusDays(3))
            seeder.seedIfNeeded()
            val program = programs.observeActiveProgram().first()!!
            // 16 Sep (Wed) and 21 Sep (Mon) were skipped.
            seedHistory(db, program, today, missed = setOf(today.minusDays(12), today.minusDays(7)))
            listOf(103.0, 102.4, 101.8, 101.5, 100.9, 100.4).forEachIndexed { i, kg ->
                body.addMeasurement(BuiltInMeasurementTypes.WEIGHT, kg, today.minusWeeks(6L - i))
            }
        }
    }

    @Test
    fun historyCalendarAndSessionDetail(): Unit = with(composeRule) {
        waitForText("Сьогодні тренування")
        screenshot("10_home_with_history")

        onNodeWithText("Історія").performClick()
        waitForText("Вересень 2026")
        waitForText("пропущено: 2", substring = true)
        screenshot("11_history_calendar")

        onNodeWithContentDescription("пʼятниця, 25 вересня, Виконано").performClick()
        waitForText("Пʼятниця, 25 вересня")
        onAllNodesWithText("Full Body", substring = true).onFirst().assertExists()
        onNode(verticalScroller).performScrollToNode(hasText("18:05", substring = true))
        onAllNodesWithText("18:05", substring = true).onFirst().performClick()

        waitForText("Жим штанги лежачи")
        screenshot("12_session_detail")
    }

    @Test
    fun statisticsAndExerciseProgress(): Unit = with(composeRule) {
        waitForText("Сьогодні тренування")
        onNodeWithText("Статистика").performClick()
        waitForText("Тренування по тижнях")
        screenshot("13_stats_top")

        onNode(verticalScroller).performScrollToNode(hasText("Жим штанги лежачи"))
        screenshot("14_stats_exercises")
        onNodeWithText("Жим штанги лежачи").performClick()

        waitForText("Робоча вага")
        screenshot("15_exercise_progress")
        onNodeWithText("1ПМ (оцінка)").performClick()
        waitForText("формулою Еплі", substring = true)
    }
}
