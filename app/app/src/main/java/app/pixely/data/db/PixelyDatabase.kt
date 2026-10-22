package app.pixely.data.db

import android.content.Context
import android.util.Log
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.pixely.data.model.MODEL_ID
import app.pixely.data.search.DIMS
import kotlinx.coroutines.Dispatchers

@Database(entities = [Photo::class, PhotoEmbedding::class, IndexMeta::class], version = 1, exportSchema = false)
abstract class PixelyDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun metaDao(): IndexMetaDao

    companion object {
        private const val NAME = "pixely.db"
        private const val TAG = "PixelyDb"

        /**
         * Room 3 on the bundled driver. sqlite-vec is loaded on every connection when it works on this
         * device; [vecLoaded] false means search falls back to the Kotlin scan.
         */
        fun open(context: Context): Opened {
            val extension = "${context.applicationInfo.nativeLibraryDir}/libsqlite_vec.so"
            val vecLoaded = probe(extension)
            val driver = BundledSQLiteDriver().apply { if (vecLoaded) addExtension(extension, ENTRY_POINT) }
            val db = Room.databaseBuilder(context, PixelyDatabase::class.java, NAME)
                .setDriver(driver)
                .setQueryCoroutineContext(Dispatchers.IO)
                .addCallback(Setup(vecLoaded))
                .build()
            return Opened(db, vecLoaded)
        }

        private const val ENTRY_POINT = "sqlite3_vec_init"

        /** Loads the extension into a throwaway in-memory database, so a bad .so can't break the real one. */
        private fun probe(extension: String): Boolean = try {
            BundledSQLiteDriver().apply { addExtension(extension, ENTRY_POINT) }.open(":memory:").use { c ->
                c.prepare("SELECT vec_version()").use { it.step() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "sqlite-vec unavailable, using the Kotlin scan", e)
            false
        }
    }

    class Opened(val db: PixelyDatabase, val vecLoaded: Boolean)

    /** Things Room can't declare: defaults, clean-up triggers, and the sqlite-vec table. */
    private class Setup(private val vecLoaded: Boolean) : Callback() {
        override suspend fun onCreate(connection: SQLiteConnection) {
            connection.execSQL("INSERT INTO index_meta(`key`, value) VALUES ('${MetaKey.MODEL}', '$MODEL_ID')")
            connection.execSQL("INSERT INTO index_meta(`key`, value) VALUES ('${MetaKey.DETAIL}', '140')")
            connection.execSQL("INSERT INTO index_meta(`key`, value) VALUES ('${MetaKey.SEARCH_SIZE}', '$DIMS')")
            connection.execSQL("INSERT INTO index_meta(`key`, value) VALUES ('${MetaKey.SCHEMA}', '1')")
            connection.execSQL(
                "CREATE TRIGGER photo_embedding_on_delete AFTER DELETE ON photo BEGIN " +
                    "DELETE FROM photo_embedding WHERE photo_id = OLD.id; END"
            )
            // New pixels need a new vector; index_state goes back to pending in the same UPDATE.
            connection.execSQL(
                "CREATE TRIGGER photo_embedding_on_change AFTER UPDATE OF date_modified ON photo " +
                    "WHEN OLD.date_modified != NEW.date_modified BEGIN " +
                    "DELETE FROM photo_embedding WHERE photo_id = OLD.id; END"
            )
        }

        override suspend fun onOpen(connection: SQLiteConnection) {
            if (!vecLoaded) return
            // Created here rather than in onCreate so a device that gains sqlite-vec later still gets it.
            connection.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS photo_search USING vec0(" +
                    "photo_id INTEGER PRIMARY KEY, embedding float[$DIMS] distance_metric=cosine)"
            )
            connection.execSQL(
                "CREATE TRIGGER IF NOT EXISTS photo_search_on_embedding_delete AFTER DELETE ON photo_embedding BEGIN " +
                    "DELETE FROM photo_search WHERE photo_id = OLD.photo_id; END"
            )
        }
    }
}
