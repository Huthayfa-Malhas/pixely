package app.pixely.ui.licences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pixely.ui.Icons
import app.pixely.ui.settings.Card
import app.pixely.ui.theme.Bricolage
import app.pixely.ui.theme.PixelyColors

/** One licence text in assets (the repo's licenses/ folder) and what it covers. */
private class Notice(val name: String, val detail: String, val file: String)

private val NOTICES = listOf(
    Notice("Pixely", "Apache License 2.0", "Apache-2.0.txt"),
    Notice("EmbeddingGemma 2", "Google · Apache License 2.0", "Apache-2.0.txt"),
    Notice(
        "AndroidX, Jetpack Compose, Room, WorkManager, Dagger Hilt, Kotlin, Coil, MediaPipe, LiteRT-LM",
        "Apache License 2.0",
        "Apache-2.0.txt",
    ),
    Notice("sqlite-vec", "Alex Garcia · MIT License", "MIT-sqlite-vec.txt"),
    Notice("Figtree", "Erik Kennedy · SIL Open Font License 1.1", "OFL-Figtree.txt"),
    Notice("Bricolage Grotesque", "Mathieu Triay · SIL Open Font License 1.1", "OFL-Bricolage-Grotesque.txt"),
    Notice("Lucide icons", "ISC License", "ISC-Lucide.txt"),
)

/** The code, model and fonts Pixely is built with, each opening to its licence text. */
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Back, contentDescription = "Back", tint = PixelyColors.Text, modifier = Modifier.size(22.dp))
                }
                Text("Open-source licences", fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
        }
        items(NOTICES, key = { it.name }) { notice ->
            val expanded = open == notice.name
            Card {
                Column {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { open = if (expanded) null else notice.name }
                            .heightIn(min = 60.dp)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(notice.name, fontSize = 15.sp)
                            Text(notice.detail, fontSize = 13.sp, color = PixelyColors.TextFaint)
                        }
                        Icon(
                            Icons.Back, contentDescription = null, tint = PixelyColors.TextFaint,
                            modifier = Modifier.size(18.dp).rotate(if (expanded) 90f else 270f))
                    }
                    if (expanded) LicenceText(notice.file)
                }
            }
        }
    }
}

@Composable
private fun LicenceText(file: String) {
    val context = LocalContext.current
    val text = remember(file) { context.assets.open(file).bufferedReader().use { it.readText() } }
    Text(
        text.trim(),
        fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, color = PixelyColors.TextMuted,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
    )
}
