package app.pixely.data.index

import androidx.work.ExistingWorkPolicy
import app.pixely.data.db.IndexMeta
import app.pixely.data.db.MetaKey
import app.pixely.data.db.PixelyDatabase
import app.pixely.data.model.MODEL_ID
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

    fun observeChargingOnly(): Flow<Boolean> = meta.observe(MetaKey.CHARGING_ONLY).map { it?.toBooleanStrictOrNull() ?: true }

    fun observeDetail(): Flow<Detail> = meta.observe(MetaKey.DETAIL).map(::detailOf)

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

    /** A new model makes every stored vector incompatible with new queries, so all photos go again. */
    suspend fun checkModel() {
        if (meta.get(MetaKey.MODEL) == MODEL_ID) return
        meta.put(IndexMeta(MetaKey.MODEL, MODEL_ID))
        db.photoDao().markAllPending()
    }

    companion object {
        fun detailOf(value: String?): Detail = Detail.entries.firstOrNull { "${it.tokens}" == value } ?: Detail.FINE
    }
}
