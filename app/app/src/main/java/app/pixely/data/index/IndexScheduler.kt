package app.pixely.data.index

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.pixely.data.db.MetaKey
import app.pixely.data.db.PixelyDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One indexing job at a time, under a unique name; WorkManager keeps it across reboots. */
@Singleton
class IndexScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: PixelyDatabase,
) {
    private val work get() = WorkManager.getInstance(context)

    /**
     * KEEP leaves a queued or running job alone; REPLACE restarts it with new settings (photos already
     * done stay done); APPEND_OR_REPLACE queues a follow-up behind the running job, for its next window.
     */
    suspend fun start(policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP, delayMs: Long = 0) {
        val chargingOnly = db.metaDao().get(MetaKey.CHARGING_ONLY)?.toBooleanStrictOrNull() ?: true
        val request = OneTimeWorkRequestBuilder<IndexWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresCharging(chargingOnly)
                    .setRequiresBatteryNotLow(!chargingOnly)
                    .build()
            )
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag(NAME)
            .build()
        work.enqueueUniqueWork(NAME, policy, request)
    }

    /** The job that will run next: the running one, else the queued one; null when there is none. */
    fun observe(): Flow<WorkInfo?> = work.getWorkInfosForUniqueWorkFlow(NAME).map { infos ->
        infos.firstOrNull { it.state == WorkInfo.State.RUNNING } ?: infos.firstOrNull { !it.state.isFinished }
    }

    companion object {
        const val NAME = "index"
    }
}
