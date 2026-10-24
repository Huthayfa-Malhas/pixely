package app.pixely.data.media

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore.Images.Media
import app.pixely.BuildConfig
import app.pixely.data.db.Photo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** A folder of photos, by MediaStore's RELATIVE_PATH ("DCIM/Camera/"). */
data class Folder(val path: String, val count: Int) {
    val name: String get() = path.trimEnd('/').substringAfterLast('/').ifEmpty { path }
}

/** Reads every image MediaStore lets Pixely see; with partial access that's only the selected ones.
 *  Debug builds only read BuildConfig.TEST_FOLDER. */
class MediaStoreSource @Inject constructor(@ApplicationContext private val context: Context) {

    /** Everything except photos in [skip], folders the user turned off. */
    fun readAll(skip: Set<String> = emptySet()): List<Photo> {
        val photos = ArrayList<Photo>()
        val columns = arrayOf(Media._ID, Media.DATE_TAKEN, Media.DATE_ADDED, Media.DATE_MODIFIED, Media.WIDTH, Media.HEIGHT, Media.MIME_TYPE)
        val (where, args) = where(skip)
        context.contentResolver.query(Media.EXTERNAL_CONTENT_URI, columns, where, args, null)?.use { c ->
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

    /** Every folder with photos Pixely can see, skipped ones too, biggest first. */
    fun folders(): List<Folder> {
        val counts = HashMap<String, Int>()
        val (where, args) = where(emptySet())
        context.contentResolver.query(Media.EXTERNAL_CONTENT_URI, arrayOf(Media.RELATIVE_PATH), where, args, null)?.use { c ->
            while (c.moveToNext()) {
                val path = c.getString(0) ?: continue
                counts[path] = (counts[path] ?: 0) + 1
            }
        }
        return counts.map { (path, n) -> Folder(path, n) }.sortedWith(compareByDescending<Folder> { it.count }.thenBy { it.path })
    }

    private fun where(skip: Set<String>): Pair<String?, Array<String>?> {
        val clauses = ArrayList<String>()
        val args = ArrayList<String>()
        BuildConfig.TEST_FOLDER.takeIf { it.isNotEmpty() }?.let { clauses += "${Media.RELATIVE_PATH} LIKE ?"; args += "$it%" }
        if (skip.isNotEmpty()) {
            clauses += "${Media.RELATIVE_PATH} NOT IN (${skip.joinToString { "?" }})"
            args += skip
        }
        return if (clauses.isEmpty()) null to null else clauses.joinToString(" AND ") to args.toTypedArray()
    }
}
