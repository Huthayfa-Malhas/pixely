package app.pixely.ui.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pixely.data.albums.SmartSet
import app.pixely.data.db.GridPhoto
import app.pixely.ui.Icons
import app.pixely.ui.SettingsButton
import app.pixely.ui.photos.dayLabel
import app.pixely.ui.search.ResultsGrid
import app.pixely.ui.search.SectionLabel
import app.pixely.ui.theme.Bricolage
import app.pixely.ui.theme.PixelyColors
import app.pixely.ui.thumbs.Thumbnail
import coil3.compose.AsyncImage
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The Albums tab: the smart sets and Duplicates, each a card with its newest photo. */
@Composable
fun AlbumsScreen(state: AlbumsState, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Albums", fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.weight(1f))
            SettingsButton(onSettings)
        }
        SectionLabel("Smart sets · fill themselves")
        if (state.modelMissing) {
            Text("The AI model isn't on this phone yet, so albums can't be worked out", fontSize = 14.sp, color = PixelyColors.TextMuted)
            return@Column
        }
        // Until the first pass is done, the cards show without counts.
        val cards = state.cards ?: (SmartSet.entries.map { AlbumCard(it.name, it.label, -1, null, null) } + AlbumCard(DUPLICATES, "Duplicates", -1, null, null))
        cards.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { Card(it, onOpen, Modifier.weight(1f)) }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Card(card: AlbumCard, onOpen: (String) -> Unit, modifier: Modifier) {
    Column(modifier.clickable { onOpen(card.key) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)).background(PixelyColors.Field)) {
            card.cover?.let {
                AsyncImage(model = Thumbnail(it.uri), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(card.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(count(card), fontSize = 13.sp, color = PixelyColors.TextFaint)
    }
}

private fun count(card: AlbumCard): String {
    val n = NumberFormat.getIntegerInstance()
    return when {
        card.count < 0 -> "Sorting…"
        card.groups != null -> if (card.groups == 0) "None found" else "${n.format(card.groups)} groups · ${n.format(card.count)} photos"
        else -> "${n.format(card.count)} photos"
    }
}

/** One smart set, newest first, or the duplicate groups under a line each. */
@Composable
fun AlbumScreen(state: AlbumState, onBack: () -> Unit, onOpen: (group: Int, index: Int) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Back, contentDescription = "Back", tint = PixelyColors.Text) }
            Column(Modifier.padding(start = 4.dp)) {
                Text(state.title, fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(summary(state), fontSize = 13.sp, color = PixelyColors.TextFaint)
            }
        }
        Box(Modifier.weight(1f).padding(top = 12.dp)) {
            state.photos?.let { ResultsGrid(it, onOpen = { i -> onOpen(0, i) }) }
            state.groups?.let { Groups(it, onOpen) }
        }
    }
}

@Composable
private fun Groups(groups: List<List<GridPhoto>>, onOpen: (group: Int, index: Int) -> Unit) {
    val today = LocalDate.now()
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        groups.forEachIndexed { g, photos ->
            item(key = "group-$g", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "${photos.size} alike · ${dayLabel(Instant.ofEpochMilli(photos.first().takenAt).atZone(ZoneId.systemDefault()).toLocalDate(), today)}",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = if (g == 0) 0.dp else 16.dp, bottom = 6.dp),
                )
            }
            itemsIndexed(photos, key = { _, p -> "$g-${p.id}" }) { i, photo ->
                AsyncImage(
                    model = Thumbnail(photo.uri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(PixelyColors.Field)
                        .semantics { contentDescription = "Group ${g + 1} photo ${i + 1}" }
                        .clickable { onOpen(g, i) },
                )
            }
        }
    }
}

private fun summary(state: AlbumState): String {
    val n = NumberFormat.getIntegerInstance()
    state.groups?.let { groups ->
        return if (groups.isEmpty()) "No near-identical photos" else
            "${n.format(groups.size)} groups · ${n.format(groups.sumOf { it.size })} photos · newest first"
    }
    val photos = state.photos ?: return "Sorting…"
    return if (photos.isEmpty()) "No photos yet" else "${n.format(photos.size)} photos · newest first"
}
