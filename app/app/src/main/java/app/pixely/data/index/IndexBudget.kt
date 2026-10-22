package app.pixely.data.index

/**
 * Android 15+ gives a mediaProcessing foreground service 6 hours in any 24. Pixely keeps its own
 * count so it stops a little early and says when it will resume, instead of being cut off.
 */
data class IndexBudget(val windowStart: Long, val usedMs: Long) {

    /** The budget as it stands at [now]: a window older than a day starts over. */
    fun at(now: Long): IndexBudget = if (now - windowStart >= DAY_MS) IndexBudget(now, 0) else this

    fun remainingMs(now: Long): Long = (LIMIT_MS - at(now).usedMs).coerceAtLeast(0)

    fun plus(ms: Long, now: Long): IndexBudget = at(now).let { it.copy(usedMs = it.usedMs + ms) }

    val resumeAt: Long get() = windowStart + DAY_MS

    companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
        /** 15 minutes under the system's 6 h, for the model load and the last batch. */
        const val LIMIT_MS = 6 * 60 * 60 * 1000L - 15 * 60 * 1000L
    }
}
