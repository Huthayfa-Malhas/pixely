package app.pixely.data.search

import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import app.pixely.data.db.PixelyDatabase
import java.util.PriorityQueue

/**
 * Exact search by reading every stored vector. About 1.5 s per 100k photos on a mid-range phone,
 * so it's the fallback, not the default. Always searches at full size.
 */
class KotlinScanIndex(private val db: PixelyDatabase) : VectorIndex {

    override suspend fun put(photoId: Long, vector: FloatArray) {
        db.useWriterConnection { c ->
            c.usePrepared("INSERT OR REPLACE INTO photo_embedding(photo_id, vector) VALUES (?, ?)") {
                it.bindLong(1, photoId); it.bindBlob(2, vector.toBlob()); it.step()
            }
        }
    }

    override suspend fun remove(photoId: Long) {
        db.useWriterConnection { c ->
            c.usePrepared("DELETE FROM photo_embedding WHERE photo_id = ?") { it.bindLong(1, photoId); it.step() }
        }
    }

    override suspend fun vectors(photoIds: Collection<Long>): Map<Long, FloatArray> = db.readVectors(photoIds)

    override suspend fun nearest(query: FloatArray, k: Int): List<Hit> = db.useReaderConnection { c ->
        val top = PriorityQueue<Hit>(k + 1, compareBy { it.score })
        c.usePrepared("SELECT photo_id, vector FROM photo_embedding") { st ->
            while (st.step()) {
                top += Hit(st.getLong(0), dot(query, st.getBlob(1).toVector()))
                if (top.size > k) top.poll()
            }
        }
        top.sortedByDescending { it.score }
    }

    override suspend fun searchSize(): Int = DIMS

    override suspend fun setSearchSize(n: Int) = Unit
}
