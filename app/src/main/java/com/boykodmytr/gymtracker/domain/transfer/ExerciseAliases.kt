package com.boykodmytr.gymtracker.domain.transfer

/**
 * Names that mean the same exercise. Only differences in wording are listed here: equipment, grip,
 * body position or attachment (barbell vs dumbbells, rope vs bar, seated vs lying, curl vs extension)
 * make a different exercise and are never merged. Groups that are only *probably* the same are left
 * out on purpose; the user maps them by hand on the import screen (see docs/EXERCISE_NAMES.md).
 *
 * The canonical name is the one already used by the built-in program, so imported history lands on
 * the exercise the user trains today.
 */
object ExerciseAliases {

    data class Group(val canonical: String, val aliases: List<String>)

    val groups: List<Group> = listOf(
        Group("Жим штанги лежачи", listOf("Жим штанги лежачи (обережно, без партнера)", "Жим лежачи", "Жим лежачи зі штангою", "Bench Press", "Barbell Bench Press")),
        Group("Жим гантелей лежачи", listOf("Жим гантелями лежачи (пласка лавка)", "Жим гантелями лежачи", "Жим гантелей лежачи (пласка лавка)", "Dumbbell Bench Press")),
        Group("Жим гантелей сидячи", listOf("Жим гантелей сидячи над головою", "Жим гантелями сидячи над головою", "Seated Dumbbell Shoulder Press", "Seated Dumbbell Press")),
        Group("Жим штанги стоячи над головою", listOf("Жим штанги над головою", "Армійський жим", "Overhead Press", "Barbell Overhead Press")),
        Group("Згинання рук з EZ-штангою", listOf("Підйом EZ-штанги на біцепс", "Згинання рук з EZ-грифом", "EZ Bar Curl")),
        Group("Молоткові згинання", listOf("Молотки з гантелями", "Молотки", "Hammer Curl", "Hammer Curls")),
        Group("Тяга гантелі однією рукою", listOf("Тяга гантелі однією рукою з упором", "One Arm Dumbbell Row", "Single Arm Dumbbell Row")),
        Group("Підйоми на носки стоячи", listOf("Підйом на ікри стоячи", "Підйом на носки стоячи", "Standing Calf Raise")),
        Group("Румунська тяга зі штангою", listOf("Romanian Deadlift", "Barbell Romanian Deadlift")),
        Group("Тяга штанги в нахилі", listOf("Bent Over Row", "Barbell Row", "Bent Over Barbell Row")),
    )

    private val canonicalByKey: Map<String, String> = buildMap {
        for (group in groups) {
            put(HeaderText.key(group.canonical), group.canonical)
            group.aliases.forEach { put(HeaderText.key(it), group.canonical) }
        }
    }

    /** The canonical name for [name], or null when the name is not in any group. */
    fun canonical(name: String): String? = canonicalByKey[HeaderText.key(name)]

    /** Every spelling of the group [name] belongs to (just [name] itself when it is in none). */
    fun spellings(name: String): List<String> {
        val canonical = canonical(name) ?: return listOf(name)
        val group = groups.first { it.canonical == canonical }
        return listOf(group.canonical) + group.aliases
    }
}
