package app.pixely

import android.app.Application
import app.pixely.data.media.LibraryWatcher
import app.pixely.ui.thumbs.ThumbnailFetcher
import app.pixely.ui.thumbs.ThumbnailKeyer
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PixelyApp : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var watcher: LibraryWatcher

    override fun onCreate() {
        super.onCreate()
        watcher.start()
    }

    /** Grid cells use MediaStore's cached thumbnails; full photos go through Coil's own decoder. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ThumbnailFetcher.Factory(context))
                add(ThumbnailKeyer)
            }
            .build()
}
