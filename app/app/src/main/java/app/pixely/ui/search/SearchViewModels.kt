package app.pixely.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pixely.data.db.GridPhoto
import app.pixely.data.db.PixelyDatabase
import app.pixely.data.search.DateRange
import app.pixely.data.search.Hit
import app.pixely.data.search.ModelMissingException
import app.pixely.data.search.PhotoSearch
import app.pixely.data.search.QueryEmbedder
import app.pixely.data.search.RecentSearches
import app.pixely.data.search.SearchQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Search tab: recent searches, and the model warmed so the first search doesn't wait on it. */
@HiltViewModel
class SearchTabViewModel @Inject constructor(recent: RecentSearches, private val queries: QueryEmbedder) : ViewModel() {
    val recent: StateFlow<List<String>> = recent.items

    fun warm() {
        viewModelScope.launch { runCatching { queries.warm() } }
    }
}

enum class DateFilter(val label: String) {
    ANY("Any date"), DAYS_30("Last 30 days"), THIS_YEAR("This year"), LAST_YEAR("Last year");

    fun range(today: LocalDate, zone: ZoneId): DateRange? {
        fun LocalDate.ms() = atStartOfDay(zone).toInstant().toEpochMilli()
        val tomorrow = today.plusDays(1).ms()
        return when (this) {
            ANY -> null
            DAYS_30 -> DateRange(today.minusDays(29).ms(), tomorrow)
            THIS_YEAR -> DateRange(today.withDayOfYear(1).ms(), tomorrow)
            LAST_YEAR -> DateRange(today.withDayOfYear(1).minusYears(1).ms(), today.withDayOfYear(1).ms())
        }
    }
}

data class ResultsState(
    val input: String = "",
    val query: SearchQuery? = null,
    val similarTo: GridPhoto? = null,
    val dates: DateFilter = DateFilter.ANY,
    val photos: List<GridPhoto> = emptyList(),
    val searching: Boolean = false,
    val modelMissing: Boolean = false,
    /** Photos not searchable yet because indexing hasn't reached them. */
    val notIndexed: Int = 0,
)

/** One Results screen: words, or photos like one photo, with a date filter. */
@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val db: PixelyDatabase,
    private val search: PhotoSearch,
    private val recent: RecentSearches,
) : ViewModel() {
    private val _state = MutableStateFlow(ResultsState())
    val state: StateFlow<ResultsState> = _state.asStateFlow()
    private var job: Job? = null
    private var started = false

    fun start(query: String, similarTo: Long?) {
        if (started) return
        started = true
        if (similarTo == null) return submit(query)
        viewModelScope.launch {
            _state.update { it.copy(similarTo = db.photoDao().byIds(listOf(similarTo)).firstOrNull()) }
            run()
        }
    }

    fun submit(input: String) {
        if (input.isBlank()) return
        recent.add(input)
        _state.update { it.copy(input = input.trim(), query = SearchQuery.parse(input), similarTo = null) }
        run()
    }

    /** The "Without" chip's ×: search the words alone. */
    fun removeWithout() {
        val words = _state.value.query?.text ?: return
        _state.update { it.copy(input = words, query = SearchQuery(words)) }
        run()
    }

    fun setDates(dates: DateFilter) {
        _state.update { it.copy(dates = dates) }
        run()
    }

    private fun run() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(searching = true) }
            val s = _state.value
            val range = s.dates.range(LocalDate.now(), ZoneId.systemDefault())
            try {
                val hits = when {
                    s.similarTo != null -> search.similar(s.similarTo.id, range)
                    s.query != null -> search.words(s.query, range)
                    else -> emptyList()
                }
                val photos = db.photosFor(hits)
                _state.update { it.copy(photos = photos, searching = false, modelMissing = false, notIndexed = db.photoDao().pendingCount()) }
            } catch (e: ModelMissingException) {
                _state.update { it.copy(photos = emptyList(), searching = false, modelMissing = true) }
            }
        }
    }
}

data class PhotoWordsState(
    val photo: GridPhoto? = null,
    val words: String = "",
    val photos: List<GridPhoto> = emptyList(),
    val searching: Boolean = false,
    val modelMissing: Boolean = false,
)

/** "Search with this photo": the photo, changed by words as they're typed. */
@HiltViewModel
class PhotoWordsViewModel @Inject constructor(private val db: PixelyDatabase, private val search: PhotoSearch) : ViewModel() {
    private val _state = MutableStateFlow(PhotoWordsState())
    val state: StateFlow<PhotoWordsState> = _state.asStateFlow()
    private var job: Job? = null

    fun start(photoId: Long) {
        if (_state.value.photo != null) return
        viewModelScope.launch {
            _state.update { it.copy(photo = db.photoDao().byIds(listOf(photoId)).firstOrNull()) }
            run(0)
        }
    }

    /** Typing searches once it pauses; a suggestion searches straight away. */
    fun setWords(words: String, now: Boolean = false) {
        _state.update { it.copy(words = words) }
        run(if (now) 0 else TYPING_PAUSE_MS)
    }

    private fun run(after: Long) {
        val photo = _state.value.photo ?: return
        job?.cancel()
        job = viewModelScope.launch {
            delay(after)
            _state.update { it.copy(searching = true) }
            try {
                val photos = db.photosFor(search.photoAndWords(photo.id, _state.value.words))
                _state.update { it.copy(photos = photos, searching = false, modelMissing = false) }
            } catch (e: ModelMissingException) {
                _state.update { it.copy(photos = emptyList(), searching = false, modelMissing = true) }
            }
        }
    }

    private companion object {
        const val TYPING_PAUSE_MS = 500L
    }
}

/** The hits' photos, best first. */
private suspend fun PixelyDatabase.photosFor(hits: List<Hit>): List<GridPhoto> {
    val byId = hits.map { it.photoId }.chunked(900).flatMap { photoDao().byIds(it) }.associateBy { it.id }
    return hits.mapNotNull { byId[it.photoId] }
}
