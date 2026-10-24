package app.pixely.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.pixely.data.db.GridPhoto
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.pixely.data.media.PhotoAccess
import app.pixely.ui.albums.AlbumScreen
import app.pixely.ui.albums.AlbumViewModel
import app.pixely.ui.albums.AlbumsScreen
import app.pixely.ui.albums.AlbumsViewModel
import app.pixely.ui.indexing.IndexingScreen
import app.pixely.ui.indexing.IndexingViewModel
import app.pixely.ui.photos.GridItem
import app.pixely.ui.photos.LibraryViewModel
import app.pixely.ui.photos.PhotosScreen
import app.pixely.ui.search.PhotoWordsScreen
import app.pixely.ui.search.PhotoWordsViewModel
import app.pixely.ui.search.ResultsScreen
import app.pixely.ui.search.ResultsViewModel
import app.pixely.ui.search.SearchScreen
import app.pixely.ui.search.SearchTabViewModel
import app.pixely.ui.diagnostics.DiagnosticsScreen
import app.pixely.ui.diagnostics.DiagnosticsViewModel
import app.pixely.ui.diagnostics.shareDiagnostics
import app.pixely.ui.settings.SettingsScreen
import app.pixely.ui.settings.SettingsViewModel
import app.pixely.ui.viewer.ViewerScreen
import app.pixely.ui.welcome.WelcomeScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object Welcome : NavKey

@Serializable data object Photos : NavKey

/** [list] names the search results it was opened from; none means the whole library. */
@Serializable data class Viewer(val photoId: Long, val list: String? = null) : NavKey

@Serializable data object SearchTab : NavKey

/** Words, or photos like [similarTo]. */
@Serializable data class Results(val query: String = "", val similarTo: Long? = null) : NavKey

@Serializable data class PhotoWords(val photoId: Long) : NavKey

@Serializable data object Indexing : NavKey

@Serializable data object SettingsKey : NavKey

@Serializable data object Diagnostics : NavKey

@Serializable data object AlbumsTab : NavKey

/** A smart set by its name, or Duplicates. */
@Serializable data class Album(val key: String) : NavKey

