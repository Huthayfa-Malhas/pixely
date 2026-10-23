package app.pixely.data.search

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The last few searches, newest first, kept on the phone only. */
@Singleton
class RecentSearches @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("search", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(prefs.getString(KEY, null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList())
    val items: StateFlow<List<String>> = _items.asStateFlow()

    fun add(query: String) {
        val q = query.trim().replace('\n', ' ')
        if (q.isEmpty()) return
        _items.value = (listOf(q) + _items.value.filterNot { it.equals(q, ignoreCase = true) }).take(MAX)
        prefs.edit().putString(KEY, _items.value.joinToString("\n")).apply()
    }

    private companion object {
        const val KEY = "recent"
        const val MAX = 6
    }
}
