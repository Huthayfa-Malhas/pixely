package app.pixely.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pixely.ui.Icons
import app.pixely.ui.theme.PixelyColors
import app.pixely.ui.thumbs.Thumbnail
import coil3.compose.AsyncImage
import java.text.NumberFormat

/** Best matches first, with the "Without" chip and a date filter. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultsScreen(
    state: ResultsState,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
    onRemoveWithout: () -> Unit,
    onDates: (DateFilter) -> Unit,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable(state.input) { mutableStateOf(state.input) }
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 8.dp, end = 16.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.Back, contentDescription = "Back", tint = PixelyColors.Text) }
                val like = state.similarTo
                if (like != null && state.query == null) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(
                            model = Thumbnail(like.uri), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(PixelyColors.Field),
                        )
                        Text("Photos like this one", fontSize = 17.sp, color = PixelyColors.Text)
                    }
                } else {
                    SearchField(
                        value = text, onValueChange = { text = it }, onSearch = onSubmit,
                        placeholder = "Describe a photo…", height = 48.dp, modifier = Modifier.weight(1f),
                    )
                }
            }
            FlowRow(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.query?.without?.let {
                    Chip(
                        "Without: $it", onClick = onRemoveWithout, accent = true, leading = Icons.Minus,
                        trailing = Icons.Close, trailingLabel = "Remove", onTrailing = onRemoveWithout,
                    )
                }
                DateChip(state.dates, onDates)
            }
            Text(summary(state), modifier = Modifier.padding(start = 8.dp), fontSize = 13.sp, color = PixelyColors.TextFaint)
        }
        Box(Modifier.weight(1f).padding(top = 12.dp)) {
            ResultsGrid(state.photos, onOpen)
        }
    }
}

@Composable
private fun DateChip(selected: DateFilter, onDates: (DateFilter) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Chip("${selected.label} ▾", onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = PixelyColors.Field) {
            DateFilter.entries.forEach { f ->
                DropdownMenuItem(
                    text = { Text(f.label, color = if (f == selected) PixelyColors.Accent else PixelyColors.Text) },
                    onClick = { open = false; if (f != selected) onDates(f) },
                )
            }
        }
    }
}

private fun summary(state: ResultsState): String {
    val number = NumberFormat.getIntegerInstance()
    return when {
        state.modelMissing -> "The AI model isn't on this phone yet, so search can't run"
        state.searching && state.photos.isEmpty() -> "Searching…"
        state.photos.isEmpty() -> "No photos found" + if (state.dates != DateFilter.ANY) " in this date range" else ""
        else -> "${number.format(state.photos.size)} best matches first" +
            if (state.notIndexed > 0) " · ${number.format(state.notIndexed)} photos not indexed yet" else ""
    }
}
