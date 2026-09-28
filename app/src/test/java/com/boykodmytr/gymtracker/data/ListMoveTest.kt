package com.boykodmytr.gymtracker.data

import com.boykodmytr.gymtracker.data.repository.moved
import org.junit.Assert.assertEquals
import org.junit.Test

class ListMoveTest {
    private val list = listOf("a", "b", "c")

    @Test
    fun `moves and clamps`() {
        assertEquals(listOf("b", "a", "c"), list.moved("a", 1) { it })
        assertEquals(listOf("c", "a", "b"), list.moved("c", -5) { it })
        assertEquals(list, list.moved("c", 1) { it })
        assertEquals(list, list.moved("x", 1) { it })
    }
}
