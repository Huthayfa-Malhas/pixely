package app.pixely.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.pixely.R

/** Colours from the Pixely design canvas. Dark only for v1. */
object PixelyColors {
    val Ground = Color(0xFF0F1012)
    val Bar = Color(0xFF17181B)
    val Field = Color(0xFF1F2024)
    val Raised = Color(0xFF2A2C30)
    val Line = Color(0xFF34363B)
    val Text = Color(0xFFF3F1EC)
    val TextMuted = Color(0xFFB3B0A9)
    val TextFaint = Color(0xFFA3A09A)
    val Accent = Color(0xFFF5B84A)
    val OnAccent = Color(0xFF1A1407)
    val AccentSoft = Color(0xFF3A2F17)
    val Good = Color(0xFF9FDDBF)
    val GoodSoft = Color(0xFF1A2A22)
}

private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Figtree = FontFamily(variable(R.font.figtree, 400), variable(R.font.figtree, 500), variable(R.font.figtree, 600))
val Bricolage = FontFamily(variable(R.font.bricolage_grotesque, 500), variable(R.font.bricolage_grotesque, 700))

private val colors = darkColorScheme(
    primary = PixelyColors.Accent,
    onPrimary = PixelyColors.OnAccent,
    primaryContainer = PixelyColors.AccentSoft,
    onPrimaryContainer = PixelyColors.Accent,
    background = PixelyColors.Ground,
    onBackground = PixelyColors.Text,
    surface = PixelyColors.Ground,
    onSurface = PixelyColors.Text,
    surfaceVariant = PixelyColors.Field,
    onSurfaceVariant = PixelyColors.TextMuted,
    surfaceContainer = PixelyColors.Bar,
    surfaceContainerHigh = PixelyColors.Field,
    surfaceContainerHighest = PixelyColors.Raised,
    outline = PixelyColors.Line,
)

private val base = Typography()

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 37.sp, letterSpacing = (-0.025).em),
    headlineSmall = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.02).em),
    titleMedium = base.titleMedium.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = base.bodyLarge.copy(fontFamily = Figtree, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = Figtree, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = base.bodySmall.copy(fontFamily = Figtree, fontSize = 12.sp),
    labelLarge = base.labelLarge.copy(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelMedium = base.labelMedium.copy(fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 13.sp),
)

/** The Surface gives every screen the ground colour and light text by default. */
@Composable
fun PixelyTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = colors, typography = typography) {
    Surface(Modifier.fillMaxSize(), color = PixelyColors.Ground, content = content)
}
