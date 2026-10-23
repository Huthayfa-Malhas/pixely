package app.pixely.data.search

import android.content.Context
import app.pixely.data.index.Detail
import app.pixely.data.model.Embedder
import app.pixely.data.model.ModelFiles
import com.google.mediapipe.tasks.core.Delegate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ModelMissingException : IllegalStateException("The AI model isn't on this phone")

/**
 * The model copy that turns search words into vectors. On the CPU: it loads in about 3 s where the
 * GPU takes 20, and a short query is no slower. Kept warm while searching, let go after a quiet spell.
 */
@Singleton
class QueryEmbedder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: ModelFiles,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private var embedder: Embedder? = null
    private var release: Job? = null

    /** Loads the model ahead of the first query, e.g. when the Search tab opens. */
    suspend fun warm() {
        withModel { }
    }

    suspend fun embed(text: String): FloatArray = withModel { it.query(text) }

    private suspend fun <T> withModel(block: (Embedder) -> T): T = withContext(Dispatchers.Default) {
        lock.withLock {
            release?.cancel()
            val model = embedder ?: Embedder.open(context, models.find() ?: throw ModelMissingException(), Detail.FINE.tokens, Delegate.CPU)
                .also { embedder = it }
            block(model).also {
                release = scope.launch {
                    delay(IDLE_MS)
                    lock.withLock { embedder?.close(); embedder = null }
                }
            }
        }
    }

    private companion object {
        const val IDLE_MS = 2 * 60_000L
    }
}
