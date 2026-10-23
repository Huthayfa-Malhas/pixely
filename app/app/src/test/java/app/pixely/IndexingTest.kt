package app.pixely

import androidx.work.WorkInfo
import app.pixely.data.db.IndexProgress
import app.pixely.data.index.IndexBudget
import app.pixely.data.index.IndexBudget.Companion.DAY_MS
import app.pixely.data.index.IndexBudget.Companion.LIMIT_MS
import app.pixely.ui.indexing.IndexStatus
import app.pixely.ui.indexing.duration
import app.pixely.ui.indexing.indexStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class IndexingTest {

    private val hour = 60 * 60 * 1000L

    @Test
    fun `budget runs out within a day and comes back after it`() {
        val start = 1_000_000L
        val used = IndexBudget(start, 0).plus(5 * hour, start + 5 * hour)
        assertEquals(LIMIT_MS - 5 * hour, used.remainingMs(start + 6 * hour))
        val spent = used.plus(hour, start + 7 * hour)
        assertEquals(0, spent.remainingMs(start + 8 * hour))
        assertEquals(start + DAY_MS, spent.resumeAt)
        assertEquals(LIMIT_MS, spent.remainingMs(start + DAY_MS))
    }

    private fun status(
        pending: Int = 10,
        work: WorkInfo.State? = WorkInfo.State.ENQUEUED,
        userPaused: Boolean = false,
        problem: String? = null,
        resumeAt: Long? = null,
        chargingOnly: Boolean = true,
        charging: Boolean = false,
    ) = indexStatus(IndexProgress(100, 100 - pending, 0), work, userPaused, problem, resumeAt, 1_300.0, chargingOnly, charging, now = 1_000)

    @Test
    fun `status says what is holding indexing up`() {
        assertEquals(IndexStatus.Done, status(pending = 0, work = null))
        assertEquals(IndexStatus.Running(charging = true, msLeft = 13_000), status(work = WorkInfo.State.RUNNING, charging = true))
        assertEquals(IndexStatus.PausedByUser, status(work = WorkInfo.State.RUNNING, userPaused = true))
        assertEquals(IndexStatus.Done, status(pending = 0, userPaused = true))
        assertEquals(IndexStatus.ModelMissing, status(problem = "model_missing"))
        assertEquals(IndexStatus.Paused(5_000), status(resumeAt = 5_000))
        assertEquals(IndexStatus.WaitingForCharger, status(resumeAt = 500))
        assertEquals(IndexStatus.Starting, status(chargingOnly = false))
    }

    @Test
    fun `time left reads naturally`() {
        assertEquals("under a minute", duration(30_000))
        assertEquals("25 min", duration(25 * 60_000L))
        assertEquals("3 h", duration(3 * hour))
        assertEquals("3 h 40 min", duration(3 * hour + 40 * 60_000L))
    }
}
