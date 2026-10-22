package app.pixely.data.search

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** EmbeddingGemma 2's full vector size. */
const val DIMS = 768

/** The search sizes Settings offers; anything below [DIMS] is re-checked at full size. */
val SEARCH_SIZES = listOf(768, 512, 256, 128)

/** Little-endian float32, the layout sqlite-vec reads from a BLOB. */
fun FloatArray.toBlob(): ByteArray =
    ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { asFloatBuffer().put(this@toBlob) }.array()

fun ByteArray.toVector(): FloatArray =
    FloatArray(size / 4).also { ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it) }

fun dot(a: FloatArray, b: FloatArray): Float {
    var s = 0f
    for (i in a.indices) s += a[i] * b[i]
    return s
}

/** The first [n] numbers, scaled back to length 1 (Matryoshka truncation). */
fun FloatArray.truncated(n: Int): FloatArray {
    if (n >= size) return this
    val out = copyOf(n)
    val norm = sqrt(dot(out, out))
    if (norm > 0f) for (i in out.indices) out[i] /= norm
    return out
}
