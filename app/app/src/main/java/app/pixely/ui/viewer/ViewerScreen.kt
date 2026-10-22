package app.pixely.ui.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.pixely.data.db.GridPhoto
import app.pixely.ui.Icons
import app.pixely.ui.theme.PixelyColors
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One photo at a time, swiping through the library in grid order. Similar and + Words join
 * Share once search exists (weeks 6–7).
 */
@Composable
fun ViewerScreen(photos: List<GridPhoto>, startIndex: Int, onBack: () -> Unit, onPage: (Int) -> Unit) {
    if (photos.isEmpty()) return
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, photos.lastIndex)) { photos.size }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect(onPage) }
    val context = LocalContext.current
    val current = photos[pager.currentPage.coerceIn(0, photos.lastIndex)]

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, key = { photos[it].id }, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
            AsyncImage(
                model = photos[page].uri,
                contentDescription = "Photo taken ${takenLabel(photos[page].takenAt)}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(
            Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).statusBarsPadding().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Back, contentDescription = "Back", tint = PixelyColors.Text) }
            Text(
                takenLabel(current.takenAt),
                style = MaterialTheme.typography.bodyMedium,
                color = PixelyColors.TextMuted,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { share(context, current.uri) }) { Icon(Icons.Share, contentDescription = "Share", tint = PixelyColors.Text) }
        }
    }
}

private val takenFormat = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy · HH:mm", Locale.getDefault())

private fun takenLabel(takenAt: Long): String = Instant.ofEpochMilli(takenAt).atZone(ZoneId.systemDefault()).format(takenFormat)

/** Hands the photo to another app; the read grant covers just this one URI. */
private fun share(context: Context, uri: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/*")
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}
