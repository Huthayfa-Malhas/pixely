package app.pixely.data.search

import app.pixely.data.db.PixelyDatabase
import java.util.PriorityQueue
import javax.inject.Inject
import kotlin.math.sqrt

/** When a photo was taken, as a filter: from inclusive, until exclusive, epoch millis. */
data class DateRange(val from: Long, val until: Long)

/**
 * The three ways to search: words, a photo (find similar), and a photo changed by words. Each ends in
 * the nearest photos by cosine, best first.
 */
class PhotoSearch @Inject constructor(
    private val db: PixelyDatabase,
    private val index: VectorIndex,
    private val queries: QueryEmbedder,
) {
    suspend fun words(query: SearchQuery, dates: DateRange? = null): List<Hit> {
        val hits = nearest(queries.embed(query.words), dates)
        val without = query.without ?: return hits
        return pushDown(hits, queries.embed("a photo of $without"))
    }

    /** Photos like this one; itself left out. */
    suspend fun similar(photoId: Long, dates: DateRange? = null): List<Hit> {
        val photo = index.vectors(listOf(photoId))[photoId] ?: return emptyList()
        return nearest(photo, dates).filter { it.photoId != photoId }
    }

    /** This photo, changed by words: "but in black". The mix is a plain sum, untested so far. */
    suspend fun photoAndWords(photoId: Long, words: String): List<Hit> {
        val photo = index.vectors(listOf(photoId))[photoId] ?: return emptyList()
        if (words.isBlank()) return similar(photoId)
        val text = queries.embed(words)
        return nearest(normalized(FloatArray(photo.size) { photo[it] + WORDS_WEIGHT * text[it] }), null)
            .filter { it.photoId != photoId }
    }

    private suspend fun nearest(query: FloatArray, dates: DateRange?): List<Hit> {
        if (dates == null) return index.nearest(query, LIMIT)
        // Filtered: score every indexed photo in the range exactly, a chunk at a time.
        val top = PriorityQueue<Hit>(LIMIT + 1, compareBy { it.score })
        db.photoDao().indexedBetween(dates.from, dates.until).chunked(1_000).forEach { chunk ->
            index.vectors(chunk).forEach { (id, v) ->
                top += Hit(id, dot(query, v))
                if (top.size > LIMIT) top.poll()
            }
        }
        return top.sortedByDescending { it.score }
    }

    /** Photos that look like the excluded thing sink: on the POC's photos it kept them out of the top 5 for 99.5% of queries. */
    private suspend fun pushDown(hits: List<Hit>, without: FloatArray): List<Hit> {
        val vectors = index.vectors(hits.map { it.photoId })
        return hits.map { hit -> vectors[hit.photoId]?.let { hit.copy(score = hit.score - WITHOUT_WEIGHT * dot(without, it)) } ?: hit }
            .sortedByDescending { it.score }
    }

    private fun normalized(v: FloatArray): FloatArray {
        val n = sqrt(dot(v, v))
        return if (n > 0f) FloatArray(v.size) { v[it] / n } else v
    }

    companion object {
        /** How many photos a search returns. */
        const val LIMIT = 200

        /**
         * Tuned on the POC's 375 photos with "<caption>, without X": 0.5 kept the right photo first 83.2% of
         * the time (84.4% with no push-down) and an X photo out of the top 5 in 99.5% of queries.
         */
        const val WITHOUT_WEIGHT = 0.5f

        const val WORDS_WEIGHT = 1f
    }
}
