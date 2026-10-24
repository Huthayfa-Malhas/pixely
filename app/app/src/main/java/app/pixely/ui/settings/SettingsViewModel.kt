package app.pixely.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pixely.data.db.IndexProgress
import app.pixely.data.db.PixelyDatabase
import app.pixely.data.index.Detail
import app.pixely.data.index.IndexSettings
import app.pixely.data.search.DIMS
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsState(
    val progress: IndexProgress = IndexProgress(0, 0, 0),
    /** What the database holds: vectors, the search table and the photo list. */
    val indexBytes: Long = 0,
    val chargingOnly: Boolean = false,
    val detail: Detail = Detail.FINE,
    val searchSize: Int = DIMS,
    /** A search-size switch or a delete is under way; the options wait for it. */
    val busy: Boolean = false,
    /** What the last change set off, in a line. */
    val note: String? = null,
    /** Folders switched off in Settings → Folders. */
    val skippedFolders: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    db: PixelyDatabase,
    private val settings: IndexSettings,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val note = MutableStateFlow<String?>(null)

    private val progress = db.photoDao().observeProgress()

    // Re-measured whenever a photo is indexed or the index changes.
    private val indexBytes = combine(progress, busy) { _, _ -> settings.indexBytes() }

    val state: StateFlow<SettingsState> = combine(
        combine(progress, indexBytes, ::Pair),
        settings.observeChargingOnly(),
        settings.observeDetail(),
        settings.observeSearchSize(),
        combine(busy, note, settings.observeSkippedFolders(), ::Triple),
    ) { (progress, bytes), chargingOnly, detail, size, (busy, note, skipped) ->
        SettingsState(progress, bytes, chargingOnly, detail, size, busy, note, skipped.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun setChargingOnly(on: Boolean) {
        viewModelScope.launch { settings.setChargingOnly(on) }
    }

    fun setDetail(detail: Detail) {
        if (detail == state.value.detail) return
        viewModelScope.launch {
            settings.setDetail(detail)
            note.value = "Re-indexing at ${detail.label} detail in the background. Search keeps working."
        }
    }

    fun setSearchSize(n: Int) {
        if (n == state.value.searchSize || busy.value) return
        work("Search table rebuilt at ${sizeLabel(n).lowercase()} from stored vectors. No photos re-indexed.") {
            settings.setSearchSize(n)
        }
    }

    fun rebuild() {
        viewModelScope.launch {
            settings.rebuild()
            note.value = "Rebuilding the index from scratch in the background. Search keeps working."
        }
    }

    fun deleteIndex() {
        work("Index deleted. Indexing is paused until you resume it on the Indexing screen.") { settings.deleteIndex() }
    }

    private fun work(done: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            try {
                block()
                note.value = done
            } finally {
                busy.value = false
            }
        }
    }
}

val Detail.label: String get() = when (this) {
    Detail.FINE -> "Fine"
    Detail.FAST -> "Standard"
}

fun sizeLabel(n: Int): String = if (n == DIMS) "Full" else "$n"
