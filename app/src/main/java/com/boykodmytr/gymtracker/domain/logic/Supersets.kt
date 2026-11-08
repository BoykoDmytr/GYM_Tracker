package com.boykodmytr.gymtracker.domain.logic

/**
 * Supersets are stored as a shared id on neighbouring exercises. These functions work on the ids of a
 * workout's exercises in order and always return a normalized list: every id marks one contiguous run
 * of at least two exercises, everything else is null.
 */
object Supersets {

    fun isLinkedWithNext(ids: List<String?>, index: Int): Boolean {
        val id = ids.getOrNull(index) ?: return false
        return ids.getOrNull(index + 1) == id
    }

    /** Joins exercise [index] with the next one (and with the supersets either of them is already in). */
    fun link(ids: List<String?>, index: Int, newId: () -> String): List<String?> {
        if (index !in 0 until ids.lastIndex) return normalize(ids, newId)
        val result = ids.toMutableList()
        val first = result[index]
        val second = result[index + 1]
        val id = first ?: second ?: newId()
        result[index] = id
        if (second != null) {
            var i = index + 1
            while (i < result.size && result[i] == second) result[i++] = id
        } else {
            result[index + 1] = id
        }
        return normalize(result, newId)
    }

    /** Splits the superset between exercise [index] and the next one. */
    fun unlink(ids: List<String?>, index: Int, newId: () -> String): List<String?> {
        if (!isLinkedWithNext(ids, index)) return normalize(ids, newId)
        val result = ids.toMutableList()
        val old = result[index]
        val fresh = newId()
        var i = index + 1
        while (i < result.size && result[i] == old) result[i++] = fresh
        return normalize(result, newId)
    }

    /** Clears single-exercise "supersets" and gives a fresh id to a run that reuses an earlier run's id. */
    fun normalize(ids: List<String?>, newId: () -> String): List<String?> {
        val result = ids.toMutableList()
        val seen = mutableSetOf<String>()
        var start = 0
        while (start < result.size) {
            val id = result[start]
            var end = start + 1
            while (end < result.size && result[end] == id) end++
            if (id != null) {
                val replacement = when {
                    end - start < 2 -> null
                    id in seen -> newId()
                    else -> id
                }
                for (i in start until end) result[i] = replacement
                replacement?.let(seen::add)
            }
            start = end
        }
        return result
    }

    /**
     * Supersets written as labels, e.g. a program file's «Суперсет» column: neighbouring exercises with
     * the same label (case and spaces ignored) form one superset. Returns normalized ids.
     */
    fun fromLabels(labels: List<String?>, newId: () -> String): List<String?> {
        val ids = mutableListOf<String?>()
        var previousKey: String? = null
        for (label in labels) {
            val key = label?.trim()?.lowercase()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }
            ids += when {
                key == null -> null
                key == previousKey -> ids.last()
                else -> newId()
            }
            previousKey = key
        }
        return normalize(ids, newId)
    }

    /** Index ranges of the supersets in [ids] (normalized input expected). */
    fun groups(ids: List<String?>): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var start = 0
        while (start < ids.size) {
            var end = start + 1
            while (end < ids.size && ids[start] != null && ids[end] == ids[start]) end++
            if (end - start >= 2) ranges += start until end
            start = end
        }
        return ranges
    }
}
