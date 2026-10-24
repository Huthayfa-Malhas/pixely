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
import app.pixely.ui.folders.FoldersScreen
import app.pixely.ui.folders.FoldersViewModel
import app.pixely.ui.licences.LicencesScreen
import app.pixely.ui.indexing.IndexingScreen
import app.pixely.ui.indexing.IndexingViewModel
import app.pixely.ui.photos.GridItem
import app.pixely.ui.photos.LibraryViewModel
import app.pixely.ui.photos.PhotosScreen
import app.pixely.ui.search.PhotoWordsScreen
import app.pixely.ui.search.PhotoWordsViewModel
import app.pixely.ui.search.PickPhotoScreen
import app.pixely.ui.search.PickPhotoViewModel
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

/** The search box's photo button. */
@Serializable data object PickPhoto : NavKey

@Serializable data object Indexing : NavKey

@Serializable data object SettingsKey : NavKey

@Serializable data object Diagnostics : NavKey

@Serializable data object Folders : NavKey

@Serializable data object Licences : NavKey

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
        // Photos is the start tab: back from Search returns to it.
        backStack.clear()
        backStack.add(Photos)
        if (tab == Tab.SEARCH) backStack.add(SearchTab)
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
                    SearchScreen(
                        recent = recent,
                        onSearch = { backStack.add(Results(query = it.trim())) },
                        onPhoto = { backStack.add(PickPhoto) },
                        onSettings = { backStack.add(SettingsKey) },
                    )
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
            entry<PickPhoto> {
                val pickVm: PickPhotoViewModel = hiltViewModel()
                val photos by pickVm.photos.collectAsStateWithLifecycle()
                PickPhotoScreen(
                    photos = photos,
                    onBack = { backStack.removeLastOrNull() },
                    // Back from photo + words returns to Search, not the picker.
                    onPick = { backStack[backStack.lastIndex] = PhotoWords(it.id) },
                )
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
                    onFolders = { backStack.add(Folders) },
                    onLicences = { backStack.add(Licences) },
                )
            }
            entry<Licences> { LicencesScreen(onBack = { backStack.removeLastOrNull() }) }
            entry<Folders> {
                val foldersVm: FoldersViewModel = hiltViewModel()
                val state by foldersVm.state.collectAsStateWithLifecycle()
                FoldersScreen(state = state, onBack = { backStack.removeLastOrNull() }, onSearched = foldersVm::setSearched)
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
