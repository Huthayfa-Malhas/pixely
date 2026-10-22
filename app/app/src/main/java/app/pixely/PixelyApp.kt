package app.pixely

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.pixely.data.db.PixelyDatabase
import app.pixely.data.index.IndexScheduler
import app.pixely.data.index.IndexSettings
import app.pixely.data.media.LibraryWatcher
import app.pixely.ui.thumbs.ThumbnailFetcher
import app.pixely.ui.thumbs.ThumbnailKeyer
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class PixelyApp : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject lateinit var watcher: LibraryWatcher
    @Inject lateinit var settings: IndexSettings
    @Inject lateinit var indexer: IndexScheduler
    @Inject lateinit var db: PixelyDatabase
    @Inject lateinit var workerFactory: HiltWorkerFactory

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        watcher.start()
        // Picks up a model change, and a run that never got queued (first launch, a cleared job).
        scope.launch {
            settings.checkModel()
            if (db.photoDao().pendingCount() > 0) indexer.start()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /** Grid cells use MediaStore's cached thumbnails; full photos go through Coil's own decoder. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ThumbnailFetcher.Factory(context))
                add(ThumbnailKeyer)
            }
            .build()
}
