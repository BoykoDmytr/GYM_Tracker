package com.boykodmytr.gymtracker.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boykodmytr.gymtracker.MainActivity
import com.boykodmytr.gymtracker.data.seed.DataSeeder
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/** Walks the core scenario: home → preview → sets with rest → next exercise → finish → summary. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WorkoutFlowUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var seeder: DataSeeder

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking { seeder.seedIfNeeded() }
    }

    @Test
    fun completeWorkoutFlow(): Unit = with(composeRule) {
        waitForText("Сьогодні тренування")
        onNodeWithText("Full Body A").assertExists()
        screenshot("01_home")

        onNodeWithText("Почати тренування").performClick()
        waitForText("Жим штанги лежачи")
        onNodeWithText("3×6–8", substring = true).assertExists()
        screenshot("02_preview")

        onNodeWithText("Почати").performClick()
        waitForText("Підхід 1 / 3")
        onNodeWithText("Жим штанги лежачи").assertExists()
        screenshot("03_active_set1")

        onNodeWithText("Завершити підхід").performClick()
        waitForText("Підхід 1 — результат")
        onAllNodes(hasSetTextAction())[0].performTextReplacement("60")
        onAllNodes(hasSetTextAction())[1].performTextReplacement("8")
        onNodeWithText("Зберегти").performClick()

        waitForText("Підхід 2 / 3")
        waitForText("Відпочинок")
        screenshot("05_rest")

        repeat(2) {
            onNodeWithText("Завершити підхід").performClick()
            waitForText("Зберегти")
            onNodeWithText("Зберегти").performClick()
            waitForNoText("Зберегти")
        }
        waitForText("Вправу завершено")
        onNodeWithText("Далі: Румунська тяга зі штангою").assertExists()
        screenshot("06_exercise_done")

        onNodeWithText("Наступна вправа").performClick()
        waitForText("Румунська тяга зі штангою")
        waitForText("Підхід 1 / 3")

        onNodeWithText("Завершити").performClick()
        waitForText("Завершити тренування?")
        onNode(inDialog("Завершити")).performClick()

        waitForText("Тренування завершено")
        onNodeWithText("60×8, 60×8, 60×8 кг").assertExists()
        screenshot("08_summary")

        onNodeWithText("Готово").performClick()
        waitForText("Сьогодні тренування виконано")
        screenshot("09_home_done")
    }
}
