package app.pixely.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface IndexMetaDao {
    @Query("SELECT value FROM index_meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM index_meta WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Upsert
    suspend fun put(meta: IndexMeta)

    @Query("DELETE FROM index_meta WHERE `key` = :key")
    suspend fun clear(key: String)
}

/** Keys in index_meta. A model or detail-level change marks every photo for re-indexing. */
object MetaKey {
    const val MODEL = "model_id"
    const val DETAIL = "detail_level"
    const val SEARCH_SIZE = "search_size"
    const val SCHEMA = "schema_version"
    const val CHARGING_ONLY = "charging_only"
    /** Set while the user has paused indexing; nothing starts it until they resume. */
    const val USER_PAUSED = "user_paused"
    /** Foreground time used in the current 24 h window, against Android's 6 h cap for mediaProcessing. */
    const val BUDGET_WINDOW_START = "budget_window_start"
    const val BUDGET_USED_MS = "budget_used_ms"
    /** Set while indexing waits for the next window; epoch millis. */
    const val RESUME_AT = "resume_at"
    /** Why indexing can't run, e.g. "model_missing"; absent when it can. */
    const val PROBLEM = "index_problem"
    const val DELEGATE = "delegate"
    const val MS_PER_PHOTO = "ms_per_photo"
}
