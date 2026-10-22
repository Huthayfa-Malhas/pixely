package app.pixely.data.db

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

    /** One transaction so the grid never sees a half-applied sync. */
    @Transaction
    open suspend fun apply(diff: SyncDiff) {
        diff.removed.chunked(500).forEach { delete(it) }
        if (diff.changed.isNotEmpty()) update(diff.changed)
        if (diff.added.isNotEmpty()) insert(diff.added)
    }
}

data class SyncDiff(val added: List<Photo>, val changed: List<Photo>, val removed: List<Long>) {
    val isEmpty get() = added.isEmpty() && changed.isEmpty() && removed.isEmpty()
}
