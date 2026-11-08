package com.boykodmytr.gymtracker.testing

import com.boykodmytr.gymtracker.domain.model.SetTarget
import com.boykodmytr.gymtracker.domain.transfer.ParsedProgram
import com.boykodmytr.gymtracker.domain.transfer.ParsedProgramExercise
import com.boykodmytr.gymtracker.domain.transfer.ParsedProgramWorkout

/**
 * The app's first starter program (no weights, default rest), kept as the fixture the UI tests are
 * written against, so they do not change whenever the real starter program does.
 */
object LegacyStarterProgram {

    private fun e(name: String, setsMin: Int, setsMax: Int, repsMin: Int, repsMax: Int) =
        ParsedProgramExercise(name, SetTarget(setsMin, setsMax, repsMin, repsMax), restSeconds = null, note = "", row = 0)

    val program = ParsedProgram(
        name = "Full Body — 3 рази на тиждень",
        description = "Три тренування на все тіло (A, B, C), які чергуються по черзі у дні тренувань.",
        workouts = listOf(
            ParsedProgramWorkout(
                "Full Body A",
                listOf(
                    e("Жим штанги лежачи", 3, 3, 6, 8),
                    e("Румунська тяга зі штангою", 3, 3, 8, 10),
                    e("Тяга гантелі однією рукою", 3, 3, 8, 12),
                    e("Жим гантелей сидячи", 3, 3, 8, 10),
                    e("Тяга верхнього блока до грудей", 3, 3, 8, 12),
                    e("Згинання рук з EZ-штангою", 2, 2, 10, 12),
                    e("Прес", 2, 3, 10, 15),
                ),
            ),
            ParsedProgramWorkout(
                "Full Body B",
                listOf(
                    e("Жим гантелей лежачи", 3, 3, 8, 12),
                    e("Присідання до лавки / боксу", 3, 3, 8, 10),
                    e("Тяга штанги в нахилі", 3, 3, 8, 10),
                    e("Румунська тяга з гантелями/штангою", 3, 3, 8, 12),
                    e("Верхній блок вузьким/нейтральним хватом", 3, 3, 10, 12),
                    e("Розгинання рук на верхньому блоці", 2, 3, 10, 15),
                    e("Підйоми на носки стоячи", 3, 3, 12, 20),
                ),
            ),
            ParsedProgramWorkout(
                "Full Body C",
                listOf(
                    e("Жим штанги лежачи", 3, 3, 8, 10),
                    e("Румунська тяга з гантелями/штангою", 3, 3, 8, 12),
                    e("Підтягування / верхній блок", 3, 3, 8, 12),
                    e("Жим гантелей сидячи", 3, 3, 8, 12),
                    e("Тяга гантелей/штанги", 3, 3, 10, 12),
                    e("Молоткові згинання", 2, 2, 10, 15),
                    e("Розгинання рук на блоці", 2, 2, 10, 15),
                ),
            ),
        ),
    )
}
