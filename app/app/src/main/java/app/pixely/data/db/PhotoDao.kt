package app.pixely.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PhotoDao {

    @Query("SELECT id, uri, taken_at, width, height FROM photo ORDER BY taken_at DESC, id DESC")
    abstract fun observeGrid(): Flow<List<GridPhoto>>

    @Query("SELECT COUNT(*) FROM photo")
    abstract fun observeCount(): Flow<Int>

    @Query("SELECT id, media_store_id, date_modified FROM photo")
    abstract suspend fun syncKeys(): List<SyncKey>

    @Insert
    abstract suspend fun insert(photos: List<Photo>)

    @Update
    abstract suspend fun update(photos: List<Photo>)

    @Query("DELETE FROM photo WHERE id IN (:ids)")
    abstract suspend fun delete(ids: List<Long>)

    /** In the order of [ids]' first appearance is the caller's job: SQL returns them in any order. */
    @Query("SELECT id, uri, taken_at, width, height FROM photo WHERE id IN (:ids)")
    abstract suspend fun byIds(ids: List<Long>): List<GridPhoto>

    @Query("SELECT id, taken_at FROM photo WHERE index_state = 'DONE'")
    abstract suspend fun indexedTimes(): List<PhotoTime>

    /** Changes whenever a photo is indexed, re-indexed or removed: albums are worked out again then. */
    @Query("SELECT COUNT(*) || ':' || COALESCE(MAX(indexed_at), 0) FROM photo WHERE index_state = 'DONE'")
    abstract fun observeIndexVersion(): Flow<String>

    @Query("SELECT id FROM photo WHERE taken_at >= :from AND taken_at < :until AND index_state = 'DONE'")
    abstract suspend fun indexedBetween(from: Long, until: Long): List<Long>

    /** Newest first, so a half-indexed library already finds recent photos. */
    @Query("SELECT id, uri, taken_at, width, height FROM photo WHERE index_state = 'PENDING' ORDER BY taken_at DESC, id DESC LIMIT :limit")
    abstract suspend fun nextPending(limit: Int): List<GridPhoto>

    /** 0 rows means the photo was deleted while it was being indexed. */
    @Query("UPDATE photo SET index_state = 'DONE', indexed_at = :at WHERE id = :id AND index_state = 'PENDING'")
    abstract suspend fun markDone(id: Long, at: Long): Int

    @Query("UPDATE photo SET index_state = 'FAILED', indexed_at = :at WHERE id = :id")
    abstract suspend fun markFailed(id: Long, at: Long)

    /** After a model or detail-level change. Old vectors stay searchable until each is replaced. */
    @Query("UPDATE photo SET index_state = 'PENDING'")
    abstract suspend fun markAllPending()

    @Query("SELECT COUNT(*) FROM photo WHERE index_state = 'PENDING'")
    abstract suspend fun pendingCount(): Int

    @Query(
        "SELECT COUNT(*) AS total, " +
            "COALESCE(SUM(index_state = 'DONE'), 0) AS done, COALESCE(SUM(index_state = 'FAILED'), 0) AS failed FROM photo"
    )
    abstract fun observeProgress(): Flow<IndexProgress>

    @Query("SELECT id, uri, mime, width, height, indexed_at FROM photo WHERE index_state = 'FAILED' ORDER BY indexed_at DESC LIMIT :limit")
    abstract suspend fun failed(limit: Int): List<FailedPhoto>

    @Query("SELECT id, uri, taken_at, width, height FROM photo WHERE index_state = 'DONE' ORDER BY indexed_at DESC LIMIT :limit")
    abstract fun observeRecentlyIndexed(limit: Int): Flow<List<GridPhoto>>

    /** One transaction so the grid never sees a half-applied sync. */
    @Transaction
    open suspend fun apply(diff: SyncDiff) {
        diff.removed.chunked(500).forEach { delete(it) }
        if (diff.changed.isNotEmpty()) update(diff.changed)
        if (diff.added.isNotEmpty()) insert(diff.added)
    }
}

data class PhotoTime(val id: Long, @ColumnInfo(name = "taken_at") val takenAt: Long)

data class FailedPhoto(
    val id: Long,
    val uri: String,
    val mime: String,
    val width: Int,
    val height: Int,
    @ColumnInfo(name = "indexed_at") val failedAt: Long?,
)

data class SyncDiff(val added: List<Photo>, val changed: List<Photo>, val removed: List<Long>) {
    val isEmpty get() = added.isEmpty() && changed.isEmpty() && removed.isEmpty()
}

/** Failed photos count as finished: they're skipped, not retried, until their pixels change. */
data class IndexProgress(val total: Int, val done: Int, val failed: Int) {
    val finished get() = done + failed
    val pending get() = total - finished
}
