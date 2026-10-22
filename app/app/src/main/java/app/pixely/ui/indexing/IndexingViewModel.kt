package app.pixely.ui.indexing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import app.pixely.data.db.GridPhoto
import app.pixely.data.db.IndexProgress
import app.pixely.data.db.MetaKey
import app.pixely.data.db.PixelyDatabase
import app.pixely.data.index.Detail
import app.pixely.data.index.IndexScheduler
import app.pixely.data.index.IndexSettings
import app.pixely.data.index.IndexWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where indexing stands, in the terms the screen and the Photos chip speak. */
sealed interface IndexStatus {
    data object Done : IndexStatus
    data class Running(val charging: Boolean, val msLeft: Long?) : IndexStatus
    data object WaitingForCharger : IndexStatus
    data class Paused(val resumeAt: Long) : IndexStatus
    data object ModelMissing : IndexStatus
    data object Starting : IndexStatus
}

data class IndexingState(
    val progress: IndexProgress = IndexProgress(0, 0, 0),
    val status: IndexStatus = IndexStatus.Starting,
    val chargingOnly: Boolean = true,
    val detail: Detail = Detail.FINE,
    val recent: List<GridPhoto> = emptyList(),
) {
    val percent: Int get() = if (progress.total == 0) 100 else progress.finished * 100 / progress.total
}

/** Ordered by what the user can act on: a running job first, then why a queued one isn't running. */
fun indexStatus(
    progress: IndexProgress,
    work: WorkInfo.State?,
    problem: String?,
    resumeAt: Long?,
    msPerPhoto: Double?,
    chargingOnly: Boolean,
    charging: Boolean,
    now: Long,
): IndexStatus = when {
    progress.pending == 0 -> IndexStatus.Done
    work == WorkInfo.State.RUNNING -> IndexStatus.Running(charging, msPerPhoto?.let { (it * progress.pending).toLong() })
    problem == IndexWorker.PROBLEM_MODEL_MISSING -> IndexStatus.ModelMissing
    resumeAt != null && resumeAt > now -> IndexStatus.Paused(resumeAt)
    chargingOnly && !charging -> IndexStatus.WaitingForCharger
    else -> IndexStatus.Starting
}

@HiltViewModel
class IndexingViewModel @Inject constructor(
    @ApplicationContext context: Context,
    db: PixelyDatabase,
    scheduler: IndexScheduler,
    private val settings: IndexSettings,
) : ViewModel() {

    private val meta = db.metaDao()

    private val status: Flow<IndexStatus> = combine(
        db.photoDao().observeProgress(),
        scheduler.observe(),
        combine(meta.observe(MetaKey.PROBLEM), meta.observe(MetaKey.RESUME_AT), meta.observe(MetaKey.MS_PER_PHOTO), ::Triple),
        settings.observeChargingOnly(),
        charging(context),
    ) { progress, work, (problem, resumeAt, msPerPhoto), chargingOnly, charging ->
        indexStatus(progress, work?.state, problem, resumeAt?.toLongOrNull(), msPerPhoto?.toDoubleOrNull(), chargingOnly, charging, System.currentTimeMillis())
    }

    val state: StateFlow<IndexingState> = combine(
        db.photoDao().observeProgress(),
        status,
        settings.observeChargingOnly(),
        settings.observeDetail(),
        db.photoDao().observeRecentlyIndexed(RECENT),
    ) { progress, status, chargingOnly, detail, recent -> IndexingState(progress, status, chargingOnly, detail, recent) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IndexingState())

    fun setChargingOnly(on: Boolean) {
        viewModelScope.launch { settings.setChargingOnly(on) }
    }

    private companion object {
        const val RECENT = 5
    }
}

/**
 * Whether the phone is plugged in, now and as it changes. Plugged, not BatteryManager.isCharging:
 * some phones (this POCO among them) report not charging while on the charger.
 */
private fun charging(context: Context): Flow<Boolean> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0)
        }
    }
    // Sticky: the receiver gets the current state straight away.
    context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
    awaitClose { context.unregisterReceiver(receiver) }
}.distinctUntilChanged()
