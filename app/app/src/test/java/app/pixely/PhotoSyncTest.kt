package app.pixely

import app.pixely.data.db.IndexState
import app.pixely.data.db.Photo
import app.pixely.data.db.SyncKey
import app.pixely.data.media.PhotoSync
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoSyncTest {

    private fun photo(mediaId: Long, modified: Long) =
        Photo(mediaStoreId = mediaId, uri = "content://media/external/images/media/$mediaId", takenAt = 0, width = 1, height = 1, mime = "image/jpeg", dateModified = modified)

    @Test
    fun `new, edited and deleted photos are each found`() {
        val stored = listOf(SyncKey(id = 1, mediaStoreId = 10, dateModified = 100), SyncKey(2, 20, 200), SyncKey(3, 30, 300))
        val current = listOf(photo(10, 100), photo(20, 201), photo(40, 400))

        val diff = PhotoSync.diff(stored, current)

        assertEquals(listOf(40L), diff.added.map { it.mediaStoreId })
        assertEquals(listOf(2L), diff.changed.map { it.id })
        assertEquals(IndexState.PENDING, diff.changed.single().indexState)
        assertEquals(listOf(3L), diff.removed)
    }

    @Test
    fun `an unchanged library gives an empty diff`() {
        val diff = PhotoSync.diff(listOf(SyncKey(1, 10, 100)), listOf(photo(10, 100)))
        assertEquals(true, diff.isEmpty)
    }
}
