package app.pixely.data.media

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore.Images.Media
import app.pixely.BuildConfig
import app.pixely.data.db.Photo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Reads every image MediaStore lets Pixely see; with partial access that's only the selected ones.
 *  Debug builds only read BuildConfig.TEST_FOLDER. */
class MediaStoreSource @Inject constructor(@ApplicationContext private val context: Context) {

    fun readAll(): List<Photo> {
        val photos = ArrayList<Photo>()
        val columns = arrayOf(Media._ID, Media.DATE_TAKEN, Media.DATE_ADDED, Media.DATE_MODIFIED, Media.WIDTH, Media.HEIGHT, Media.MIME_TYPE)
        val folder = BuildConfig.TEST_FOLDER.takeIf { it.isNotEmpty() }
        val selection = folder?.let { "${Media.RELATIVE_PATH} LIKE ?" }
        val args = folder?.let { arrayOf("$it%") }
        context.contentResolver.query(Media.EXTERNAL_CONTENT_URI, columns, selection, args, null)?.use { c ->
            val id = c.getColumnIndexOrThrow(Media._ID)
            val taken = c.getColumnIndexOrThrow(Media.DATE_TAKEN)
            val added = c.getColumnIndexOrThrow(Media.DATE_ADDED)
            val modified = c.getColumnIndexOrThrow(Media.DATE_MODIFIED)
            val width = c.getColumnIndexOrThrow(Media.WIDTH)
            val height = c.getColumnIndexOrThrow(Media.HEIGHT)
            val mime = c.getColumnIndexOrThrow(Media.MIME_TYPE)
            while (c.moveToNext()) {
                val mediaId = c.getLong(id)
                photos += Photo(
                    mediaStoreId = mediaId,
                    uri = ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, mediaId).toString(),
                    takenAt = if (c.isNull(taken) || c.getLong(taken) <= 0) c.getLong(added) * 1000 else c.getLong(taken),
                    width = c.getInt(width),
                    height = c.getInt(height),
                    mime = c.getString(mime) ?: "image/*",
                    dateModified = c.getLong(modified),
                )
            }
        }
        return photos
    }
}
