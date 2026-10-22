package app.pixely.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

enum class IndexState { PENDING, DONE, FAILED }

/** Mirrors one MediaStore image. */
@Entity(
    tableName = "photo",
    indices = [Index(value = ["media_store_id"], unique = true), Index(value = ["taken_at"])],
)
data class Photo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
    val uri: String,
    /** Epoch millis; DATE_TAKEN, or DATE_ADDED when the photo has none. */
    @ColumnInfo(name = "taken_at") val takenAt: Long,
    val width: Int,
    val height: Int,
    val mime: String,
    /** MediaStore DATE_MODIFIED in seconds; a change means the pixels may have changed. */
    @ColumnInfo(name = "date_modified") val dateModified: Long,
    @ColumnInfo(name = "index_state") val indexState: IndexState = IndexState.PENDING,
    @ColumnInfo(name = "indexed_at") val indexedAt: Long? = null,
)

/** The only stored copy of each vector: 768 float32s, 3 KB. Rows go when their photo goes (trigger). */
@Entity(tableName = "photo_embedding")
class PhotoEmbedding(
    @PrimaryKey @ColumnInfo(name = "photo_id") val photoId: Long,
    val vector: ByteArray,
)

@Entity(tableName = "index_meta")
data class IndexMeta(
    @PrimaryKey val key: String,
    val value: String,
)

/** What the grid needs per photo, without the columns it doesn't. */
data class GridPhoto(
    val id: Long,
    val uri: String,
    @ColumnInfo(name = "taken_at") val takenAt: Long,
    val width: Int,
    val height: Int,
)

/** What sync compares against MediaStore. */
data class SyncKey(
    val id: Long,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
    @ColumnInfo(name = "date_modified") val dateModified: Long,
)
