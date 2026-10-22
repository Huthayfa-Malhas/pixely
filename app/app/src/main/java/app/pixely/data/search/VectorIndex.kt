package app.pixely.data.search

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

    /** The [k] nearest photos to [query] (L2-normalised, full size), best first. */
    suspend fun nearest(query: FloatArray, k: Int = 200): List<Hit>

    /** The size searches run at; one of [SEARCH_SIZES]. */
    suspend fun searchSize(): Int

    /** Switches the search size, rebuilding from the stored vectors. No re-index. */
    suspend fun setSearchSize(n: Int)
}
