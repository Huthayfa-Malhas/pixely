package app.pixely.data.index

import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** How hard indexing may work right now. */
enum class Pace { FULL, HALF, PAUSE }

/**
 * Indexing takes the GPU and about 2 GB, enough to make the rest of the phone stutter. So it runs flat
 * out only with the screen off, at half speed while Pixely itself is open (so progress is visible),
 * and not at all while someone is using another app.
 */
class DeviceUse @Inject constructor(@ApplicationContext private val context: Context) {

    fun pace(): Pace = when {
        !context.getSystemService(PowerManager::class.java).isInteractive -> Pace.FULL
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> Pace.HALF
        else -> Pace.PAUSE
    }
}
