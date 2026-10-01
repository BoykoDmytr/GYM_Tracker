package com.boykodmytr.gymtracker.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
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

/** Links the first two exercises of a workout and does one superset round: A1 → B1 (no rest) → rest → A2. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SupersetUiTest {

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
    fun supersetAlternatesExercisesAndRestsAfterRound(): Unit = with(composeRule) {
        waitForText("Сьогодні тренування")
        onNodeWithText("Програми").performClick()
        waitForText("Full Body — 3 рази на тиждень")
        onNodeWithText("Full Body — 3 рази на тиждень").performClick()
        waitForText("Full Body A")
        onNodeWithText("Full Body A").performClick()
        waitForText("1. Жим штанги лежачи")

        onAllNodesWithText("Обʼєднати в суперсет").onFirst().performClick()
        waitForText("Розʼєднати суперсет")
        onAllNodesWithText("Суперсет").fetchSemanticsNodes().size.let { check(it == 2) { "expected 2 superset labels, got $it" } }
        screenshot("40_template_superset")

        onNodeWithContentDescription("Назад").performClick()
        waitForText("Full Body A")
        onNodeWithContentDescription("Назад").performClick()
        onNodeWithText("Головна").performClick()
        waitForText("Почати тренування")
        onNodeWithText("Почати тренування").performClick()
        waitForText("Почати")
        onNodeWithText("Почати").performClick()

        waitForText("Підхід 1 / 3")
        waitForText("Суперсет: Жим штанги лежачи → Румунська тяга зі штангою")
        onNodeWithText("Після підходу — одразу Румунська тяга зі штангою, без відпочинку").assertExists()
        screenshot("41_superset_a1")

        logSet("60", "8")
        // Straight to the second exercise, no rest in between.
        waitForText("Після підходу — відпочинок, потім нове коло")
        onNodeWithText("Румунська тяга зі штангою").assertExists()
        onNodeWithText("Підхід 1 / 3").assertExists()
        waitForNoText("Відпочинок")
        screenshot("42_superset_b1")

        logSet("50", "10")
        // Round done: rest, then back to the first exercise.
        waitForText("Відпочинок")
        waitForText("Підхід 2 / 3")
        onNodeWithText("Жим штанги лежачи").assertExists()
        screenshot("43_superset_round_rest")
    }

    private fun logSet(weight: String, reps: String) = with(composeRule) {
        onNodeWithText("Завершити підхід").performClick()
        waitForText("Зберегти")
        onAllNodes(hasSetTextAction())[0].performTextReplacement(weight)
        onAllNodes(hasSetTextAction())[1].performTextReplacement(reps)
        onNodeWithText("Зберегти").performClick()
        waitForNoText("Зберегти")
    }
}
