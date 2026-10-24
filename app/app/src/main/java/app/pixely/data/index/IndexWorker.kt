package app.pixely.data.index

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import app.pixely.MainActivity
import app.pixely.R
import app.pixely.data.db.IndexMeta
import app.pixely.data.db.MetaKey
import app.pixely.data.db.PixelyDatabase
import app.pixely.data.diagnostics.DiagnosticsLog
import app.pixely.data.media.PhotoAccess
import app.pixely.data.media.PhotoSync
import app.pixely.data.model.Embedder
import app.pixely.data.model.ModelFiles
import app.pixely.data.model.decodeForModel
import app.pixely.data.search.VectorIndex
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Indexes pending photos, newest first, one at a time, as a mediaProcessing foreground service,
 * at the [Pace] the phone's use allows.
 * Each photo's vector and its DONE mark are written as soon as it's embedded, so a stop for any
 * reason (charger out, reboot, the 6 h cap, a settings change) loses at most the photo in hand.
 */
@HiltWorker
class IndexWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: PixelyDatabase,
    private val index: VectorIndex,
    private val models: ModelFiles,
    private val sync: PhotoSync,
    private val scheduler: IndexScheduler,
    private val deviceUse: DeviceUse,
    private val log: DiagnosticsLog,
) : CoroutineWorker(context, params) {

    private val meta = db.metaDao()
    private val photos = db.photoDao()

    override suspend fun doWork(): Result {
        // Photos added while Pixely was closed have no row yet.
        if (PhotoAccess.current(applicationContext) != PhotoAccess.NONE) sync.sync()
        if (photos.pendingCount() == 0) return Result.success()

        val modelFile = models.find()
        if (modelFile == null) {
            log.w(TAG, "No model at ${models.expected}")
            meta.put(IndexMeta(MetaKey.PROBLEM, PROBLEM_MODEL_MISSING))
            return Result.success()
        }
        meta.clear(MetaKey.PROBLEM)

        var budget = budget().at(System.currentTimeMillis())
        if (budget.remainingMs(System.currentTimeMillis()) <= 0) return pauseUntil(budget.resumeAt)
        meta.clear(MetaKey.RESUME_AT)

        val chargingOnly = IndexSettings.chargingOnlyOf(meta.get(MetaKey.CHARGING_ONLY))
        if (deviceUse.needsCharger(chargingOnly)) return waitForCharger()

        setForeground(foregroundInfo(null))
        val started = SystemClock.elapsedRealtime()
        val detail = IndexSettings.detailOf(meta.get(MetaKey.DETAIL)).tokens
        var indexed = 0
        var embedder: Embedder? = null
        var pausedSince: Long? = null
        // Each photo's time since the last progress line, for the diagnostics log.
        var batchMs = 0L
        var slowestMs = 0L
        try {
            var msPerPhoto = meta.get(MetaKey.MS_PER_PHOTO)?.toDoubleOrNull()
            while (true) {
                val used = SystemClock.elapsedRealtime() - started
                if (budget.usedMs + used >= IndexBudget.LIMIT_MS) return pauseUntil(budget.resumeAt)
                if (deviceUse.needsCharger(chargingOnly)) return waitForCharger()

                val pace = deviceUse.pace()
                if (pace == Pace.PAUSE) {
                    val since = pausedSince ?: SystemClock.elapsedRealtime().also {
                        pausedSince = it
                        setForeground(foregroundInfo(photos.pendingCount(), paused = true))
                    }
                    val paused = SystemClock.elapsedRealtime() - since
                    // Hand the memory back while the phone is in use; reloading takes about 20 s.
                    if (embedder != null && paused >= RELEASE_MODEL_AFTER_MS) {
                        embedder.close()
                        embedder = null
                    }
                    // A long pause would use up the daily foreground allowance doing nothing: stop, look again later.
                    if (paused >= GIVE_UP_AFTER_MS) {
                        log.i(TAG, "Phone in use; checking again in ${RETRY_AFTER_MS / 60_000} min")
                        scheduler.start(ExistingWorkPolicy.APPEND_OR_REPLACE, RETRY_AFTER_MS)
                        return Result.success()
                    }
                    delay(PAUSE_POLL_MS)
                    continue
                }
                if (pausedSince != null) {
                    pausedSince = null
                    setForeground(foregroundInfo(photos.pendingCount()))
                }

                val photo = photos.nextPending(1).firstOrNull() ?: break
                val model = embedder ?: run {
                    val t = SystemClock.elapsedRealtime()
                    Embedder.open(applicationContext, modelFile, detail).also {
                        meta.put(IndexMeta(MetaKey.DELEGATE, it.delegate.name))
                        log.i(TAG, "Model loaded on ${it.delegate} in ${SystemClock.elapsedRealtime() - t} ms")
                    }
                }.also { embedder = it }

                val t0 = SystemClock.elapsedRealtime()
                val vector = try {
                    model.photo(decodeForModel(applicationContext, Uri.parse(photo.uri)))
                } catch (e: Exception) {
                    log.w(TAG, "Photo ${photo.id} failed", e)
                    null
                }
                val now = System.currentTimeMillis()
                if (vector == null) {
                    photos.markFailed(photo.id, now)
                } else {
                    index.put(photo.id, vector)
                    // Deleted while it was being embedded: drop the vector we just wrote.
                    if (photos.markDone(photo.id, now) == 0) index.remove(photo.id)
                }
                val ms = SystemClock.elapsedRealtime() - t0
                batchMs += ms
                slowestMs = maxOf(slowestMs, ms)
                if (pace == Pace.FULL) msPerPhoto = msPerPhoto?.let { it * 0.95 + ms * 0.05 } ?: ms.toDouble()
                if (++indexed % PROGRESS_EVERY == 0) {
                    msPerPhoto?.let { meta.put(IndexMeta(MetaKey.MS_PER_PHOTO, "%.0f".format(it))) }
                    log.i(TAG, "$indexed photos; ${batchMs / PROGRESS_EVERY} ms each, slowest $slowestMs ms; " +
                        "battery ${deviceUse.batteryPercent()}%, ${if (deviceUse.plugged()) "plugged" else "on battery"}, $pace")
                    batchMs = 0
                    slowestMs = 0
                    // A killed process never reaches finally, so the budget is saved as we go.
                    saveBudget(budget.plus(SystemClock.elapsedRealtime() - started, System.currentTimeMillis()))
                    setForeground(foregroundInfo(photos.pendingCount()))
                }
                // Half speed: rest as long as the photo took, leaving the GPU free for the screen.
                if (pace == Pace.HALF) delay(ms)
            }
            log.i(TAG, "Done: $indexed photos in ${(SystemClock.elapsedRealtime() - started) / 1000} s")
            return Result.success()
        } finally {
            embedder?.close()
            withContext(NonCancellable) {
                val now = System.currentTimeMillis()
                budget = budget.plus(SystemClock.elapsedRealtime() - started, now)
                // Cut off by the system's own 6 h limit: our count was behind, so trust theirs.
                if (Build.VERSION.SDK_INT >= 35 && stopReason == WorkInfo.STOP_REASON_FOREGROUND_SERVICE_TIMEOUT) {
                    budget = budget.copy(usedMs = IndexBudget.LIMIT_MS)
                    meta.put(IndexMeta(MetaKey.RESUME_AT, "${budget.resumeAt}"))
                    scheduler.start(ExistingWorkPolicy.APPEND_OR_REPLACE, budget.resumeAt - now)
                }
                // Stopped for "not charging" while still plugged in (a full battery): go on without that constraint.
                if (stopReason == WorkInfo.STOP_REASON_CONSTRAINT_CHARGING && deviceUse.plugged()) {
                    log.i(TAG, "Battery full, still plugged in; carrying on")
                    scheduler.start(ExistingWorkPolicy.REPLACE)
                }
                saveBudget(budget)
                log.i(TAG, "Stopped after $indexed photos; ${budget.usedMs / 60_000} min of today's budget used")
            }
        }
    }

    private suspend fun budget() = IndexBudget(
        meta.get(MetaKey.BUDGET_WINDOW_START)?.toLongOrNull() ?: System.currentTimeMillis(),
        meta.get(MetaKey.BUDGET_USED_MS)?.toLongOrNull() ?: 0,
    )

    private suspend fun saveBudget(b: IndexBudget) {
        meta.put(IndexMeta(MetaKey.BUDGET_WINDOW_START, "${b.windowStart}"))
        meta.put(IndexMeta(MetaKey.BUDGET_USED_MS, "${b.usedMs}"))
    }

    /** Queues the next run for when the window resets, behind this one. */
    private suspend fun pauseUntil(at: Long): Result {
        log.i(TAG, "Daily budget used; resuming at $at")
        meta.put(IndexMeta(MetaKey.RESUME_AT, "$at"))
        scheduler.start(ExistingWorkPolicy.APPEND_OR_REPLACE, (at - System.currentTimeMillis()).coerceAtLeast(0))
        return Result.success()
    }

    /** Unplugged with charging-only on, or the battery under the floor: queue a job that waits for the charger. */
    private suspend fun waitForCharger(): Result {
        log.i(TAG, "Battery ${deviceUse.batteryPercent()}%, unplugged; waiting for the charger")
        scheduler.start(ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(null)

    private fun foregroundInfo(pending: Int?, paused: Boolean = false): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, applicationContext.getString(R.string.indexing_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.indexing_title))
            .setContentText(if (paused) "Paused while you use your phone" else pending?.let { "$it photos to go" })
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        return ForegroundInfo(NOTIFICATION_ID, notification, type)
    }

    companion object {
        private const val TAG = "IndexWorker"
        private const val CHANNEL = "indexing"
        private const val NOTIFICATION_ID = 1
        private const val PAUSE_POLL_MS = 2_000L
        private const val RELEASE_MODEL_AFTER_MS = 30_000L
        private const val GIVE_UP_AFTER_MS = 10 * 60_000L
        private const val RETRY_AFTER_MS = 15 * 60_000L
        private const val PROGRESS_EVERY = 10
        const val PROBLEM_MODEL_MISSING = "model_missing"
    }
}
