package app.pixely

import app.pixely.data.albums.Duplicates
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicatesTest {

    private fun unit(vararg v: Float): FloatArray {
        val n = kotlin.math.sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(v.size) { v[it] / n }
    }

    @Test
    fun candidatesAreThePairsOverTheCut() = runBlocking {
        val vs = listOf(unit(1f, 0f), unit(1f, 0.1f), unit(0f, 1f), unit(1f, 0.05f))
        val flat = FloatArray(vs.size * 2).also { f -> vs.forEachIndexed { i, v -> v.copyInto(f, i * 2) } }
        val pairs = Duplicates.candidates(flat, vs.size, 2, cut = 0.99f).map { (it ushr 32).toInt() to (it and 0xffffffffL).toInt() }.toSet()
        assertEquals(setOf(0 to 1, 0 to 3, 1 to 3), pairs)
    }

    @Test
    fun matchesJoinIntoGroupsAndBurstsNeedToBeClose() {
        val minute = 60_000L
        val takenAt = longArrayOf(0, 10 * minute, 20 * minute, 20 * minute + 30_000, 50 * minute)
        val scores = mapOf(
            (0 to 1) to 0.97f, (1 to 2) to 0.965f,  // same, chained: 0, 1, 2 together
            (2 to 3) to 0.94f,                       // burst, 30 s apart: joins
            (0 to 4) to 0.94f,                       // as alike, 50 min apart: doesn't
        )
        val pairs = scores.keys.map { (i, j) -> (i.toLong() shl 32) or j.toLong() }
        val groups = Duplicates.group(5, pairs, takenAt) { i, j -> scores.getValue(i to j) }
        assertEquals(listOf(listOf(3, 2, 1, 0)), groups)
    }

    @Test
    fun groupsAreNewestFirst() {
        val takenAt = longArrayOf(1, 2, 100, 200)
        val pairs = listOf(0L shl 32 or 1L, 2L shl 32 or 3L)
        assertEquals(listOf(listOf(3, 2), listOf(1, 0)), Duplicates.group(4, pairs, takenAt) { _, _ -> 0.99f })
    }
}
