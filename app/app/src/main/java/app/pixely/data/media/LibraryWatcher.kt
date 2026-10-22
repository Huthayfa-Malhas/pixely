package app.pixely.data.media

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the photo table current while the app is in use: a sync each time it comes to the
 * foreground (changes made while it was closed) and after MediaStore reports a change.
 */
@Singleton
class LibraryWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sync: PhotoSync,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null
    private var watching = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = schedule(DEBOUNCE_MS)
    }

    fun start() = ProcessLifecycleOwner.get().lifecycle.addObserver(this)

    /** Call after the user changes photo access. */
    fun refresh() = onStart(ProcessLifecycleOwner.get())

    override fun onStart(owner: LifecycleOwner) {
        if (PhotoAccess.current(context) == PhotoAccess.NONE) return
        if (!watching) {
            context.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
            watching = true
        }
        schedule(0)
    }

    override fun onStop(owner: LifecycleOwner) {
        context.contentResolver.unregisterContentObserver(observer)
        watching = false
    }

    /** A camera burst or a bulk delete fires many changes; one sync after they settle covers them all. */
    private fun schedule(delayMs: Long) {
        pending?.cancel()
        pending = scope.launch {
            delay(delayMs)
            if (PhotoAccess.current(context) != PhotoAccess.NONE) sync.sync()
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 500L
    }
}
