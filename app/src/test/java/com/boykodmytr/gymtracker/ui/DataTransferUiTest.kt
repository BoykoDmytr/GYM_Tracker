package com.boykodmytr.gymtracker.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import com.boykodmytr.gymtracker.domain.repository.WorkoutRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DataTransferUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var seeder: DataSeeder
    @Inject lateinit var workouts: WorkoutRepository
    @Inject lateinit var programs: ProgramRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking { seeder.seedIfNeeded() }
    }

    /** Answers the system file picker the screen just opened, as if the user chose [file]. */
    private fun answerPicker(expectedAction: String, file: File) {
        composeRule.waitForIdle()
        val started = shadowOf(composeRule.activity).nextStartedActivityForResult
        assertEquals(expectedAction, started.intent.action)
        shadowOf(composeRule.activity).receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
    }

    private fun openSettingsItem(title: String) = with(composeRule) {
        waitForText("Сьогодні тренування")
        onNodeWithContentDescription("Налаштування").performClick()
        waitForText("Дні та час тренувань")
        onNode(verticalScroller).performScrollToNode(hasText(title))
        screenshot("40_settings_data")
        onNodeWithText(title).performClick()
    }

    @Test
    fun importOldJournalReviewConfirmAndUndo(): Unit = with(composeRule) {
        // Excel "CSV" from a Ukrainian Windows: Windows-1251, semicolons, decimal commas, dd.MM.yyyy.
        val csv = File(activity.cacheDir, "Журнал тренувань.csv").apply {
            writeBytes(
                """
                Дата;Тренування;Вправа;Підхід;Вага (кг);Повторення;Нотатки;Вага тіла
                12.05.2026;Push;Жим штанги лежачи (обережно, без партнера);1;50;12;;90
                12.05.2026;Push;Жим штанги лежачи (обережно, без партнера);2;62,5;7;штанга зачепилась;
                12.05.2026;Push;Махи гантелями в сторони;1;5(2);15;;
                18.05.2026;Push;Жим лежачи;1;55;10;;89,5
                20.05.2026;Pull;Молотки з гантелями;1;12;8;;
                20.05.2026;Pull;Згинання ніг на HouseFit;1;*;15;;
                """.trimIndent().replace("\n", "\r\n").toByteArray(charset("windows-1251")),
            )
        }
        openSettingsItem("Імпорт даних")
        waitForText("Вибрати CSV-файл")
        screenshot("41_import_start")
        onNodeWithText("Вибрати CSV-файл").performClick()
        answerPicker(Intent.ACTION_OPEN_DOCUMENT, csv)

        waitForText("Що буде додано", timeoutMillis = 10_000)
        onNodeWithText("Тренувань: 3").assertExists()
        onNodeWithText("Підходів: 5").assertExists()
        onNodeWithText("Кодування Windows-1251 · роздільник «;»").assertExists()
        screenshot("42_import_review")
        // Both spellings of the bench press land on the app's exercise; the problem row is reported.
        onNode(verticalScroller).performScrollToNode(hasText("Жим штанги лежачи (обережно, без партнера)"))
        assertEquals(2, onAllNodesWithText("Жим штанги лежачи").fetchSemanticsNodes().size)
        onNode(verticalScroller).performScrollToNode(hasText("Рядок 7: вага «*» — не число, підхід пропущено"))
        screenshot("43_import_issues")

        onNode(verticalScroller).performScrollToNode(hasText("Імпортувати"))
        onAllNodesWithText("Імпортувати").onFirst().performClick()
        waitForText("Імпорт завершено")
        onNodeWithText("Додано тренувань: 3, підходів: 5, вимірів: 2, нових вправ: 1. Вони вже в історії, статистиці й графіках.").assertExists()
        screenshot("44_import_done")
        assertEquals(3, runBlocking { workouts.observeAllCompletedSummaries().first().size })

        onNodeWithText("Скасувати цей імпорт").performClick()
        onNode(inDialog("Скасувати")).performClick()
        waitForText("Скасовано: видалено тренувань — 3, вимірів — 2.")
        assertTrue(runBlocking { workouts.observeAllCompletedSummaries().first().isEmpty() })
    }

    @Test
    fun importProgramAndMakeItActive(): Unit = with(composeRule) {
        val csv = File(activity.cacheDir, "Верх-Низ.csv").apply {
            writeText(
                "\uFEFFПрограма;Тренування;Вправа;Підходи;Повторення;Вага (кг);Відпочинок (с);Нотатка\r\n" +
                    "Верх / Низ v11.2;Верх-А;Жим лежачи;3;5-7;60;150;Хват ширше плечей\r\n" +
                    "Верх / Низ v11.2;Верх-А;Махи в сторони;3;8-12;7,5;90;\r\n" +
                    "Верх / Низ v11.2;Низ;Болгарські присіди;3;8-12;16;150;на ногу\r\n",
            )
        }
        openSettingsItem("Імпорт даних")
        waitForText("Вибрати CSV-файл")
        onNodeWithText("Вибрати CSV-файл").performClick()
        answerPicker(Intent.ACTION_OPEN_DOCUMENT, csv)

        waitForText("Буде створено програму", timeoutMillis = 10_000)
        onNodeWithText("«Верх / Низ v11.2»: тренувань — 2, вправ — 3").assertExists()
        screenshot("47_program_review")
        onNode(verticalScroller).performScrollToNode(hasText("1. Жим лежачи"))
        onNodeWithText("3×5–7 · 60 кг · 2:30").assertExists()

        onAllNodesWithText("Імпортувати").onFirst().performClick()
        waitForText("Імпорт завершено")
        onNodeWithText("Створено програм: 1, нових вправ: 2. Програма вже в розділі «Програми».").assertExists()
        val active = runBlocking { programs.observeActiveProgram().first()!! }
        assertEquals("Верх / Низ v11.2", active.name)
        assertEquals(listOf("Верх-А", "Низ"), active.workouts.map { it.name })
    }

    @Test
    fun spreadsheetFileGetsAClearMessage(): Unit = with(composeRule) {
        val xlsx = File(activity.cacheDir, "journal.xlsx").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0)) }
        openSettingsItem("Імпорт даних")
        waitForText("Вибрати CSV-файл")
        onNodeWithText("Вибрати CSV-файл").performClick()
        answerPicker(Intent.ACTION_OPEN_DOCUMENT, xlsx)
        waitForText("CSV UTF-8", substring = true)
    }

    @Test
    fun exportWritesExcelFriendlyCsv(): Unit = with(composeRule) {
        val target = File(activity.cacheDir, "export.csv")
        openSettingsItem("Експорт даних")
        waitForText("Історія тренувань")
        screenshot("45_export")
        onAllNodesWithText("Зберегти CSV").onFirst().performClick()
        answerPicker(Intent.ACTION_CREATE_DOCUMENT, target)
        waitForText("Файл збережено", substring = true)
        val text = target.readText(Charsets.UTF_8)
        assertTrue(text.startsWith("\uFEFFДата;Час початку;Тривалість (хв);Тренування;"))
    }

    @Test
    fun mergeExerciseFromEditor(): Unit = with(composeRule) {
        waitForText("Сьогодні тренування")
        onNodeWithText("Програми").performClick()
        waitForText("Вправи")
        onNodeWithText("Вправи").performClick()
        waitForText("Бібліотека вправ")
        onNode(verticalScroller).performScrollToNode(hasText("Розгинання рук на верхньому блоці"))
        onNodeWithText("Розгинання рук на верхньому блоці").performClick()
        waitForText("Обʼєднати з іншою вправою")
        onNode(verticalScroller).performScrollToNode(hasText("Обʼєднати з іншою вправою"))
        onNodeWithText("Обʼєднати з іншою вправою").performClick()
        waitForText("Куди перенести історію?")
        onNode(verticalScroller).performScrollToNode(hasText("Розгинання рук на блоці"))
        onNodeWithText("Розгинання рук на блоці").performClick()
        waitForText("Обʼєднати вправи?")
        onNode(inDialog("Обʼєднати")).performClick()
        waitForText("Історію перенесено в «Розгинання рук на блоці»")
        screenshot("46_merged")
    }
}
