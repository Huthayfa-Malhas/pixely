package app.pixely.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pixely.data.media.PhotoAccess
import app.pixely.ui.Icons
import app.pixely.ui.Logo
import app.pixely.ui.SettingsButton
import app.pixely.ui.indexing.IndexStatus
import app.pixely.ui.indexing.IndexingState
import app.pixely.ui.theme.PixelyColors
import app.pixely.ui.thumbs.Thumbnail
import coil3.compose.AsyncImage

/** Every photo, newest first, in day groups. */
@Composable
fun PhotosScreen(
    library: Library,
    access: PhotoAccess,
    indexing: IndexingState,
    gridState: LazyGridState,
    onOpen: (index: Int) -> Unit,
    onChangeSelection: () -> Unit,
    onIndexing: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(Modifier.weight(1f))
                SettingsButton(onSettings)
            }
            if (access == PhotoAccess.PARTIAL) PartialAccess(library.photos.size, onChangeSelection)
            if (library.photos.isNotEmpty() && indexing.status != IndexStatus.Done) IndexingChip(indexing, onIndexing)
        }
        when {
            !library.loaded -> Unit
            library.photos.isEmpty() -> Empty(access)
            else -> Grid(library.items, gridState, onOpen)
        }
    }
}

@Composable
private fun Grid(items: List<GridItem>, state: LazyGridState, onOpen: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = state,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            items,
            key = { it.key },
            span = { if (it is GridItem.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
            contentType = { it::class },
        ) { item ->
            when (item) {
                is GridItem.Header -> Text(
                    item.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
                )
                is GridItem.Cell -> AsyncImage(
                    model = Thumbnail(item.photo.uri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(PixelyColors.Field)
                        .semantics { contentDescription = "Photo ${item.index + 1}" }
                        .clickable { onOpen(item.index) },
                )
            }
        }
    }
}

/** Indexing progress until every photo is done; opens the Indexing screen. */
@Composable
private fun IndexingChip(state: IndexingState, onClick: () -> Unit) {
    val running = state.status is IndexStatus.Running
    val text = when (val status = state.status) {
        is IndexStatus.Running -> "Indexing ${state.percent}% · ${if (status.charging) "charging" else "on battery"}"
        is IndexStatus.WaitingForCharger -> "Indexing ${state.percent}% · waiting for charger"
        is IndexStatus.Paused, IndexStatus.PausedByUser -> "Indexing paused · ${state.percent}%"
        IndexStatus.ModelMissing -> "AI model not installed"
        else -> "Indexing ${state.percent}%"
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (running) PixelyColors.GoodSoft else PixelyColors.Field)
            .clickable(onClick = onClick)
            .heightIn(min = 32.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val tint = if (running) PixelyColors.Good else PixelyColors.TextMuted
        Icon(Icons.Bolt, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/** Android 14+ "Select photos": say so, and let the user pick again. */
@Composable
private fun PartialAccess(count: Int, onChange: () -> Unit) {
    Row(
        Modifier.background(PixelyColors.GoodSoft, RoundedCornerShape(16.dp)).height(44.dp).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Showing $count photo${if (count == 1) "" else "s"} you chose",
            style = MaterialTheme.typography.labelMedium,
            color = PixelyColors.Good,
        )
        TextButton(onClick = onChange) { Text("Change", style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun Empty(access: PhotoAccess) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            if (access == PhotoAccess.PARTIAL) "No photos chosen yet." else "No photos on this phone yet.",
            style = MaterialTheme.typography.bodyLarge,
            color = PixelyColors.TextMuted,
        )
    }
}