@Composable
fun PixelyNav(vm: LibraryViewModel = hiltViewModel(), indexingVm: IndexingViewModel = hiltViewModel()) {
    val access by vm.access.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val indexing by indexingVm.state.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(if (access == PhotoAccess.NONE) Welcome else Photos)
    val gridState = rememberLazyGridState()
    val activity = LocalActivity.current
    var denied by rememberSaveable { mutableStateOf(false) }
    // Each results screen's photos, for the viewer to swipe through in that order.
    val lists = remember { mutableStateMapOf<String, List<GridPhoto>>() }

    fun select(tab: Tab) {
        // Photos is the start tab: back from the others returns to it.
        backStack.clear()
        backStack.add(Photos)
        when (tab) {
            Tab.PHOTOS -> Unit
            Tab.SEARCH -> backStack.add(SearchTab)
            Tab.ALBUMS -> backStack.add(AlbumsTab)
        }
    }

    val askForPhotos = rememberLauncherForActivityResult(RequestMultiplePermissions()) {
        vm.onAccessChanged()
        // No access and no rationale means "don't ask again": only system settings can change it now.
        denied = vm.access.value == PhotoAccess.NONE &&
            activity != null && PhotoAccess.permissions.none(activity::shouldShowRequestPermissionRationale)
    }

    // Access can change outside the app (system settings, the partial-access picker), so re-check on return.
    LifecycleResumeEffect(Unit) {
        vm.onAccessChanged()
        onPauseOrDispose {}
    }

    LaunchedEffect(access) {
        // First run: show the first index over the grid, as the design does; "Start searching" drops to the grid.
        if (access != PhotoAccess.NONE && backStack.lastOrNull() == Welcome) {
            backStack.add(Photos)
            backStack.add(Indexing)
            backStack.remove(Welcome)
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<Welcome> {
                WelcomeScreen(
                    denied = denied,
                    onAllow = { askForPhotos.launch(PhotoAccess.permissions) },
                    onOpenSettings = {
                        activity?.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null))
                        )
                    },
                )
            }
            entry<Photos> {
                Tabbed(Tab.PHOTOS, ::select) {
                PhotosScreen(
                    library = library,
                    access = access,
                    indexing = indexing,
                    gridState = gridState,
                    onOpen = { index -> backStack.add(Viewer(library.photos[index].id)) },
                    onChangeSelection = { askForPhotos.launch(PhotoAccess.permissions) },
                    onIndexing = { backStack.add(Indexing) },
                    onSettings = { backStack.add(SettingsKey) },
                )
                }
            }
            entry<SearchTab> {
                val searchVm: SearchTabViewModel = hiltViewModel()
                val recent by searchVm.recent.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { searchVm.warm() }
                Tabbed(Tab.SEARCH, ::select) {
                    SearchScreen(recent = recent, onSearch = { backStack.add(Results(query = it.trim())) }, onSettings = { backStack.add(SettingsKey) })
                }
            }
            entry<Results> { key ->
                val resultsVm: ResultsViewModel = hiltViewModel()
                val state by resultsVm.state.collectAsStateWithLifecycle()
                val list = key.toString()
                LaunchedEffect(key) { resultsVm.start(key.query, key.similarTo) }
                LaunchedEffect(state.photos) { lists[list] = state.photos }
                Tabbed(Tab.SEARCH, ::select) {
                    ResultsScreen(
                        state = state,
                        onBack = { backStack.removeLastOrNull() },
                        onSubmit = resultsVm::submit,
                        onRemoveWithout = resultsVm::removeWithout,
                        onDates = resultsVm::setDates,
                        onOpen = { i -> backStack.add(Viewer(state.photos[i].id, list)) },
                    )
                }
            }
            entry<PhotoWords> { key ->
                val wordsVm: PhotoWordsViewModel = hiltViewModel()
                val state by wordsVm.state.collectAsStateWithLifecycle()
                val list = key.toString()
                LaunchedEffect(key) { wordsVm.start(key.photoId) }
                LaunchedEffect(state.photos) { lists[list] = state.photos }
                PhotoWordsScreen(
                    state = state,
                    onClose = { backStack.removeLastOrNull() },
                    onWords = wordsVm::setWords,
                    onOpen = { i -> backStack.add(Viewer(state.photos[i].id, list)) },
                )
            }
            entry<Indexing> {
                // Progress is in the notification while indexing runs; asked here, where the reason is on screen.
                val askForNotifications = rememberLauncherForActivityResult(RequestPermission()) {}
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                IndexingScreen(
                    state = indexing,
                    onChargingOnly = indexingVm::setChargingOnly,
                    onPause = indexingVm::setPaused,
                    onDone = { backStack.removeLastOrNull() },
                )
            }
            entry<AlbumsTab> {
                val albumsVm: AlbumsViewModel = hiltViewModel()
                val state by albumsVm.state.collectAsStateWithLifecycle()
                Tabbed(Tab.ALBUMS, ::select) {
                    AlbumsScreen(state = state, onOpen = { backStack.add(Album(it)) }, onSettings = { backStack.add(SettingsKey) })
                }
            }
            entry<Album> { key ->
                val albumVm: AlbumViewModel = hiltViewModel()
                val state by albumVm.state.collectAsStateWithLifecycle()
                LaunchedEffect(key) { albumVm.start(key.key) }
                // One viewer list per smart set, and one per duplicate group.
                LaunchedEffect(state) {
                    state.photos?.let { lists["album:${key.key}"] = it }
                    state.groups?.forEachIndexed { g, photos -> lists["album:${key.key}:$g"] = photos }
                }
                AlbumScreen(
                    state = state,
                    onBack = { backStack.removeLastOrNull() },
                    onOpen = { g, i ->
                        val list = if (state.groups != null) "album:${key.key}:$g" else "album:${key.key}"
                        lists[list]?.getOrNull(i)?.let { backStack.add(Viewer(it.id, list)) }
                    },
                )
            }
            entry<SettingsKey> {
                val settingsVm: SettingsViewModel = hiltViewModel()
                val state by settingsVm.state.collectAsStateWithLifecycle()
                SettingsScreen(
                    state = state,
                    onBack = { backStack.removeLastOrNull() },
                    onChargingOnly = settingsVm::setChargingOnly,
                    onDetail = settingsVm::setDetail,
                    onSearchSize = settingsVm::setSearchSize,
                    onRebuild = settingsVm::rebuild,
                    onDelete = settingsVm::deleteIndex,
                    onDiagnostics = { backStack.add(Diagnostics) },
                )
            }
            entry<Diagnostics> {
                val diagnosticsVm: DiagnosticsViewModel = hiltViewModel()
                val report by diagnosticsVm.report.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                DiagnosticsScreen(
                    report = report,
                    onBack = { backStack.removeLastOrNull() },
                    onShare = { scope.launch { shareDiagnostics(context, diagnosticsVm.export()) } },
                )
            }
            entry<Viewer> { key ->
                val fromResults = key.list?.let { lists[it] }
                val photos = fromResults ?: library.photos
                ViewerScreen(
                    photos = photos,
                    startIndex = photos.indexOfFirst { it.id == key.photoId }.coerceAtLeast(0),
                    onBack = { backStack.removeLastOrNull() },
                    onSimilar = { backStack.add(Results(similarTo = it)) },
                    onWords = { backStack.add(PhotoWords(it)) },
                    onPage = { page ->
                        if (fromResults != null) return@ViewerScreen
                        // Keep the grid on the photo being viewed, so going back lands on it.
                        val item = library.items.indexOfFirst { it is GridItem.Cell && it.index == page }
                        val visible = gridState.layoutInfo.visibleItemsInfo
                        if (item >= 0 && visible.none { it.index == item }) gridState.requestScrollToItem(item)
                    },
                )
            }
        },
    )
}

/** A tab's screen above the bottom navigation. */
@Composable
private fun Tabbed(tab: Tab, onSelect: (Tab) -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { content() }
        TabBar(tab, onSelect)
    }
}
