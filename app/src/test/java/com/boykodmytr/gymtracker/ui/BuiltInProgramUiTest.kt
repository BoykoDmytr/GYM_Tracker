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
import com.boykodmytr.gymtracker.data.seed.DataSeeder
import com.boykodmytr.gymtracker.domain.repository.ProgramRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * An install that already has its own program and exercises adds the built-in program from the
 * Programs screen: names land on the exercises already there, nothing is typed by hand.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BuiltInProgramUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var seeder: DataSeeder
    @Inject lateinit var programs: ProgramRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking { seeder.seedIfNeeded() }
    }

    @Test
    fun addBuiltInProgramFromProgramsScreen(): Unit = with(composeRule) {
        waitForText("Сьогодні тренування")
        onNodeWithText("Програми").performClick()
        waitForText("Full Body — 3 рази на тиждень")
        onNode(verticalScroller).performScrollToNode(hasText("Готова програма"))
        waitForText("Тренування v11: фулбаді ×3")
        screenshot("60_programs_built_in")
        onNodeWithText("Додати").performClick()

        waitForText("Буде створено програму", timeoutMillis = 10_000)
        onNodeWithText("«Тренування v11: фулбаді ×3»: тренувань — 3, вправ — 19").assertExists()
        waitForText("Готова програма з застосунку", substring = true)
        onNode(verticalScroller).performScrollToNode(hasText("3. Махи гантелями в сторони"))
        onAllNodesWithText("4×8–15 · 8,5 кг · 3 хв · суперсет 1").onFirst().assertExists()
        screenshot("61_built_in_review")

        onAllNodesWithText("Імпортувати").onFirst().performClick()
        waitForText("Імпорт завершено")

        val active = runBlocking { programs.observeActiveProgram().first()!! }
        assertEquals("Тренування v11: фулбаді ×3", active.name)
        val all = runBlocking { programs.observePrograms().first() }
        val legacyBench = all.first { it.name.startsWith("Full Body") }.workouts[0].exercises[0].exercise
        // The bench press of the new program is the exercise the user already had: history stays.
        assertEquals(legacyBench.id, active.workouts[0].exercises[0].exercise.id)
        assertEquals(2, all.size)
        // Supersets came along: laterals + overhead extension on day A.
        val dayA = active.workouts[0].exercises
        assertEquals(dayA[2].supersetId, dayA[3].supersetId)
        assertEquals(true, dayA[2].supersetId != null && dayA[4].supersetId == null)

        // Back on the Programs screen the offer is gone.
        onNodeWithContentDescription("Назад").performClick()
        waitForText("Full Body — 3 рази на тиждень")
        waitForNoText("Готова програма")
    }
}
