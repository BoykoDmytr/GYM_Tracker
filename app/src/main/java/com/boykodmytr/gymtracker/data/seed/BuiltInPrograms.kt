package com.boykodmytr.gymtracker.data.seed

import com.boykodmytr.gymtracker.domain.model.SetTarget
import com.boykodmytr.gymtracker.domain.model.TrainingDay
import com.boykodmytr.gymtracker.domain.transfer.ParsedProgram
import com.boykodmytr.gymtracker.domain.transfer.ParsedProgramExercise
import com.boykodmytr.gymtracker.domain.transfer.ParsedProgramWorkout
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Programs that ship with the app. A fresh install starts with [fullBodyV11] as the active program;
 * an existing install adds it from the Programs screen through the import review, which matches its
 * exercise names to the exercises already there, so history and weight suggestions stay connected.
 *
 * Exercise names are the ones the app already uses (built-in program, imported journal), so matching
 * works without manual picks wherever possible.
 */
object BuiltInPrograms {

    /** Heavy compound lifts: 3 min. Everything else: 2.5 min. A superset rests 3 min after each round. */
    private const val HEAVY = 180
    private const val NORMAL = 150

    private const val LATERALS = "Махи гантелями в сторони"
    private const val LATERALS_NOTE = "10 кг — тільки після 4×15 на 8,5 кг."
    private const val OVERHEAD_EXTENSION = "Розгинання над головою з гантеллю (трицепс)"
    private const val OVERHEAD_EXTENSION_NOTE = "Перші 3 підходи — запас 1 повтор, останній — до відмови. " +
        "Лікті вперед, внизу повне розтягнення, вгорі повне випрямлення. Коли всі 4 підходи по 15 — наступна гантеля."

    private fun exercise(
        name: String,
        sets: Int,
        repsMin: Int,
        repsMax: Int,
        weightKg: Double?,
        rest: Int,
        note: String = "",
        superset: String? = null,
    ) = ParsedProgramExercise(
        name = name,
        target = SetTarget(sets, sets, repsMin, repsMax, weightKg),
        restSeconds = rest,
        note = note,
        row = 0,
        superset = superset,
    )

    /** The usual week of [fullBodyV11]: Tue A · Thu Б · Sat В (Sunday works too; order matters, not dates). */
    val fullBodyV11Days = listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
        .map { TrainingDay(it, LocalTime.of(18, 0)) }

    /** "Тренування v11: фулбаді ×3": days A, B, C in rotation. */
    val fullBodyV11 = ParsedProgram(
        name = "Тренування v11: фулбаді ×3",
        description = "Три тренування на все тіло: А → Б → В за порядком, а не за календарем. " +
            "Звичайний тиждень: Вт А · Чт Б · Сб або Нд В; завантажений: Вт А · Пт Б · Нд В. " +
            "Запас повторів: у базових 1–2, в ізоляції 0–1; перший тиждень — 2–3 в усіх.",
        workouts = listOf(
            ParsedProgramWorkout(
                "День А",
                listOf(
                    exercise("Жим штанги лежачи", 3, 4, 6, 60.0, HEAVY, "У стійці з упорами. 62,5 кг — тільки після 6·6·6 на 60 кг."),
                    exercise("Болгарські присіди", 3, 8, 12, 16.0, NORMAL, "Повтори — на кожну ногу."),
                    exercise(LATERALS, 4, 8, 15, 8.5, HEAVY, LATERALS_NOTE, superset = "1"),
                    exercise(OVERHEAD_EXTENSION, 4, 8, 15, 10.0, HEAVY, OVERHEAD_EXTENSION_NOTE, superset = "1"),
                    exercise(
                        "Тяга гантелі однією рукою", 3, 6, 12, 24.0, NORMAL,
                        "Повтори — на кожну руку: починай зі слабшої лівої, правою стільки ж. Вільна рука в упорі, ноги в розножці, " +
                            "корпус майже паралельно підлозі; лікоть до стегна, внизу лопатка вперед. 24 кг на 12 легко — бери 28.",
                    ),
                ),
            ),
            ParsedProgramWorkout(
                "День Б",
                listOf(
                    exercise(
                        "Підтягування з вагою", 3, 3, 5, 2.5, HEAVY,
                        "Не до відмови: 3×3 → 3×4 → 3×5, потім ще +2,5 кг і знову 3×3. Вага — додаткова.",
                    ),
                    exercise("Жим штанги стоячи над головою", 3, 4, 6, 35.0, HEAVY),
                    exercise("Румунська тяга з гантелями", 3, 6, 10, 28.0, NORMAL, "Дві гантелі по 28 кг."),
                    exercise("Задня дельта на лаві", 3, 10, 15, 8.5, HEAVY, superset = "1"),
                    exercise("Молоткові згинання", 3, 8, 12, 10.0, HEAVY, superset = "1"),
                    exercise(
                        "Підйоми на носки стоячи", 3, 10, 15, 24.0, HEAVY,
                        "Якщо тренування тягнеться понад 75 хв — прибирай першими.",
                        superset = "2",
                    ),
                    exercise("Колесо з колін", 3, 8, 12, null, HEAVY, superset = "2"),
                ),
            ),
            ParsedProgramWorkout(
                "День В",
                listOf(
                    exercise("Жим гантелей на похилій лаві", 3, 6, 10, 16.0, NORMAL, "Лава 30°."),
                    exercise(
                        "Підтягування", 2, 7, 7, null, NORMAL,
                        "Без ваги, не до відмови: 2 підходи по половині максимуму (7 — половина від 14). У найближчому дні В " +
                            "перший підхід — тест до відмови, після нього ціль — половина результату; далі тест раз на 4 тижні. " +
                            "+1 повтор, коли обидва підходи легкі. Якщо напередодні був універ — пропускай.",
                    ),
                    exercise(LATERALS, 4, 8, 15, 8.5, HEAVY, LATERALS_NOTE, superset = "1"),
                    exercise(OVERHEAD_EXTENSION, 4, 8, 15, 10.0, HEAVY, OVERHEAD_EXTENSION_NOTE, superset = "1"),
                    exercise("Тяга верхнього блока до грудей", 3, 8, 12, 36.0, NORMAL),
                    exercise("Розгинання ніг, 1 нога", 3, 8, 12, null, NORMAL, "Вага — 4 плитки. Повтори — на кожну ногу."),
                    exercise("Підйом ніг у висі (прес/кор)", 3, 8, 15, null, NORMAL),
                ),
            ),
        ),
    )
}
