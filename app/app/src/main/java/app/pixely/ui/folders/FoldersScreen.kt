package app.pixely.ui.folders

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pixely.data.index.IndexSettings
import app.pixely.data.media.Folder
import app.pixely.ui.Icons
import app.pixely.ui.settings.Card
import app.pixely.ui.settings.Divider
import app.pixely.ui.theme.Bricolage
import app.pixely.ui.theme.PixelyColors
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.NumberFormat
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [folders] is null while MediaStore is read; [busy] is the folder being switched. */
data class FoldersState(val folders: List<Folder>? = null, val skipped: Set<String> = emptySet(), val busy: String? = null)

@HiltViewModel
class FoldersViewModel @Inject constructor(private val settings: IndexSettings) : ViewModel() {

    private val folders = MutableStateFlow<List<Folder>?>(null)
    private val busy = MutableStateFlow<String?>(null)

    val state: StateFlow<FoldersState> = combine(folders, settings.observeSkippedFolders(), busy, ::FoldersState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoldersState())

    init {
        viewModelScope.launch { folders.value = settings.folders() }
    }

    fun setSearched(path: String, searched: Boolean) {
        if (busy.value != null) return
        viewModelScope.launch {
            busy.value = path
            try {
                settings.setFolderSkipped(path, skipped = !searched)
            } finally {
                busy.value = null
            }
        }
    }
}

/** Settings → Folders: every folder is searched until it's switched off. */
@Composable
fun FoldersScreen(state: FoldersState, onBack: () -> Unit, onSearched: (String, Boolean) -> Unit) {
    val n = NumberFormat.getIntegerInstance()
    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Back, contentDescription = "Back", tint = PixelyColors.Text, modifier = Modifier.size(22.dp))
                }
                Text("Folders", fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
        }
        item {
            Text(
                "Pixely searches every folder. Switch one off to leave it out: its photos disappear from Pixely and their index is deleted. The photos themselves stay on your phone.",
                fontSize = 14.sp, lineHeight = 20.sp, color = PixelyColors.TextMuted)
        }
        val folders = state.folders
        if (folders == null) {
            item { Text("Reading folders…", fontSize = 14.sp, color = PixelyColors.TextFaint) }
            return@LazyColumn
        }
        if (folders.isEmpty()) {
            item { Text("No folders with photos yet", fontSize = 14.sp, color = PixelyColors.TextFaint) }
            return@LazyColumn
        }
        item {
            Card {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                    folders.forEachIndexed { i, folder ->
                        if (i > 0) Divider()
                        val searched = folder.path !in state.skipped
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                .toggleable(value = searched, enabled = state.busy == null, role = Role.Switch) { onSearched(folder.path, it) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(folder.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${n.format(folder.count)} photos · ${folder.path.trimEnd('/')}",
                                    fontSize = 13.sp, color = PixelyColors.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Switch(
                                checked = searched,
                                onCheckedChange = null,
                                enabled = state.busy == null,
                                modifier = Modifier.padding(start = 12.dp),
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = PixelyColors.Accent,
                                    checkedThumbColor = PixelyColors.OnAccent,
                                    uncheckedTrackColor = PixelyColors.Raised,
                                    uncheckedThumbColor = PixelyColors.TextMuted,
                                    uncheckedBorderColor = PixelyColors.Line,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
