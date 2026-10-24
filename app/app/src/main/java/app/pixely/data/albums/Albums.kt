package app.pixely.data.albums

import android.os.SystemClock
import app.pixely.data.db.PixelyDatabase
import app.pixely.data.diagnostics.DiagnosticsLog
import app.pixely.data.search.QueryEmbedder
import app.pixely.data.search.dot
import app.pixely.data.search.forEachVector
import app.pixely.data.search.readVectors
import app.pixely.data.search.truncated
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Albums that fill themselves: every photo scoring at least [cut] against [query].
 * Queries and cut-offs tuned on the POC's 538 COCO photos (Food, Pets and Beach labelled by COCO's
 * captions) plus 30 synthetic screenshots and 30 photographed documents, each cut set where at most
 * about 0.5% of photos that don't belong get in. Beach had only 7 examples; the rest 30 to 90.
 */
enum class SmartSet(val label: String, val query: String, val cut: Float) {
    FOOD("Food", "food", 0.72f),              // 80% found
    BEACH("Beach", "a photo of a beach", 0.725f), // 71% found
    PETS("Pets", "a dog or a cat", 0.695f),   // 79% found
    SCREENSHOTS("Screenshots", "a screenshot of a phone screen", 0.735f), // all synthetic ones found
    DOCUMENTS("Documents", "a photo of a paper document, receipt or form", 0.69f), // 97% found
}

/** Photo ids, newest first. */
data class AlbumContents(val sets: Map<SmartSet, List<Long>>, val duplicates: List<List<Long>>) {
    val duplicatePhotos get() = duplicates.sumOf { it.size }
}

/**
 * Works out the smart sets and duplicate groups in one pass over the stored vectors. Kept until the
 * index changes. On the POCO X7 Pro a pass over 11,836 photos takes about 10 s.
 */
@Singleton
class Albums @Inject constructor(
    private val db: PixelyDatabase,
    private val queries: QueryEmbedder,
    private val log: DiagnosticsLog,
) {
    private val lock = Mutex()
    private var queryVectors: Map<SmartSet, FloatArray>? = null
    private var cached: Pair<String, AlbumContents>? = null

    /**
     * The albums now, and again whenever the index changes; while indexing runs that's every photo, so
     * at most once every [REFRESH_MS].
     */
    fun observe(): Flow<AlbumContents> = db.photoDao().observeIndexVersion().conflate().transform { version ->
        emit(contents(version))
        delay(REFRESH_MS)
    }

    /** [version] is PhotoDao.observeIndexVersion's; the same version returns the kept result. */
    suspend fun contents(version: String): AlbumContents = lock.withLock {
        cached?.takeIf { it.first == version }?.let { return it.second }
        val vectors = queryVectors ?: SmartSet.entries.associateWith { queries.embed(it.query) }.also { queryVectors = it }
        withContext(Dispatchers.Default) { work(vectors) }.also { cached = version to it }
    }

    private suspend fun work(queryVectors: Map<SmartSet, FloatArray>): AlbumContents {
        val t0 = SystemClock.elapsedRealtime()
        val takenAt = db.photoDao().indexedTimes().associate { it.id to it.takenAt }
        val ids = ArrayList<Long>(takenAt.size)
        val prefixes = FloatArray(takenAt.size * Duplicates.PREFIX)
        val sets = SmartSet.entries.associateWith { ArrayList<Long>() }
        db.forEachVector { id, v ->
            // A vector whose photo is being re-indexed or was just removed: leave it out.
            if (id !in takenAt) return@forEachVector
            for ((set, q) in queryVectors) if (dot(q, v) >= set.cut) sets.getValue(set) += id
            v.truncated(Duplicates.PREFIX).copyInto(prefixes, ids.size * Duplicates.PREFIX)
            ids += id
        }
        val t1 = SystemClock.elapsedRealtime()
        val pairs = Duplicates.candidates(prefixes, ids.size, Duplicates.PREFIX)
        val t2 = SystemClock.elapsedRealtime()

        // Full-size check of the candidates only.
        val involved = pairs.flatMapTo(HashSet()) { listOf(ids[(it ushr 32).toInt()], ids[(it and 0xffffffffL).toInt()]) }
        val full = db.readVectors(involved)
        val times = LongArray(ids.size) { takenAt.getValue(ids[it]) }
        val groups = Duplicates.group(ids.size, pairs, times) { i, j -> dot(full.getValue(ids[i]), full.getValue(ids[j])) }
            .map { g -> g.map { ids[it] } }
        log.i(TAG, "${ids.size} photos: read ${t1 - t0} ms, ${pairs.size} candidate pairs in ${t2 - t1} ms, " +
            "${groups.size} duplicate groups; ${SystemClock.elapsedRealtime() - t0} ms in all")

        val newestFirst = compareByDescending<Long> { takenAt.getValue(it) }
        return AlbumContents(sets.mapValues { it.value.sortedWith(newestFirst) }, groups)
    }

    private companion object {
        const val TAG = "Albums"
        const val REFRESH_MS = 20_000L
    }
}
