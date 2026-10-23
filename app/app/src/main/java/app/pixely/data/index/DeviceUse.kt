package app.pixely.data.index

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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

    /**
     * On the charger, full battery or not. The system's "charging" goes false once a full battery stops
     * taking current, and some phones (this POCO among them) report not charging while plugged in anyway.
     */
    fun plugged(): Boolean = (battery()?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

    fun batteryPercent(): Int = battery()?.let {
        it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    } ?: 100

    fun needsCharger(chargingOnly: Boolean): Boolean = needsCharger(chargingOnly, plugged(), batteryPercent())

    /** Sticky: the last battery broadcast, without registering a receiver. */
    private fun battery(): Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
}

/** On battery, indexing stops here: the first full index costs most of a charge, new photos each day very little. */
const val BATTERY_FLOOR = 50

/** Plugged in, indexing runs whatever the level; on battery, only above [BATTERY_FLOOR] and when allowed. */
fun needsCharger(chargingOnly: Boolean, plugged: Boolean, batteryPercent: Int): Boolean =
    !plugged && (chargingOnly || batteryPercent < BATTERY_FLOOR)
