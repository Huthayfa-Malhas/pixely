package app.pixely.ui.thumbs

import android.content.Context
import android.net.Uri
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options

/** A grid-sized image of a MediaStore photo. */
data class Thumbnail(val uri: String, val px: Int = GRID_PX) {
    companion object {
        /** A quarter of a 1440 px-wide screen, so one size serves every phone. */
        const val GRID_PX = 360
    }
}

/**
 * Asks MediaStore for the thumbnail it already keeps, which is much cheaper than decoding the
 * full photo for every cell as the grid scrolls.
 */
class ThumbnailFetcher(private val context: Context, private val data: Thumbnail) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bitmap = context.contentResolver.loadThumbnail(Uri.parse(data.uri), Size(data.px, data.px), null)
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory(private val context: Context) : Fetcher.Factory<Thumbnail> {
        override fun create(data: Thumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbnailFetcher(context, data)
    }
}

/** Lets Coil keep thumbnails in its memory cache, so scrolling back doesn't refetch them. */
object ThumbnailKeyer : Keyer<Thumbnail> {
    override fun key(data: Thumbnail, options: Options): String = "${data.uri}@${data.px}"
}
