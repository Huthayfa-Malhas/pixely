package app.pixely.data.index

import androidx.room3.useReaderConnection
import androidx.work.ExistingWorkPolicy
import app.pixely.data.db.IndexMeta
import app.pixely.data.db.MetaKey
import app.pixely.data.db.PixelyDatabase
import app.pixely.data.model.MODEL_ID
import app.pixely.data.search.DIMS
import app.pixely.data.search.VectorIndex
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Image tokens per photo: Fine reads small things better, Fast indexes about 1.7x quicker. */
enum class Detail(val tokens: Int) { FINE(140), FAST(70) }

/** The settings that decide how photos are indexed, and what changing each one sets off. */
@Singleton
class IndexSettings @Inject constructor(
    private val db: PixelyDatabase,
    private val index: VectorIndex,
    private val scheduler: IndexScheduler,
) {
    private val meta = db.metaDao()

    fun observeChargingOnly(): Flow<Boolean> = meta.observe(MetaKey.CHARGING_ONLY).map(::chargingOnlyOf)

    fun observeDetail(): Flow<Detail> = meta.observe(MetaKey.DETAIL).map(::detailOf)

    fun observeSearchSize(): Flow<Int> = meta.observe(MetaKey.SEARCH_SIZE).map { it?.toIntOrNull() ?: DIMS }

    suspend fun detail(): Detail = detailOf(meta.get(MetaKey.DETAIL))

    /** The running job keeps its old constraint until restarted, so restart it. */
    suspend fun setChargingOnly(on: Boolean) {
        meta.put(IndexMeta(MetaKey.CHARGING_ONLY, "$on"))
        scheduler.start(ExistingWorkPolicy.REPLACE)
    }

    /** Every photo is indexed again; search keeps using the old vectors until each is replaced. */
    suspend fun setDetail(detail: Detail) {
        if (detail == detail()) return
        meta.put(IndexMeta(MetaKey.DETAIL, "${detail.tokens}"))
        db.photoDao().markAllPending()
        scheduler.start(ExistingWorkPolicy.REPLACE)
    }

    /** Rebuilds photo_search from the stored vectors in seconds; nothing is indexed again. */
    suspend fun setSearchSize(n: Int) {
        if (n != index.searchSize()) index.setSearchSize(n)
    }

    /** Every photo again, failed ones too; search keeps using the old vectors until each is replaced. */
    suspend fun rebuild() {
        db.photoDao().markAllPending()
        scheduler.resume()
    }

    /**
     * Stops indexing and removes every vector, giving the space back. Search finds nothing until the
     * user resumes indexing, which starts again from the newest photo.
     */
    suspend fun deleteIndex() {
        scheduler.pause()
        db.photoDao().markAllPending()
        index.clear()
        index.compact()
    }

    /**
     * Bytes the database's live pages take: vectors, the search table and the photo list. Not the file
     * sizes, which also count the WAL and pages freed by a search-size switch, waiting for reuse.
     */
    suspend fun indexBytes(): Long = db.useReaderConnection { c ->
        suspend fun pragma(name: String) = c.usePrepared("PRAGMA $name") { st -> st.step(); st.getLong(0) }
        (pragma("page_count") - pragma("freelist_count")) * pragma("page_size")
    }

    /** A new model makes every stored vector incompatible with new queries, so all photos go again. */
    suspend fun checkModel() {
        if (meta.get(MetaKey.MODEL) == MODEL_ID) return
        meta.put(IndexMeta(MetaKey.MODEL, MODEL_ID))
        db.photoDao().markAllPending()
    }

    companion object {
        /** Off unless turned on: on battery, indexing stops at [BATTERY_FLOOR]. */
        fun chargingOnlyOf(value: String?): Boolean = value?.toBooleanStrictOrNull() ?: false

        fun detailOf(value: String?): Detail = Detail.entries.firstOrNull { "${it.tokens}" == value } ?: Detail.FINE
    }
}
