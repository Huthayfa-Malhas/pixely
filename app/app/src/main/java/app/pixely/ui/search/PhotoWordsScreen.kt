package app.pixely.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pixely.ui.Icons
import app.pixely.ui.theme.PixelyColors
import app.pixely.ui.thumbs.Thumbnail
import coil3.compose.AsyncImage
import java.text.NumberFormat

/** Changes that read naturally after "this photo, …"; the last clears the words. */
private val SUGGESTIONS = listOf("same place", "at night", "with people", "more like this")

/** "Search with this photo": the photo plus words such as "but in black". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoWordsScreen(state: PhotoWordsState, onClose: () -> Unit, onWords: (String, Boolean) -> Unit, onOpen: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onClose) { Icon(Icons.Close, contentDescription = "Close", tint = PixelyColors.Text) }
                Text("Search with this photo", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp))
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PixelyColors.Bar).border(1.dp, PixelyColors.Raised, RoundedCornerShape(20.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = state.photo?.let { Thumbnail(it.uri) }, contentDescription = "The photo you're searching with", contentScale = ContentScale.Crop,
                    modifier = Modifier.size(88.dp, 112.dp).clip(RoundedCornerShape(12.dp)).background(PixelyColors.Field),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Change it with words", fontSize = 13.sp, color = PixelyColors.TextFaint)
                    SearchField(
                        value = state.words, onValueChange = { onWords(it, false) }, onSearch = { onWords(it, true) },
                        placeholder = "but in black", highlighted = true, height = 48.dp, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SUGGESTIONS.forEach { s -> Chip(s, onClick = { onWords(if (s == "more like this") "" else s, true) }) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Matches", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    when {
                        state.modelMissing -> "AI model not installed"
                        state.photoNotIndexed -> "Photo not indexed yet"
                        state.searching && state.photos.isEmpty() -> "Searching…"
                        else -> "${NumberFormat.getIntegerInstance().format(state.photos.size)} photos"
                    },
                    fontSize = 13.sp, color = PixelyColors.TextFaint,
                )
            }
        }
        Box(Modifier.weight(1f).padding(top = 10.dp)) {
            ResultsGrid(state.photos, onOpen, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp)
        }
    }
}
