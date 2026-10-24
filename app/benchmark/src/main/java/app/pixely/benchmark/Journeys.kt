package app.pixely.benchmark

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry

const val PACKAGE = "app.pixely"

/*
 * What a person does in Pixely, driven through accessibility actions rather than injected touches:
 * this POCO blocks input injection from adb (and so UiAutomator's click and fling), but Compose
 * answers click, scroll, set-text and IME actions from the accessibility tree.
 */

/** The Photos grid, a screenful at a time. */
fun MacrobenchmarkScope.scrollPhotos(screens: Int = 8) {
    waitFor { it.contentDescription?.toString() == "Photo 1" }
    repeat(screens) {
        val grid = waitFor { it.isScrollable } ?: return
        grid.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        device.waitForIdle()
    }
}

/** Search tab, type [query], search, wait for results, scroll them. */
fun MacrobenchmarkScope.search(query: String) {
    click { it.text?.toString() == "Search" }
    val field = waitFor { it.isEditable } ?: error("No search box")
    field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
    })
    field.performAction(AccessibilityAction.ACTION_IME_ENTER.id)
    waitFor(30_000) { it.text?.toString()?.contains("best matches first") == true } ?: error("No results for $query")
    repeat(3) {
        waitFor { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        device.waitForIdle()
    }
}

private fun click(match: (AccessibilityNodeInfo) -> Boolean) {
    var node = waitFor(match = match) ?: error("Nothing to click")
    // Text sits inside the clickable element; climb to it.
    while (!node.isClickable) node = node.parent ?: error("Nothing clickable")
    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
}

private fun waitFor(timeoutMs: Long = 10_000, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val until = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < until) {
        automation.rootInActiveWindow?.let { root -> find(root, match)?.let { return it } }
        Thread.sleep(100)
    }
    val root = automation.rootInActiveWindow
    android.util.Log.w("Journeys", "Not found; active window ${root?.packageName}, ${root?.childCount} children, windows ${automation.windows.map { it.title }}")
    return null
}

private fun find(node: AccessibilityNodeInfo, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
    if (match(node)) return node
    for (i in 0 until node.childCount) node.getChild(i)?.let { child -> find(child, match)?.let { return it } }
    return null
}
