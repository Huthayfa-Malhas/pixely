package app.pixely

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Search tab → "city at night, no people" → a result → Similar → + Words, with screenshots.
 * Needs the test photos indexed and the model on the phone.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SearchScreensTest {

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun searchSimilarAndWords() {
        rule.waitUntilAtLeastOneExists(hasContentDescription("Photo 1"), 10_000)
        rule.onNodeWithText("Search").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Describe a photo…"), 5_000)
        shot("1_search_tab")

        rule.onNode(hasSetTextAction()).performTextInput("city at night, no people")
        rule.onNode(hasSetTextAction()).performImeAction()
        // The first search loads the model on the CPU, about 3 s.
        rule.waitUntilAtLeastOneExists(hasText("best matches first", substring = true), 30_000)
        rule.waitUntilAtLeastOneExists(hasText("Without: people"), 1_000)
        rule.waitForIdle()
        shot("2_results")

        rule.onNodeWithContentDescription("Result 1").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Similar"), 5_000)
        rule.waitForIdle()
        shot("3_viewer")

        rule.onNodeWithText("Similar").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Photos like this one"), 5_000)
        rule.waitUntilAtLeastOneExists(hasText("best matches first", substring = true), 10_000)
        rule.waitForIdle()
        shot("4_similar")

        rule.onNodeWithContentDescription("Result 1").performClick()
        rule.waitUntilAtLeastOneExists(hasText("+ Words"), 5_000)
        rule.onNodeWithText("+ Words").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Search with this photo"), 5_000)
        rule.onAllNodesWithText("at night").onFirst().performClick()
        rule.waitUntilAtLeastOneExists(photoCount, 10_000)
        rule.waitForIdle()
        shot("5_photo_words")
    }

    /** The search box's photo button: pick a photo, then photo + words; back lands on Search. */
    @Test
    fun photoButton() {
        rule.waitUntilAtLeastOneExists(hasContentDescription("Photo 1"), 10_000)
        rule.onNodeWithText("Search").performClick()
        rule.onNodeWithContentDescription("Search with a photo").performClick()
        rule.waitUntilAtLeastOneExists(hasContentDescription("Result 1"), 10_000)
        rule.waitForIdle()
        shot("6_pick_photo")

        rule.onNodeWithContentDescription("Result 1").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Search with this photo"), 5_000)
        rule.onAllNodesWithText("at night").onFirst().performClick()
        rule.waitUntilAtLeastOneExists(photoCount, 10_000)
        rule.waitForIdle()
        shot("7_picked_words")

        rule.onNodeWithContentDescription("Close").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Describe a photo…"), 5_000)
    }

    /** "199 photos": the count once a photo + words search has finished. */
    private val photoCount = SemanticsMatcher("a photo count") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { Regex("""^[\d,]+ photos$""").matches(it.text) } == true
    }

    private fun shot(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "test-shots").apply { mkdirs() }
        File(dir, "search_$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
