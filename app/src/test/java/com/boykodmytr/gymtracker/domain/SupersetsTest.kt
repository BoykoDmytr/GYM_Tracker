package com.boykodmytr.gymtracker.domain

import com.boykodmytr.gymtracker.domain.logic.AfterSet
import com.boykodmytr.gymtracker.domain.logic.Supersets
import com.boykodmytr.gymtracker.domain.logic.WorkoutFlow
import com.boykodmytr.gymtracker.domain.model.ExerciseStatus
import com.boykodmytr.gymtracker.domain.model.SetTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupersetsTest {

    private var counter = 0
    private val newId = { "n${++counter}" }

    @Test
    fun `link joins two exercises and unlink splits them again`() {
        val linked = Supersets.link(listOf(null, null, null, null), 2, newId)
        assertEquals(listOf(null, null, "n1", "n1"), linked)
        assertTrue(Supersets.isLinkedWithNext(linked, 2))
        assertFalse(Supersets.isLinkedWithNext(linked, 3))
        assertEquals(listOf(null, null, null, null), Supersets.unlink(linked, 2, newId))
    }

    @Test
    fun `two separate pairs keep separate ids`() {
        // The user's lower-body day: 3+4 and 5+6 (indexes 2..5).
        var ids: List<String?> = List(6) { null }
        ids = Supersets.link(ids, 2, newId)
        ids = Supersets.link(ids, 4, newId)
        assertEquals(listOf(null, null, "n1", "n1", "n2", "n2"), ids)
        assertEquals(listOf(2..3, 4..5), Supersets.groups(ids))
    }

    @Test
    fun `linking next to an existing superset extends it, unlinking the middle splits it`() {
        val three = Supersets.link(listOf("a", "a", null), 1, newId)
        assertEquals(listOf("a", "a", "a"), three)
        val split = Supersets.unlink(three, 0, newId)
        assertEquals(listOf(null, "n1", "n1"), split)
    }

    @Test
    fun `normalize clears single members and renames a reused id`() {
        assertEquals(listOf(null, null, null), Supersets.normalize(listOf("a", null, "b"), newId))
        assertEquals(listOf("a", "a", null, "n1", "n1"), Supersets.normalize(listOf("a", "a", null, "a", "a"), newId))
    }

    @Test
    fun `superset alternates exercises and rests only after a round`() {
        val s = session(
            listOf(
                exercise("x", 0, sets = 3, status = ExerciseStatus.COMPLETED),
                exercise("a", 1, superset = "g"),
                exercise("b", 2, superset = "g"),
                exercise("c", 3),
            ),
            currentId = "a",
        )
        assertEquals(listOf("a", "b"), WorkoutFlow.supersetOf(s, s.exercises[1]).map { it.id })
        assertEquals(AfterSet("b", rest = false, workoutDone = false), WorkoutFlow.afterSet(s, "a"))

        val afterA = s.copy(exercises = s.exercises.map { if (it.id == "a") it.copy(sets = listOf(set("a", 1))) else it })
        assertEquals(AfterSet("a", rest = true, workoutDone = false), WorkoutFlow.afterSet(afterA, "b"))
    }

    @Test
    fun `exercise with fewer sets drops out of later rounds`() {
        val s = session(
            listOf(
                exercise("a", 0, target = SetTarget(2, 2, 8, 10), sets = 1, superset = "g"),
                exercise("b", 1, target = SetTarget(3, 3, 8, 10), sets = 1, superset = "g"),
            ),
            currentId = "a",
        )
        // a's last set → b, no rest.
        assertEquals(AfterSet("b", rest = false, workoutDone = false), WorkoutFlow.afterSet(s, "a"))
        // b's 2nd set: a is full, so b continues alone after a rest.
        val later = s.copy(exercises = s.exercises.map { if (it.id == "a") it.copy(sets = listOf(set("a", 1), set("a", 2))) else it })
        assertEquals(AfterSet(null, rest = true, workoutDone = false), WorkoutFlow.afterSet(later, "b"))
    }

    @Test
    fun `last set of the last superset finishes the workout`() {
        val s = session(
            listOf(
                exercise("a", 0, sets = 3, superset = "g"),
                exercise("b", 1, sets = 2, superset = "g"),
            ),
            currentId = "b",
        )
        assertEquals(AfterSet(null, rest = true, workoutDone = true), WorkoutFlow.afterSet(s, "b"))
    }

    @Test
    fun `exercise without superset keeps the old behaviour`() {
        val s = session(listOf(exercise("a", 0, sets = 1), exercise("b", 1)), currentId = "a")
        assertEquals(AfterSet(null, rest = true, workoutDone = false), WorkoutFlow.afterSet(s, "a"))
    }
}
