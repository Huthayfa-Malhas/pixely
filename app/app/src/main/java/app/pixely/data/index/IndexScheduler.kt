package app.pixely.data.index

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.pixely.data.db.IndexMeta
import app.pixely.data.db.MetaKey
import app.pixely.data.db.PixelyDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** One indexing job at a time, under a unique name; WorkManager keeps it across reboots. */
@Singleton
class IndexScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: PixelyDatabase,
    private val deviceUse: DeviceUse,
) {
    private val work get() = WorkManager.getInstance(context)

    /**
     * KEEP leaves a queued or running job alone; REPLACE restarts it with new settings (photos already
     * done stay done); APPEND_OR_REPLACE queues a follow-up behind the running job, for its next window.
     */
    suspend fun start(policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP, delayMs: Long = 0) {
        // Everything that starts indexing comes through here, so a pause holds until the user lifts it.
        if (db.metaDao().get(MetaKey.USER_PAUSED) != null) return
        val chargingOnly = IndexSettings.chargingOnlyOf(db.metaDao().get(MetaKey.CHARGING_ONLY))
        // The system's charging constraint only to wake us when the charger goes in: it also fails once a
        // full battery stops charging, and would stop the job though the phone is still plugged in.
        // Otherwise the worker watches the plug and the battery level itself.
        val request = OneTimeWorkRequestBuilder<IndexWorker>()
            .setConstraints(Constraints.Builder().setRequiresCharging(deviceUse.needsCharger(chargingOnly)).build())
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            // A stop (app update, reboot, charger out) counts as an attempt; don't let the wait double each time.
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .addTag(NAME)
            .build()
        work.enqueueUniqueWork(NAME, policy, request)
    }

    /**
     * From the app opening: start now unless a job is already running or waiting for the next window.
     * A job sitting in a retry delay after being stopped is replaced, so it doesn't wait it out.
     */
    suspend fun startNow() {
        val queued = work.getWorkInfosForUniqueWorkFlow(NAME).first().filterNot { it.state.isFinished }
        val running = queued.any { it.state == WorkInfo.State.RUNNING }
        val paused = (db.metaDao().get(MetaKey.RESUME_AT)?.toLongOrNull() ?: 0) > System.currentTimeMillis()
        start(if (running || paused) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE)
    }

    /** Stops the running job (the photo in hand is lost, the rest kept) and keeps any new one from starting. */
    suspend fun pause() {
        db.metaDao().put(IndexMeta(MetaKey.USER_PAUSED, "true"))
        work.cancelUniqueWork(NAME)
    }

    suspend fun resume() {
        db.metaDao().clear(MetaKey.USER_PAUSED)
        startNow()
    }

    fun observePaused(): Flow<Boolean> = db.metaDao().observe(MetaKey.USER_PAUSED).map { it != null }

    /** The job that will run next: the running one, else the queued one; null when there is none. */
    fun observe(): Flow<WorkInfo?> = work.getWorkInfosForUniqueWorkFlow(NAME).map { infos ->
        infos.firstOrNull { it.state == WorkInfo.State.RUNNING } ?: infos.firstOrNull { !it.state.isFinished }
    }

    companion object {
        const val NAME = "index"
    }
}
