package app.pixely.data.media

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build

/** How much of the library Pixely can see. Partial = Android 14+ "Select photos". */
enum class PhotoAccess { FULL, PARTIAL, NONE;

    companion object {
        /** What to ask for. On Android 14+ asking for both lets the system offer "Select photos". */
        val permissions: Array<String> = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(READ_MEDIA_IMAGES)
            else -> arrayOf(READ_EXTERNAL_STORAGE)
        }

        fun current(context: Context): PhotoAccess {
            fun granted(p: String) = context.checkSelfPermission(p) == PERMISSION_GRANTED
            return when {
                Build.VERSION.SDK_INT >= 33 && granted(READ_MEDIA_IMAGES) -> FULL
                Build.VERSION.SDK_INT >= 34 && granted(READ_MEDIA_VISUAL_USER_SELECTED) -> PARTIAL
                Build.VERSION.SDK_INT < 33 && granted(READ_EXTERNAL_STORAGE) -> FULL
                else -> NONE
            }
        }
    }
}
