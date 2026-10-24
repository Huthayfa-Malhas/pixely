package app.pixely

import android.util.Log
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole app in one pass on the phone's real library, checking on every screen that each thing
 * you can tap has a label TalkBack reads out and a touch area of at least 48 × 48 dp. (Google's
 * Accessibility Test Framework would need newer Guava and AndroidX versions in the app itself.)
 * Text contrast is checked from the colours: the faintest text on the lightest surface is 5.4:1.
 * Photos → viewer → Similar → Search → results → photo button → photo + words → Settings → Diagnostics → Licences.
 * Welcome and the first index need a fresh install, so they're left to their own tests.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MainFlowTest {

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainFlow() {
        rule.waitUntilAtLeastOneExists(hasContentDescription("Photo 1"), 10_000)
        check("Photos")

        rule.onNodeWithContentDescription("Photo 1").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Similar"), 5_000)
        check("Viewer")
        rule.onNodeWithText("Similar").performClick()
        // The newest photo may still be waiting for indexing; then the screen says so.
        rule.waitUntilAtLeastOneExists(hasText("best matches first", substring = true) or hasText("isn't indexed yet", substring = true), 15_000)
        check("Similar results")
        back()
        back()

        rule.onNodeWithText("Search").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Describe a photo…"), 5_000)
        check("Search")
        rule.onNode(hasSetTextAction()).performTextInput("dog on a beach")
        rule.onNode(hasSetTextAction()).performImeAction()
        rule.waitUntilAtLeastOneExists(hasText("best matches first", substring = true), 30_000)
        check("Results")
        back()

        rule.onNodeWithContentDescription("Search with a photo").performClick()
        rule.waitUntilAtLeastOneExists(hasContentDescription("Result 1"), 10_000)
        check("Pick a photo")
        rule.onNodeWithContentDescription("Result 1").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Matches"), 10_000)
        check("Photo + words")
        rule.onNodeWithContentDescription("Close").performClick()

        rule.onAllNodesWithContentDescription("Settings").onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText("Index size"), 5_000)
        check("Settings")
        rule.onNodeWithText("Diagnostics").performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(hasText("Chip"), 10_000)
        check("Diagnostics")
        back()

        rule.onNodeWithText("Open-source licences").performScrollTo().performClick()
        rule.onNodeWithText("sqlite-vec").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Permission is hereby granted", substring = true), 5_000)
        check("Licences")
    }

    private fun check(screen: String) {
        rule.waitForIdle()
        val density = rule.activity.resources.displayMetrics.density
        val tappable = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        val problems = tappable.mapNotNull { node ->
            val label = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
                ?: node.config.getOrNull(SemanticsProperties.EditableText)?.text
            val touch = node.touchBoundsInRoot
            val w = touch.width / density
            val h = touch.height / density
            // A grid cell cut off at the screen edge is smaller than it really is; skip partial ones.
            val onScreen = node.boundsInRoot.height >= node.size.height - 1
            when {
                label.isNullOrBlank() -> "no label (${w.toInt()}×${h.toInt()} dp at ${node.boundsInRoot.topLeft})"
                onScreen && (w < MIN_DP || h < MIN_DP) -> "\"$label\" is ${w.toInt()}×${h.toInt()} dp"
                else -> null
            }
        }
        problems.forEach { Log.w(TAG, "$screen: $it") }
        Log.i(TAG, "$screen: ${tappable.size} tappable, ${problems.size} problems")
        assertTrue("$screen: $problems", problems.isEmpty())
    }

    private fun back() {
        rule.onAllNodesWithContentDescription("Back").onFirst().performClick()
        rule.waitForIdle()
    }

    private companion object {
        const val TAG = "MainFlowTest"
        const val MIN_DP = 47.5f
    }
}
