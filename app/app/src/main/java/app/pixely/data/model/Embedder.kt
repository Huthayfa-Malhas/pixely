package app.pixely.data.model

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.EmbeddingResult
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedder
import com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedderOptions
import java.io.File

/** Prompts from the model's sentence-transformers config, as the Mac POC and the benchmark used. */
const val QUERY_PROMPT = "task: search result | query: "
const val DOCUMENT_PROMPT = "title: none | text: "

/**
 * EmbeddingGemma 2 through MediaPipe's UniversalEmbedder, text and vision towers only. Week 1 found
 * the NPU can't load the model (the audio tower has ops it can't run), so this is GPU with a CPU
 * fallback. Not thread safe: callers serialize access.
 */
class Embedder private constructor(private val engine: UniversalEmbedder, val delegate: Delegate) : AutoCloseable {

    fun query(text: String): FloatArray = engine.embedText(QUERY_PROMPT + text).vector()

    /** A photo after the document prompt, as sentence-transformers does with prompt_name="document". */
    fun photo(bitmap: Bitmap): FloatArray =
        engine.embedContent(listOf<Any>(DOCUMENT_PROMPT, BitmapImageBuilder(bitmap).build())).vector()

    override fun close() = engine.close()

    private fun EmbeddingResult.vector(): FloatArray = embeddings().first().floatEmbedding()

    companion object {
        private const val TAG = "Embedder"

        /** Week 1: 256 tokens of text input saves about 1 GB over the model's default at the same speed. */
        private const val MAX_INPUT_LENGTH = 256

        /** [detail] is image tokens per photo: 140 (Fine) or 70 (Fast). Loading takes about 20 s on the GPU, 3 s on the CPU. */
        fun open(context: Context, model: File, detail: Int, delegate: Delegate = Delegate.GPU): Embedder =
            if (delegate == Delegate.CPU) Embedder(create(context, model, detail, Delegate.CPU), Delegate.CPU)
            else try {
                Embedder(create(context, model, detail, Delegate.GPU), Delegate.GPU)
            } catch (e: Exception) {
                Log.w(TAG, "GPU unavailable, using the CPU (about 3x slower)", e)
                Embedder(create(context, model, detail, Delegate.CPU), Delegate.CPU)
            }

        private fun create(context: Context, model: File, detail: Int, delegate: Delegate): UniversalEmbedder =
            ParcelFileDescriptor.open(model, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                val options = UniversalEmbedderOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetFileDescriptor(pfd.fd).setDelegate(delegate).build())
                    .setTextDelegate(delegate)
                    .setVisionDelegate(delegate)
                    .setVisionTokensPerImage(detail)
                    .setMaxInputLength(MAX_INPUT_LENGTH)
                    .setL2Normalize(true)
                    .setCacheDir(context.cacheDir.absolutePath)
                    .build()
                UniversalEmbedder.createFromOptions(context, options)
            }
    }
}
