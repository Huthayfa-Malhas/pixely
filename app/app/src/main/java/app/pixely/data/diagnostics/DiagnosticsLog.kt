package app.pixely.data.diagnostics

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What indexing did, kept on the phone for the diagnostics screen and its export: model loads, speed
 * every few photos, failures with their reason, stops. Logcat as well. Never leaves the phone unless
 * the user shares the export.
 */
@Singleton
class DiagnosticsLog @Inject constructor(@ApplicationContext context: Context) {

    private val file = File(context.filesDir, "diagnostics.log")

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        append(tag, message)
    }

    fun w(tag: String, message: String, e: Throwable? = null) {
        Log.w(tag, message, e)
        append(tag, if (e == null) message else "$message: ${e.javaClass.simpleName}: ${e.message}")
    }

    /** Oldest first. */
    @Synchronized
    fun lines(): List<String> = if (file.exists()) file.readLines() else emptyList()

    @Synchronized
    private fun append(tag: String, message: String) {
        try {
            file.appendText("${LocalDateTime.now().format(TIME)} $tag: ${message.replace('\n', ' ')}\n")
            // Over the cap, keep the newer half.
            if (file.length() > MAX_BYTES) {
                val lines = file.readLines()
                file.writeText(lines.takeLast(lines.size / 2).joinToString("\n", postfix = "\n"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't write the diagnostics log", e)
        }
    }

    private companion object {
        const val TAG = "DiagnosticsLog"
        const val MAX_BYTES = 256 * 1024
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
    }
}
