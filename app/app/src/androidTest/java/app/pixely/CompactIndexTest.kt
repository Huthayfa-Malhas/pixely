package app.pixely

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.pixely.data.db.PixelyDatabase
import app.pixely.data.search.KotlinScanIndex
import app.pixely.data.search.SqliteVecIndex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Compacts the phone's real index: the file shrinks, and search still finds the same photos. */
@RunWith(AndroidJUnit4::class)
class CompactIndexTest {

    @Test
    fun compact() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val opened = PixelyDatabase.open(context)
        val index = if (opened.vecLoaded) SqliteVecIndex(opened.db) else KotlinScanIndex(opened.db)
        val file = context.getDatabasePath("pixely.db")
        fun bytes() = listOf("", "-wal").sumOf { context.getDatabasePath("pixely.db$it").length() }

        // Any stored vector makes a query whose nearest photo is itself.
        val probe = index.vectors(listOf(index.nearest(FloatArray(768) { 1f / 768 }, 1).first().photoId)).entries.first()
        val before = bytes()
        val t = System.currentTimeMillis()
        index.compact()
        val ms = System.currentTimeMillis() - t
        val after = bytes()
        Log.i(TAG, "Compacted in $ms ms: ${before / 1_000_000} MB → ${after / 1_000_000} MB (${file.name})")

        assertTrue(after <= before)
        assertEquals(probe.key, index.nearest(probe.value, 1).first().photoId)
        opened.db.close()
    }

    private companion object {
        const val TAG = "CompactIndexTest"
    }
}
