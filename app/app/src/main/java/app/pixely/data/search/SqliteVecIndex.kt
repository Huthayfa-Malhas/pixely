package app.pixely.data.search

import androidx.room3.executeSQL
import androidx.room3.immediateTransaction
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import app.pixely.data.db.IndexMeta
import app.pixely.data.db.MetaKey
import app.pixely.data.db.PixelyDatabase

/**
 * sqlite-vec over photo_search, a vec0 table holding each vector cut to the search size. Below 768
 * the nearest [k] come from the cut vectors and are re-scored against the full ones in photo_embedding.
 */
class SqliteVecIndex(private val db: PixelyDatabase) : VectorIndex {

    private val meta = db.metaDao()

    override suspend fun put(photoId: Long, vector: FloatArray) {
        val n = searchSize()
        db.useWriterConnection { c ->
            c.immediateTransaction {
                usePrepared("INSERT OR REPLACE INTO photo_embedding(photo_id, vector) VALUES (?, ?)") {
                    it.bindLong(1, photoId); it.bindBlob(2, vector.toBlob()); it.step()
                }
                // vec0 has no INSERT OR REPLACE, so any earlier row goes first.
                usePrepared("DELETE FROM photo_search WHERE photo_id = ?") { it.bindLong(1, photoId); it.step() }
                usePrepared("INSERT INTO photo_search(photo_id, embedding) VALUES (?, ?)") {
                    it.bindLong(1, photoId); it.bindBlob(2, vector.truncated(n).toBlob()); it.step()
                }
            }
        }
    }

    override suspend fun remove(photoId: Long) {
        // The trigger on photo_embedding removes the photo_search row too.
        db.useWriterConnection { c ->
            c.usePrepared("DELETE FROM photo_embedding WHERE photo_id = ?") { it.bindLong(1, photoId); it.step() }
        }
    }

    override suspend fun clear() {
        db.useWriterConnection { c ->
            c.immediateTransaction {
                executeSQL("DELETE FROM photo_embedding")
                executeSQL("DELETE FROM photo_search")
            }
        }
    }

    override suspend fun vectors(photoIds: Collection<Long>): Map<Long, FloatArray> = db.readVectors(photoIds)

    override suspend fun nearest(query: FloatArray, k: Int): List<Hit> {
        val n = searchSize()
        return db.useReaderConnection { c ->
            val ids = c.usePrepared("SELECT photo_id, distance FROM photo_search WHERE embedding MATCH ? AND k = ?") { st ->
                st.bindBlob(1, query.truncated(n).toBlob()); st.bindLong(2, k.toLong())
                buildList { while (st.step()) add(Hit(st.getLong(0), 1f - st.getDouble(1).toFloat())) }
            }
            if (n == DIMS || ids.isEmpty()) return@useReaderConnection ids
            c.usePrepared("SELECT photo_id, vector FROM photo_embedding WHERE photo_id IN (${ids.joinToString { "${it.photoId}" }})") { st ->
                buildList { while (st.step()) add(Hit(st.getLong(0), dot(query, st.getBlob(1).toVector()))) }
            }.sortedByDescending { it.score }
        }
    }

    override suspend fun searchSize(): Int = meta.get(MetaKey.SEARCH_SIZE)?.toIntOrNull() ?: DIMS

    override suspend fun setSearchSize(n: Int) {
        require(n in SEARCH_SIZES) { "Unsupported search size $n" }
        rebuild(n)
        meta.put(IndexMeta(MetaKey.SEARCH_SIZE, "$n"))
    }

    /**
     * vec0 stores vectors in fixed blocks of 1,024 and a delete only marks its slot empty, so the
     * blocks stay. Rebuilt from the stored vectors, the table holds only what's left.
     */
    override suspend fun compact() {
        rebuild(searchSize())
        db.vacuum()
    }

    private suspend fun rebuild(n: Int) {
        val source = if (n == DIMS) "vector" else "vec_normalize(vec_slice(vector, 0, $n))"
        db.useWriterConnection { c ->
            c.immediateTransaction {
                executeSQL("DROP TABLE IF EXISTS photo_search")
                executeSQL("CREATE VIRTUAL TABLE photo_search USING vec0(photo_id INTEGER PRIMARY KEY, embedding float[$n] distance_metric=cosine)")
                executeSQL("INSERT INTO photo_search(photo_id, embedding) SELECT photo_id, $source FROM photo_embedding")
            }
        }
    }
}
