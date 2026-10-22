package app.pixely.ui.photos

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pixely.data.db.GridPhoto
import app.pixely.data.db.PixelyDatabase
import app.pixely.data.media.LibraryWatcher
import app.pixely.data.media.PhotoAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The library as the Photos grid and the viewer both see it. */
data class Library(val photos: List<GridPhoto>, val items: List<GridItem>, val loaded: Boolean)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    db: PixelyDatabase,
    private val watcher: LibraryWatcher,
) : ViewModel() {

    private val _access = MutableStateFlow(PhotoAccess.current(context))
    val access: StateFlow<PhotoAccess> = _access.asStateFlow()

    val library: StateFlow<Library> = db.photoDao().observeGrid()
        .map { photos -> Library(photos, groupByDay(photos, LocalDate.now(), ZoneId.systemDefault()), loaded = true) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Library(emptyList(), emptyList(), loaded = false))

    /** After a permission prompt, whatever the answer. */
    fun onAccessChanged() {
        _access.value = PhotoAccess.current(context)
        watcher.refresh()
    }
}
