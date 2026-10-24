package app.pixely

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
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
 * Settings → Folders on the phone's test folder: switching "synth" (61 photos) off takes them out of
 * Pixely, switching it back on brings them back as photos to index.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FoldersScreenTest {

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun skipAndBringBack() {
        rule.waitUntilAtLeastOneExists(hasContentDescription("Settings"), 10_000)
        rule.onAllNodesWithContentDescription("Settings").onFirst().performClick()
        rule.onNodeWithText("Folders").performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(hasText("synth"), 10_000)
        rule.waitForIdle()
        shot("1_folders")

        val t = System.currentTimeMillis()
        rule.onNodeWithText("synth").performClick()
        back()
        rule.waitUntilAtLeastOneExists(hasText("1 folder left out"), 10_000)
        rule.waitUntilAtLeastOneExists(hasText("/ 538", substring = true), 10_000)
        Log.i(TAG, "synth left out in ${System.currentTimeMillis() - t} ms")
        shot("2_left_out")

        rule.onNodeWithText("Folders").performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(hasText("synth"), 10_000)
        rule.onNodeWithText("synth").performClick()
        back()
        rule.waitUntilAtLeastOneExists(hasText("Every folder is searched"), 10_000)
        rule.waitUntilAtLeastOneExists(hasText("/ 599", substring = true), 10_000)
        Log.i(TAG, "synth back")
    }

    private fun back() {
        rule.onAllNodesWithContentDescription("Back").onFirst().performClick()
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "test-shots").apply { mkdirs() }
        File(dir, "folders_$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private companion object {
        const val TAG = "FoldersScreenTest"
    }
}
