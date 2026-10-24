package app.pixely.data.search

import androidx.room3.executeSQL
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import app.pixely.data.db.PixelyDatabase

/** A photo and how close it is to the query: cosine similarity at full size, higher is closer. */
data class Hit(val photoId: Long, val score: Float)

/**
 * Stores each photo's vector and finds the nearest ones. sqlite-vec is the main implementation;
 * [KotlinScanIndex] is the fallback if it can't load, and the escape hatch if it's ever abandoned.
 */
interface VectorIndex {
    /** Stores the full 768 vector (L2-normalised), replacing any earlier one for that photo. */
    suspend fun put(photoId: Long, vector: FloatArray)

    suspend fun remove(photoId: Long)

    /** Removes every stored vector. */
    suspend fun clear()

    /** The stored full-size vectors of those photos that have one. */
    suspend fun vectors(photoIds: Collection<Long>): Map<Long, FloatArray>

    /** The [k] nearest photos to [query] (L2-normalised, full size), best first. */
    suspend fun nearest(query: FloatArray, k: Int = 200): List<Hit>

    /** The size searches run at; one of [SEARCH_SIZES]. */
    suspend fun searchSize(): Int

    /** Switches the search size, rebuilding from the stored vectors. No re-index. */
    suspend fun setSearchSize(n: Int)

    /** Gives back the space that removed vectors still hold, e.g. after many photos are deleted. */
    suspend fun compact()
}

/** Reads stored vectors straight from photo_embedding, in chunks under SQLite's bound-parameter limit. */
internal suspend fun PixelyDatabase.readVectors(photoIds: Collection<Long>): Map<Long, FloatArray> = buildMap {
    photoIds.chunked(900).forEach { chunk ->
        useReaderConnection { c ->
            c.usePrepared("SELECT photo_id, vector FROM photo_embedding WHERE photo_id IN (${chunk.joinToString()})") { st ->
                while (st.step()) put(st.getLong(0), st.getBlob(1).toVector())
            }
        }
    }
}

/** Rewrites the database file without its free pages, so the space goes back to the phone. */
internal suspend fun PixelyDatabase.vacuum() {
    useWriterConnection {
        it.executeSQL("VACUUM")
        // VACUUM writes the whole file through the WAL; truncate it, or the space isn't given back.
        it.executeSQL("PRAGMA wal_checkpoint(TRUNCATE)")
    }
}
