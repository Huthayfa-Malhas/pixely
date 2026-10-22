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
import android.util.Log
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
import app.pixely.data.media.PhotoAccess
import app.pixely.data.media.PhotoSync
import app.pixely.data.model.Embedder
import app.pixely.data.model.ModelFiles
import app.pixely.data.model.decodeForModel
import app.pixely.data.search.VectorIndex
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Indexes pending photos, newest first, one at a time, as a mediaProcessing foreground service.
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
) : CoroutineWorker(context, params) {

    private val meta = db.metaDao()
    private val photos = db.photoDao()

    override suspend fun doWork(): Result {
        // Photos added while Pixely was closed have no row yet.
        if (PhotoAccess.current(applicationContext) != PhotoAccess.NONE) sync.sync()
        if (photos.pendingCount() == 0) return Result.success()

        val model = models.find()
        if (model == null) {
            Log.w(TAG, "No model at ${models.expected}")
            meta.put(IndexMeta(MetaKey.PROBLEM, PROBLEM_MODEL_MISSING))
            return Result.success()
        }
        meta.clear(MetaKey.PROBLEM)

        var budget = budget().at(System.currentTimeMillis())
        if (budget.remainingMs(System.currentTimeMillis()) <= 0) return pauseUntil(budget.resumeAt)
        meta.clear(MetaKey.RESUME_AT)

        setForeground(foregroundInfo(null))
        val started = SystemClock.elapsedRealtime()
        var indexed = 0
        try {
            Embedder.open(applicationContext, model, IndexSettings.detailOf(meta.get(MetaKey.DETAIL)).tokens).use { embedder ->
                meta.put(IndexMeta(MetaKey.DELEGATE, embedder.delegate.name))
                Log.i(TAG, "Model loaded on ${embedder.delegate} in ${SystemClock.elapsedRealtime() - started} ms")
                var msPerPhoto = meta.get(MetaKey.MS_PER_PHOTO)?.toDoubleOrNull()
                while (true) {
                    val batch = photos.nextPending(BATCH)
                    if (batch.isEmpty()) break
                    for (photo in batch) {
                        val used = SystemClock.elapsedRealtime() - started
                        if (budget.usedMs + used >= IndexBudget.LIMIT_MS) return pauseUntil(budget.resumeAt)

                        val t0 = SystemClock.elapsedRealtime()
                        val vector = try {
                            embedder.photo(decodeForModel(applicationContext, Uri.parse(photo.uri)))
                        } catch (e: Exception) {
                            Log.w(TAG, "Photo ${photo.id} failed", e)
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
                        val ms = (SystemClock.elapsedRealtime() - t0).toDouble()
                        msPerPhoto = msPerPhoto?.let { it * 0.95 + ms * 0.05 } ?: ms
                        if (++indexed % PROGRESS_EVERY == 0) {
                            meta.put(IndexMeta(MetaKey.MS_PER_PHOTO, "%.0f".format(msPerPhoto)))
                            // A killed process never reaches finally, so the budget is saved as we go.
                            saveBudget(budget.plus(SystemClock.elapsedRealtime() - started, System.currentTimeMillis()))
                            setForeground(foregroundInfo(photos.pendingCount()))
                        }
                    }
                }
            }
            Log.i(TAG, "Done: $indexed photos in ${(SystemClock.elapsedRealtime() - started) / 1000} s")
            return Result.success()
        } finally {
            withContext(NonCancellable) {
                val now = System.currentTimeMillis()
                budget = budget.plus(SystemClock.elapsedRealtime() - started, now)
                // Cut off by the system's own 6 h limit: our count was behind, so trust theirs.
                if (Build.VERSION.SDK_INT >= 35 && stopReason == WorkInfo.STOP_REASON_FOREGROUND_SERVICE_TIMEOUT) {
                    budget = budget.copy(usedMs = IndexBudget.LIMIT_MS)
                    meta.put(IndexMeta(MetaKey.RESUME_AT, "${budget.resumeAt}"))
                    scheduler.start(ExistingWorkPolicy.APPEND_OR_REPLACE, budget.resumeAt - now)
                }
                saveBudget(budget)
                Log.i(TAG, "Stopped after $indexed photos; ${budget.usedMs / 60_000} min of today's budget used")
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
        Log.i(TAG, "Daily budget used; resuming at $at")
        meta.put(IndexMeta(MetaKey.RESUME_AT, "$at"))
        scheduler.start(ExistingWorkPolicy.APPEND_OR_REPLACE, (at - System.currentTimeMillis()).coerceAtLeast(0))
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(null)

    private fun foregroundInfo(pending: Int?): ForegroundInfo {
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
            .setContentText(pending?.let { "$it photos to go" })
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
        private const val BATCH = 32
        private const val PROGRESS_EVERY = 10
        const val PROBLEM_MODEL_MISSING = "model_missing"
    }
}
