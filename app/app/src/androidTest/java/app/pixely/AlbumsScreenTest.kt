package app.pixely

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
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

/** Albums tab → a smart set → back → Duplicates, with screenshots and each card's count in the log. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AlbumsScreenTest {

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun albums() {
        rule.waitUntilAtLeastOneExists(hasText("Albums"), 10_000)
        val t = System.currentTimeMillis()
        rule.onAllNodesWithText("Albums").onLast().performClick()
        rule.waitUntil(180_000) { rule.onAllNodesWithText("Sorting…").fetchSemanticsNodes().isEmpty() && rule.onAllNodesWithText(" photos", substring = true).fetchSemanticsNodes().isNotEmpty() }
        Log.i(TAG, "Albums ready in ${System.currentTimeMillis() - t} ms")
        rule.onAllNodesWithText(" photos", substring = true).fetchSemanticsNodes().forEach {
            Log.i(TAG, "Card: " + it.config.getOrNull(SemanticsProperties.Text)?.joinToString())
        }
        rule.waitForIdle()
        shot("1_albums")

        rule.onNodeWithText("Food").performClick()
        rule.waitUntilAtLeastOneExists(hasText("newest first", substring = true), 30_000)
        rule.waitForIdle()
        shot("2_food")
        rule.onNodeWithContentDescriptionBack()

        rule.onNodeWithText("Duplicates").performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(hasText("groups ·", substring = true), 30_000)
        rule.waitForIdle()
        shot("3_duplicates")
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onNodeWithContentDescriptionBack() {
        onAllNodes(androidx.compose.ui.test.hasContentDescription("Back")).onFirst().performClick()
        waitUntilAtLeastOneExists(hasText("Smart sets", substring = true, ignoreCase = true), 10_000)
    }

    private fun shot(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "test-shots").apply { mkdirs() }
        File(dir, "albums_$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private companion object {
        const val TAG = "AlbumsScreenTest"
    }
}
