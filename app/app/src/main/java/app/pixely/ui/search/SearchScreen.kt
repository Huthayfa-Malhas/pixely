package app.pixely.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pixely.ui.Icons
import app.pixely.ui.SettingsButton
import app.pixely.ui.theme.PixelyColors

/** Examples that show the three kinds of query: a scene, details, and leaving something out. */
private val TRY = listOf("sunset at the beach", "two dogs in snow", "screenshots", "city at night, no people")

/** The Search tab: describe a photo, start from one of your photos, or from a suggestion or a recent search. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(recent: List<String>, onSearch: (String) -> Unit, onPhoto: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(
        modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchField(
                value = text,
                onValueChange = { text = it },
                onSearch = { if (it.isNotBlank()) onSearch(it) },
                placeholder = "Describe a photo…",
                highlighted = true,
                leading = { Icon(Icons.Search, contentDescription = null, tint = PixelyColors.Text, modifier = Modifier.size(20.dp)) },
                trailing = {
                    IconButton(onClick = onPhoto, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Photo, contentDescription = "Search with a photo", tint = PixelyColors.TextMuted, modifier = Modifier.size(22.dp))
                    }
                },
                modifier = Modifier.weight(1f),
            )
            SettingsButton(onSettings)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Try")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TRY.forEach { Chip(it, onClick = { onSearch(it) }, height = 36.dp) }
            }
        }
        if (recent.isNotEmpty()) {
            Column {
                SectionLabel("Recent", Modifier.padding(bottom = 6.dp))
                recent.forEach { q ->
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).clickable { onSearch(q) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Clock, contentDescription = null, tint = PixelyColors.TextFaint, modifier = Modifier.size(18.dp))
                        Text(q, fontSize = 15.sp, color = PixelyColors.Text)
                    }
                }
            }
        }
    }
}
