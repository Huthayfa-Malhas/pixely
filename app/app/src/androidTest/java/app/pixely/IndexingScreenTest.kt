package app.pixely

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Photos chip → Indexing screen → Start searching, with screenshots, while indexing has photos left. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class IndexingScreenTest {

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun chipOpensIndexingScreen() {
        rule.waitUntilAtLeastOneExists(hasText("Indexing", substring = true), 10_000)
        shot("1_chip")
        rule.onNodeWithText("Indexing", substring = true).performClick()
        rule.waitUntilAtLeastOneExists(hasText("Getting your library ready"), 5_000)
        rule.waitForIdle()
        shot("2_indexing")
        rule.onNodeWithText("Start searching").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Indexing", substring = true), 5_000)
    }

    private fun shot(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "test-shots").apply { mkdirs() }
        File(dir, "indexing_$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
