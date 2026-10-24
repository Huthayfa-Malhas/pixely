package app.pixely

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.pixely.data.diagnostics.DiagnosticsReport
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Gear → Settings → Diagnostics on the phone's real index: the facts, the failed photos and the log
 * show, and sharing writes the export file (the share sheet itself is left open).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DiagnosticsScreenTest {

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun diagnostics() {
        rule.waitUntilAtLeastOneExists(hasContentDescription("Settings"), 10_000)
        rule.onAllNodesWithContentDescription("Settings").onFirst().performClick()
        rule.onNodeWithText("Diagnostics").performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(hasText("Chip"), 10_000)
        rule.waitForIdle()
        shot("1_top")

        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("FAILED PHOTOS", substring = true))
        rule.waitForIdle()
        shot("2_failed")

        val exports = File(rule.activity.cacheDir, DiagnosticsReport.EXPORT_DIR)
        exports.listFiles()?.forEach { it.delete() }
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        rule.onNodeWithText("Share as a text file").performClick()
        rule.waitUntil(10_000) { exports.listFiles()?.isNotEmpty() == true }
        val text = exports.listFiles()!!.single().readText()
        text.lines().take(60).forEach { Log.i(TAG, it) }
        assertTrue(text.startsWith("Pixely diagnostics"))
        assertTrue("## Log" in text)
    }

    private fun shot(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "test-shots").apply { mkdirs() }
        File(dir, "diagnostics_$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private companion object {
        const val TAG = "DiagnosticsScreenTest"
    }
}
