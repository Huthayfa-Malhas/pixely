package app.pixely.data.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/** The model Pixely indexes with. A different id in index_meta means every photo is indexed again. */
const val MODEL_ID = "embeddinggemma-2-740m"

/**
 * Where the model file lives. Until the Play AI pack exists (it needs the Play Console), the file is
 * copied over adb into the app's external files dir (see docs/install-model.md):
 *   adb push embeddinggemma-2-740m.litertlm /sdcard/Android/data/app.pixely/files/models/
 */
class ModelFiles @Inject constructor(@ApplicationContext private val context: Context) {

    val expected: File get() = File(context.getExternalFilesDir("models"), "$MODEL_ID.litertlm")

    fun find(): File? = expected.takeIf { it.isFile && it.length() > 0 }
}

/** Decoded with the long side at most [maxSide]; the model resizes further itself. */
fun decodeForModel(context: Context, uri: Uri, maxSide: Int = 1024): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val long = maxOf(info.size.width, info.size.height)
        if (long > maxSide) {
            val scale = maxSide.toFloat() / long
            decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
        }
    }.let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false) }
