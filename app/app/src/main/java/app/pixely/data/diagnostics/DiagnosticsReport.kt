package app.pixely.data.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import app.pixely.BuildConfig
import app.pixely.data.db.MetaKey
import app.pixely.data.db.PixelyDatabase
import app.pixely.data.index.Detail
import app.pixely.data.index.DeviceUse
import app.pixely.data.index.IndexBudget
import app.pixely.data.index.IndexSettings
import app.pixely.data.model.MODEL_ID
import app.pixely.data.model.ModelFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class Section(val title: String, val rows: List<Pair<String, String>>)

/** A photo indexing gave up on, and why if the log still says. */
data class Failure(val line: String, val reason: String?)

/** [events] is the whole log, oldest first. */
data class Report(val sections: List<Section>, val failures: List<Failure>, val failedCount: Int, val events: List<String>) {

    /** What the export holds: everything on the screen, the full log, nothing about what's in the photos. */
    fun text(): String = buildString {
        appendLine("Pixely diagnostics · ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")
        for (s in sections) {
            appendLine()
            appendLine("## ${s.title}")
            s.rows.forEach { (label, value) -> appendLine("$label: $value") }
        }
        appendLine()
        appendLine("## Failed photos ($failedCount)")
        failures.forEach { appendLine(it.line + (it.reason?.let { r -> " · $r" } ?: "")) }
        appendLine()
        appendLine("## Log")
        events.forEach(::appendLine)
    }
}

/** Gathers what someone helping with a problem needs to know about this phone and its index. */
class DiagnosticsReport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: PixelyDatabase,
    private val settings: IndexSettings,
    private val models: ModelFiles,
    private val deviceUse: DeviceUse,
    private val log: DiagnosticsLog,
) {
    suspend fun build(): Report = withContext(Dispatchers.IO) {
        val n = NumberFormat.getIntegerInstance()
        val meta = db.metaDao()
        val progress = db.photoDao().observeProgress().first()
        val events = log.lines()

        val chip = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE
        val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val phone = Section("Phone", listOf(
            "Phone" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Chip" to chip,
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Memory" to "%.1f GB".format(memory.totalMem / 1e9),
            "Pixely" to "${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})",
        ))

        val file = models.find()
        val model = Section("Model", listOf(
            "Model" to MODEL_ID,
            "Model file" to (file?.let { "%.2f GB".format(it.length() / 1e9) } ?: "Missing"),
            "Runs on" to (meta.get(MetaKey.DELEGATE) ?: "Not loaded yet"),
            "Detail" to settings.detail().let { "${if (it == Detail.FINE) "Fine" else "Standard"} · ${it.tokens} image tokens" },
            "Search size" to (meta.get(MetaKey.SEARCH_SIZE) ?: "768"),
        ))

        val now = System.currentTimeMillis()
        val budget = IndexBudget(
            meta.get(MetaKey.BUDGET_WINDOW_START)?.toLongOrNull() ?: now,
            meta.get(MetaKey.BUDGET_USED_MS)?.toLongOrNull() ?: 0,
        ).at(now)
        val state = when {
            meta.get(MetaKey.PROBLEM) != null -> "Can't run: ${meta.get(MetaKey.PROBLEM)}"
            meta.get(MetaKey.USER_PAUSED) != null -> "Paused by you"
            meta.get(MetaKey.RESUME_AT)?.toLongOrNull()?.takeIf { it > now } != null ->
                "Daily limit reached; resumes ${time(meta.get(MetaKey.RESUME_AT)!!.toLong())}"
            progress.pending == 0 -> "Up to date"
            else -> "Running or waiting to run"
        }
        val indexing = Section("Indexing", listOf(
            "State" to state,
            "Photos" to n.format(progress.total),
            "Indexed" to n.format(progress.done),
            "Failed" to n.format(progress.failed),
            "Waiting" to n.format(progress.pending),
            "Time per photo" to (meta.get(MetaKey.MS_PER_PHOTO)?.toDoubleOrNull()?.let { "%.2f s with the screen off".format(it / 1000) } ?: "Not measured yet"),
            "Indexing time today" to "${budget.usedMs / 60_000} of ${IndexBudget.LIMIT_MS / 60_000} min",
            "Only while charging" to if (IndexSettings.chargingOnlyOf(meta.get(MetaKey.CHARGING_ONLY))) "On" else "Off",
            "Battery" to "${deviceUse.batteryPercent()}%, ${if (deviceUse.plugged()) "plugged in" else "on battery"}",
            "Index size" to "%.1f MB".format(settings.indexBytes() / 1e6),
        ))

        val failures = db.photoDao().failed(FAILURES_SHOWN).map { p ->
            val name = p.uri.substringAfterLast('/')
            val line = "#${p.id} · media $name · ${p.mime} · ${p.width}×${p.height}" + (p.failedAt?.let { " · ${time(it)}" } ?: "")
            // The newest log line about this photo has the exception.
            val reason = events.lastOrNull { "Photo ${p.id} failed" in it }?.substringAfter("Photo ${p.id} failed")?.removePrefix(": ")?.ifBlank { null }
            Failure(line, reason)
        }
        Report(listOf(phone, model, indexing), failures, progress.failed, events)
    }

    /** The report as a text file in the cache, where the share sheet can reach it. */
    suspend fun export(report: Report): File = withContext(Dispatchers.IO) {
        File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
            .resolve("pixely-diagnostics-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}.txt")
            .apply { writeText(report.text()) }
    }

    private fun time(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))

    companion object {
        /** Matches res/xml/shared_files.xml. */
        const val EXPORT_DIR = "diagnostics"
        const val FAILURES_SHOWN = 50
    }
}
