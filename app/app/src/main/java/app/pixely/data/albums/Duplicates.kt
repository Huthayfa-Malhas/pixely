package app.pixely.data.albums

import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Near-identical photos, grouped. Two photos match at [SAME] or above, or at [BURST] when taken within
 * [BURST_MS] of each other; a group is everything joined by matches.
 *
 * Tuned on the POC's 538 COCO photos at 140 tokens: edited copies (re-saved, cropped to 90%, brighter,
 * rotated 3°, mirrored, shifted like a burst) scored 0.96 or more 90-100% of the time; the closest
 * pair of different photos (two zebra herds) scored 0.969, the next 0.954.
 */
object Duplicates {
    const val SAME = 0.96f
    const val BURST = 0.93f
    const val BURST_MS = 2 * 60_000L

    /** Every pair is compared on the first [PREFIX] numbers first; on COCO no match fell below 0.955 there. */
    const val PREFIX = 128
    const val PREFIX_CUT = 0.93f

    /**
     * Pairs (i, j), i < j, whose [PREFIX]-number vectors are at least [cut] alike. [flat] holds n
     * vectors of [dims] numbers each, one after another. Rows are split across the cores.
     */
    suspend fun candidates(flat: FloatArray, n: Int, dims: Int, cut: Float = PREFIX_CUT): List<Long> = coroutineScope {
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        (0 until threads).map { t ->
            async(Dispatchers.Default) {
                val out = ArrayList<Long>()
                var i = t
                while (i < n) {
                    val a = i * dims
                    for (j in i + 1 until n) {
                        val b = j * dims
                        var s = 0f
                        for (k in 0 until dims) s += flat[a + k] * flat[b + k]
                        if (s >= cut) out += (i.toLong() shl 32) or j.toLong()
                    }
                    i += threads
                }
                out
            }
        }.awaitAll().flatten()
    }

    /**
     * Groups of indexes into [takenAt], newest first, each newest first. [score] gives the full-size
     * similarity of a candidate pair from [candidates].
     */
    fun group(n: Int, pairs: List<Long>, takenAt: LongArray, score: (Int, Int) -> Float): List<List<Int>> {
        val parent = IntArray(n) { it }
        fun root(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var y = x
            while (parent[y] != r) { val next = parent[y]; parent[y] = r; y = next }
            return r
        }
        for (p in pairs) {
            val i = (p ushr 32).toInt()
            val j = (p and 0xffffffffL).toInt()
            if (root(i) == root(j)) continue
            val s = score(i, j)
            if (s >= SAME || (s >= BURST && abs(takenAt[i] - takenAt[j]) <= BURST_MS)) parent[root(i)] = root(j)
        }
        return (0 until n).groupBy(::root).values
            .filter { it.size > 1 }
            .map { g -> g.sortedByDescending { takenAt[it] } }
            .sortedByDescending { takenAt[it.first()] }
    }
}
