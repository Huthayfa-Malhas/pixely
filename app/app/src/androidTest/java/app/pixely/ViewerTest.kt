package app.pixely

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Grid → viewer → swipe → back, on a phone that already granted photo access and has photos in
 * the debug test folder. Compose injects the touches itself, so this runs on phones that block
 * adb input. Screenshots land in the app's external files dir under test-shots/.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ViewerTest {

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensSwipesAndGoesBack() {
        rule.waitUntilAtLeastOneExists(hasContentDescription("Photo 1"), 10_000)
        shot("1_grid")

        rule.onNodeWithContentDescription("Photo 1").performClick()
        rule.waitUntilAtLeastOneExists(hasContentDescription("Back"), 5_000)
        rule.waitForIdle()
        val first = shownPhoto()
        shot("2_viewer")

        pager().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        val second = shownPhoto()
        assertNotEquals("swiping left shows the next photo", first, second)
        shot("3_swiped")

        pager().performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertEquals("swiping right comes back", first, shownPhoto())

        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntilAtLeastOneExists(hasContentDescription("Photo 1"), 5_000)
        shot("4_back")
    }

    private fun pager(): SemanticsNodeInteraction = rule.onNode(hasContentDescription("Photo taken ${shownPhoto()}"))

    /** The top bar's date and time; the pager's own pages can't tell which one is on screen. */
    private fun shownPhoto(): String =
        rule.onNode(hasText(" · ", substring = true)).fetchSemanticsNode().config[SemanticsProperties.Text].first().text

    private fun shot(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "test-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
