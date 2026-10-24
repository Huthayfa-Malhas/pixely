package app.pixely

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Gear → Settings, then every action for real on the phone's index: search size there and back,
 * delete, rebuild. Delete and rebuild change the real database, so back it up before and restore it after.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun everySetting() {
        rule.waitUntilAtLeastOneExists(hasContentDescription("Settings"), 10_000)
        rule.onAllNodesWithContentDescription("Settings").onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText("No internet permission"), 5_000)
        rule.waitUntilAtLeastOneExists(hasText(" MB", substring = true), 5_000)
        shot("1_settings")

        var t = System.currentTimeMillis()
        rule.onNodeWithText("512").performClick()
        rule.waitUntilAtLeastOneExists(hasText("rebuilt at 512", substring = true), 60_000)
        Log.i(TAG, "Search size 512 in ${System.currentTimeMillis() - t} ms")
        rule.waitForIdle()
        shot("2_size_512")
        t = System.currentTimeMillis()
        rule.onNodeWithText("Full").performClick()
        rule.waitUntilAtLeastOneExists(hasText("rebuilt at full", substring = true), 60_000)
        Log.i(TAG, "Search size Full in ${System.currentTimeMillis() - t} ms")

        rule.onNodeWithText("Delete index").performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(isDialog(), 5_000)
        shot("3_delete_confirm")
        t = System.currentTimeMillis()
        rule.onNodeWithText("Delete").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Index deleted", substring = true), 60_000)
        Log.i(TAG, "Deleted in ${System.currentTimeMillis() - t} ms")
        rule.waitUntilAtLeastOneExists(hasText("0 / ", substring = true), 5_000)
        rule.waitForIdle()
        rule.onNodeWithText("Photos indexed").performScrollTo()
        shot("4_deleted")

        rule.onNodeWithText("Rebuild index").performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(isDialog(), 5_000)
        rule.onNodeWithText("Rebuild").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Rebuilding the index", substring = true), 10_000)
        rule.waitForIdle()
        shot("5_rebuilding")
    }

    private fun shot(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "test-shots").apply { mkdirs() }
        File(dir, "settings_$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private companion object {
        const val TAG = "SettingsScreenTest"
    }
}
