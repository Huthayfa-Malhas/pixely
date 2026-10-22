package app.pixely.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface IndexMetaDao {
    @Query("SELECT value FROM index_meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(meta: IndexMeta)
}

/** Keys in index_meta. A model or detail-level change marks every photo for re-indexing. */
object MetaKey {
    const val MODEL = "model_id"
    const val DETAIL = "detail_level"
    const val SEARCH_SIZE = "search_size"
    const val SCHEMA = "schema_version"
}
