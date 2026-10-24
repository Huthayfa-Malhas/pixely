package app.pixely.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pixely.data.db.GridPhoto
import app.pixely.data.db.PixelyDatabase
import app.pixely.ui.Icons
import app.pixely.ui.theme.Bricolage
import app.pixely.ui.theme.PixelyColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Indexed photos only: the others have no vector to search with yet. Null while loading. */
@HiltViewModel
class PickPhotoViewModel @Inject constructor(db: PixelyDatabase) : ViewModel() {
    val photos: StateFlow<List<GridPhoto>?> = db.photoDao().observeIndexedGrid()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** The search box's photo button: pick the photo to start from, then add words. */
@Composable
fun PickPhotoScreen(photos: List<GridPhoto>?, onBack: () -> Unit, onPick: (GridPhoto) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Back, contentDescription = "Back", tint = PixelyColors.Text) }
            Column(Modifier.padding(start = 4.dp)) {
                Text("Search with a photo", fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("Pick one, then add words like “at night”", fontSize = 13.sp, color = PixelyColors.TextFaint)
            }
        }
        Box(Modifier.weight(1f).padding(top = 12.dp)) {
            when {
                photos == null -> Unit
                photos.isEmpty() -> Text(
                    "No photos are indexed yet. They can be searched with once they are.",
                    fontSize = 14.sp, color = PixelyColors.TextMuted, modifier = Modifier.padding(horizontal = 16.dp),
                )
                else -> ResultsGrid(photos, onOpen = { onPick(photos[it]) })
            }
        }
    }
}
