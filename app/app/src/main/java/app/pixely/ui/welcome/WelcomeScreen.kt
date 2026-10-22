package app.pixely.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pixely.ui.Icons
import app.pixely.ui.Logo
import app.pixely.ui.theme.PixelyColors

/**
 * First run: what Pixely does and the photo permission. [denied] means the system will no longer
 * show the prompt, so the button opens Pixely's page in system settings instead.
 */
@Composable
fun WelcomeScreen(denied: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    // The button stays pinned at the bottom; the rest scrolls on short screens.
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 32.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Logo()
            Mosaic()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Search your photos by what's in them.", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Everything runs on this phone. Pixely has no internet permission, so your photos can't leave it.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PixelyColors.TextMuted,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Point(Icons.Search, "Type what you remember: \"sunset at the beach\", \"red jacket, white sneakers\"")
                Point(Icons.Photo, "Search with a photo, and add words: \"this, but in black\"")
                Point(Icons.Without, "Leave things out: \"city at night, no people\"")
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (denied) {
                Text(
                    "Photo access is off. Turn it on in Settings › Permissions › Photos and videos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PixelyColors.TextMuted,
                )
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Open settings") }
            } else {
                Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Allow access to photos") }
            }
            Text(
                "AI model stored on this phone · 485 MB",
                style = MaterialTheme.typography.bodySmall,
                color = PixelyColors.TextFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Point(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = PixelyColors.Accent, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Six tiles in the library's colours, standing in for photos the app can't see yet. */
@Composable
private fun Mosaic() {
    val tiles = listOf(0xFF8C5A3C, 0xFF2F4858, 0xFFA0825F, 0xFF4E6B5C, 0xFF6E4C4C, 0xFF55606E).map(::Color)
    Column(Modifier.clip(RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { Box(Modifier.weight(1f).height(96.dp).background(it)) }
            }
        }
    }
}
