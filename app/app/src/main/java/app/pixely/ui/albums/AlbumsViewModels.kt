package app.pixely.ui.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pixely.data.albums.AlbumContents
import app.pixely.data.albums.Albums
import app.pixely.data.albums.SmartSet
import app.pixely.data.db.GridPhoto
import app.pixely.data.db.PixelyDatabase
import app.pixely.data.search.ModelMissingException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** An album's name in navigation: a smart set's name, or [DUPLICATES]. */
const val DUPLICATES = "DUPLICATES"

data class AlbumCard(val key: String, val label: String, val count: Int, val groups: Int?, val cover: GridPhoto?)

/** [cards] is null until the first pass is done. */
data class AlbumsState(val cards: List<AlbumCard>? = null, val modelMissing: Boolean = false)

@HiltViewModel
class AlbumsViewModel @Inject constructor(private val db: PixelyDatabase, albums: Albums) : ViewModel() {

    val state: StateFlow<AlbumsState> = albums.observe()
        .map { contents ->
            val covers = db.photoDao().byIds(contents.sets.values.mapNotNull { it.firstOrNull() } + listOfNotNull(contents.duplicates.firstOrNull()?.first()))
                .associateBy { it.id }
            val sets = SmartSet.entries.map { set ->
                val ids = contents.sets.getValue(set)
                AlbumCard(set.name, set.label, ids.size, null, ids.firstOrNull()?.let(covers::get))
            }
            val dupes = AlbumCard(DUPLICATES, "Duplicates", contents.duplicatePhotos, contents.duplicates.size,
                contents.duplicates.firstOrNull()?.first()?.let(covers::get))
            AlbumsState(sets + dupes)
        }
        .catch { if (it is ModelMissingException) emit(AlbumsState(modelMissing = true)) else throw it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumsState())
}

/** A smart set's photos, or the duplicate groups; null while the first pass runs. */
data class AlbumState(val title: String, val photos: List<GridPhoto>? = null, val groups: List<List<GridPhoto>>? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlbumViewModel @Inject constructor(private val db: PixelyDatabase, private val albums: Albums) : ViewModel() {

    private val key = MutableStateFlow<String?>(null)

    val state: StateFlow<AlbumState> = key.filterNotNull()
        .flatMapLatest { key ->
            val title = SmartSet.entries.firstOrNull { it.name == key }?.label ?: "Duplicates"
            albums.observe()
                .map { show(key, title, it) }
                .onStart { emit(AlbumState(title)) }
                .catch { if (it is ModelMissingException) emit(AlbumState(title, photos = emptyList())) else throw it }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumState(""))

    fun start(key: String) {
        this.key.value = key
    }

    private suspend fun show(key: String, title: String, contents: AlbumContents): AlbumState {
        if (key == DUPLICATES) {
            val photos = photos(contents.duplicates.flatten()).associateBy { it.id }
            return AlbumState(title, groups = contents.duplicates.map { g -> g.mapNotNull(photos::get) })
        }
        return AlbumState(title, photos = photos(contents.sets.getValue(SmartSet.valueOf(key))))
    }

    /** In the order of [ids]. */
    private suspend fun photos(ids: List<Long>): List<GridPhoto> {
        val byId = ids.chunked(900).flatMap { db.photoDao().byIds(it) }.associateBy { it.id }
        return ids.mapNotNull(byId::get)
    }
}
