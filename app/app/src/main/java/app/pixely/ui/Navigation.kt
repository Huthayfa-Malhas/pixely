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
import androidx.compose.runtime.Composable
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
import app.pixely.ui.indexing.IndexingScreen
import app.pixely.ui.indexing.IndexingViewModel
import app.pixely.ui.photos.GridItem
import app.pixely.ui.photos.LibraryViewModel
import app.pixely.ui.photos.PhotosScreen
import app.pixely.ui.viewer.ViewerScreen
import app.pixely.ui.welcome.WelcomeScreen
import kotlinx.serialization.Serializable

@Serializable data object Welcome : NavKey

@Serializable data object Photos : NavKey

@Serializable data class Viewer(val photoId: Long) : NavKey

@Serializable data object Indexing : NavKey

@Composable
fun PixelyNav(vm: LibraryViewModel = hiltViewModel(), indexingVm: IndexingViewModel = hiltViewModel()) {
    val access by vm.access.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val indexing by indexingVm.state.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(if (access == PhotoAccess.NONE) Welcome else Photos)
    val gridState = rememberLazyGridState()
    val activity = LocalActivity.current
    var denied by rememberSaveable { mutableStateOf(false) }

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
                PhotosScreen(
                    library = library,
                    access = access,
                    indexing = indexing,
                    gridState = gridState,
                    onOpen = { index -> backStack.add(Viewer(library.photos[index].id)) },
                    onChangeSelection = { askForPhotos.launch(PhotoAccess.permissions) },
                    onIndexing = { backStack.add(Indexing) },
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
            entry<Viewer> { key ->
                val photos = library.photos
                ViewerScreen(
                    photos = photos,
                    startIndex = photos.indexOfFirst { it.id == key.photoId }.coerceAtLeast(0),
                    onBack = { backStack.removeLastOrNull() },
                    onPage = { page ->
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
