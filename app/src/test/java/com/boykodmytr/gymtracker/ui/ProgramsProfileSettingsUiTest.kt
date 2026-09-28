package com.boykodmytr.gymtracker.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProgramsProfileSettingsUiTest {

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
    fun editProgramTemplateWithExerciseFromLibrary(): Unit = with(composeRule) {
        waitForText("Сьогодні тренування")
        onNodeWithText("Програми").performClick()
        waitForText("Full Body — 3 рази на тиждень")
        screenshot("20_programs")

        onNodeWithText("Full Body — 3 рази на тиждень").performClick()
        waitForText("Full Body A")
        screenshot("21_program_detail")

        onNodeWithText("Full Body A").performClick()
        waitForText("1. Жим штанги лежачи")
        screenshot("22_template_editor")
        onNode(verticalScroller).performScrollToNode(hasText("7. Прес"))
        onNodeWithText("2–3×10–15", substring = true).assertExists()

        onNode(verticalScroller).performScrollToNode(hasText("Додати вправу"))
        onNodeWithText("Додати вправу").performClick()
        waitForText("Вибери вправу")
        onNodeWithText("Молоткові згинання").performClick()

        waitForText("Підходи")
        val fields = onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement("3")
        fields[1].performTextReplacement("3")
        fields[2].performTextReplacement("10")
        fields[3].performTextReplacement("12")
        onNodeWithText("Зберегти").performScrollTo().performClick()

        onNode(verticalScroller).performScrollToNode(hasText("8. Молоткові згинання"))
        onNodeWithText("3×10–12", substring = true).assertExists()
    }

    @Test
    fun createExerciseInLibrary(): Unit = with(composeRule) {
        waitForText("Сьогодні тренування")
        onNodeWithText("Програми").performClick()
        waitForText("Вправи")
        onNodeWithText("Вправи").performClick()
        waitForText("Бібліотека вправ")
        screenshot("23_library")

        onNode(hasText("Нова вправа"), useUnmergedTree = true).performClick()
        waitForText("Спершу збережи вправу", substring = true)
        onAllNodes(hasSetTextAction())[0].performTextReplacement("Face pull")
        onAllNodes(hasSetTextAction())[1].performTextReplacement("Лікті вище кистей")
        onNodeWithText("Зберегти").performClick()
        waitForText("Додати фото")
        screenshot("24_exercise_editor")
    }

    @Test
    fun recordBodyMeasurements(): Unit = with(composeRule) {
        waitForText("Сьогодні тренування")
        onNodeWithText("Профіль").performClick()
        waitForText("Параметри тіла")
        onNodeWithText("Записати вимірювання").performClick()
        waitForText("Вага, кг")
        onAllNodes(hasSetTextAction())[0].performTextReplacement("101,8")
        onAllNodes(hasSetTextAction())[2].performTextReplacement("94")
        onNodeWithText("Зберегти").performScrollTo().performClick()

        waitForText("101,8 кг")
        waitForText("94 см")
        screenshot("25_profile")

        onNodeWithText("Вага").performClick()
        waitForText("Історія")
        screenshot("26_measurement_detail")
    }

    @Test
    fun settingsAndDarkTheme(): Unit = with(composeRule) {
        waitForText("Сьогодні тренування")
        onNodeWithContentDescription("Налаштування").performClick()
        waitForText("Дні та час тренувань")
        onAllNodesWithText("18:00").onFirst().assertExists()
        screenshot("27_settings")

        onNode(verticalScroller).performScrollToNode(hasText("Темна"))
        onNodeWithText("Темна").performClick()
        onNodeWithContentDescription("Назад").performClick()
        waitForText("Сьогодні тренування")
        screenshot("28_home_dark")
    }

    @Test
    fun standaloneTimer(): Unit = with(composeRule) {
        waitForText("Сьогодні тренування")
        onNodeWithContentDescription("Таймер").performClick()
        waitForText("Секундомір")
        onNodeWithText("2 хв").performClick()
        waitForText("2:00")
        onNodeWithText("Старт").performClick()
        waitForText("Пауза")
        screenshot("29_timer")
        onNodeWithText("Пауза").performClick()
        waitForText("Скинути")
    }
}
