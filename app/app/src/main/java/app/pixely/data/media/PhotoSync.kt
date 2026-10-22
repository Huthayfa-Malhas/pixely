package app.pixely.data.media

import android.util.Log
import app.pixely.data.db.IndexState
import app.pixely.data.db.Photo
import app.pixely.data.db.PixelyDatabase
import app.pixely.data.db.SyncDiff
import app.pixely.data.db.SyncKey
import app.pixely.data.index.IndexScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Brings the photo table in line with MediaStore. A full compare is cheap enough (10k rows read
 * and diffed in well under a second) that there's no change-token bookkeeping to get wrong.
 */
@Singleton
class PhotoSync @Inject constructor(
    private val db: PixelyDatabase,
    private val source: MediaStoreSource,
    private val indexer: IndexScheduler,
) {
    private val mutex = Mutex()

    suspend fun sync(): SyncDiff = mutex.withLock {
        withContext(Dispatchers.IO) {
            val started = System.nanoTime()
            val dao = db.photoDao()
            val diff = diff(dao.syncKeys(), source.readAll())
            if (!diff.isEmpty) dao.apply(diff)
            if (diff.added.isNotEmpty() || diff.changed.isNotEmpty()) indexer.start()
            Log.i(TAG, "Sync: +${diff.added.size} ~${diff.changed.size} -${diff.removed.size} in ${(System.nanoTime() - started) / 1_000_000} ms")
            diff
        }
    }

    companion object {
        private const val TAG = "PhotoSync"

        /** New ids are added, a new date_modified means new pixels (pending again), and the rest are gone. */
        fun diff(stored: List<SyncKey>, current: List<Photo>): SyncDiff {
            val byMediaId = stored.associateBy { it.mediaStoreId }
            val added = ArrayList<Photo>()
            val changed = ArrayList<Photo>()
            for (photo in current) {
                val known = byMediaId[photo.mediaStoreId]
                when {
                    known == null -> added += photo
                    known.dateModified != photo.dateModified -> changed += photo.copy(id = known.id, indexState = IndexState.PENDING, indexedAt = null)
                }
            }
            val seen = current.mapTo(HashSet()) { it.mediaStoreId }
            val removed = stored.filter { it.mediaStoreId !in seen }.map { it.id }
            return SyncDiff(added, changed, removed)
        }
    }
}
