package app.pixely.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import app.pixely.ui.theme.PixelyColors

/** The design canvas's line icons: 24-unit viewport, 1.8 stroke, round caps. Tinted by Icon. */
object Icons {
    val Search = icon("M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0", "M20 20l-3.5-3.5")
    val Photo = icon(
        "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z",
        "M10.5 10a1.5 1.5 0 1 1-3 0a1.5 1.5 0 1 1 3 0",
        "M21 16l-5-5-8 8",
    )
    val Without = icon("M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0", "M5.6 5.6l12.8 12.8")
    val Bolt = icon("M13 2L4 14h7l-1 8 9-12h-7z")
    val Minus = icon("M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0", "M8 12h8")
    val Close = icon("M6 6l12 12M18 6L6 18")
    val Clock = icon("M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0", "M12 7v5l3 2")
    val Similar = icon("M9 7h9a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-9a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2z", "M4 16V6a2 2 0 0 1 2-2h10")
    val PlusWords = icon("M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0", "M20 20l-3.5-3.5M8 11h6M11 8v6")
    val Grid = icon(
        "M4.5 3h4.5a1.5 1.5 0 0 1 1.5 1.5v4.5a1.5 1.5 0 0 1-1.5 1.5h-4.5a1.5 1.5 0 0 1-1.5-1.5v-4.5a1.5 1.5 0 0 1 1.5-1.5z",
        "M15 3h4.5a1.5 1.5 0 0 1 1.5 1.5v4.5a1.5 1.5 0 0 1-1.5 1.5h-4.5a1.5 1.5 0 0 1-1.5-1.5v-4.5a1.5 1.5 0 0 1 1.5-1.5z",
        "M4.5 13.5h4.5a1.5 1.5 0 0 1 1.5 1.5v4.5a1.5 1.5 0 0 1-1.5 1.5h-4.5a1.5 1.5 0 0 1-1.5-1.5v-4.5a1.5 1.5 0 0 1 1.5-1.5z",
        "M15 13.5h4.5a1.5 1.5 0 0 1 1.5 1.5v4.5a1.5 1.5 0 0 1-1.5 1.5h-4.5a1.5 1.5 0 0 1-1.5-1.5v-4.5a1.5 1.5 0 0 1 1.5-1.5z",
    )
    val Back = icon("M15 5l-7 7 7 7")
    val Share = icon(
        "M8.5 12a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0",
        "M20.5 6a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0",
        "M20.5 18a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0",
        "M8.2 10.8l7.6-3.6M8.2 13.2l7.6 3.6",
    )

    private fun icon(vararg paths: String): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach {
                    addPath(
                        pathData = addPathNodes(it),
                        // Icon tints with SrcIn, so the stroke colour itself doesn't matter.
                        stroke = SolidColor(Color.White),
                        strokeLineWidth = 1.8f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()
}

/** The mark (four tiles on an accent square) and the wordmark. */
@Composable
fun Logo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.size(32.dp).background(PixelyColors.Accent, RoundedCornerShape(10.dp)).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            listOf(listOf(1f, 0.55f), listOf(0.55f, 1f)).forEach { row ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEach { alpha ->
                        Box(Modifier.weight(1f).fillMaxHeight().background(PixelyColors.Ground.copy(alpha = alpha), RoundedCornerShape(3.dp)))
                    }
                }
            }
        }
        Text("pixely", style = MaterialTheme.typography.headlineSmall)
    }
}
