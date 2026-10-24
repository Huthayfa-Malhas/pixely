package app.pixely.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pixely.data.index.BATTERY_FLOOR
import app.pixely.data.index.Detail
import app.pixely.data.search.SEARCH_SIZES
import app.pixely.ui.Icons
import app.pixely.ui.search.SectionLabel
import app.pixely.ui.theme.Bricolage
import app.pixely.ui.theme.PixelyColors
import java.text.NumberFormat

private val DETAIL_HINTS = mapOf(
    Detail.FINE to "Fine (${Detail.FINE.tokens} tokens) reads small things better. Changing it re-indexes.",
    Detail.FAST to "Standard (${Detail.FAST.tokens} tokens) indexes about 1.7× faster. Changing it re-indexes.",
)

// From the POC's vector-size tests on 375 COCO photos.
private val SIZE_HINTS = mapOf(
    768 to "Most accurate. Smaller sizes search big libraries faster. No re-index.",
    512 to "Same accuracy as Full in tests, a bit faster. No re-index.",
    256 to "About 2× faster at 100k photos; top 200 re-checked at full size. No re-index.",
    128 to "Fastest; top 200 re-checked at full size. No re-index.",
)

/** What Pixely stores and how it indexes; reached from the gear on any tab. */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onBack: () -> Unit,
    onChargingOnly: (Boolean) -> Unit,
    onDetail: (Detail) -> Unit,
    onSearchSize: (Int) -> Unit,
    onRebuild: () -> Unit,
    onDelete: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf<Confirm?>(null) }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Back, contentDescription = "Back", tint = PixelyColors.Text, modifier = Modifier.size(22.dp))
            }
            Text("Settings", fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        }
        NoInternet()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Index")
            Card {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                    val n = NumberFormat.getIntegerInstance()
                    Value("Photos indexed", "${n.format(state.progress.done)} / ${n.format(state.progress.total)}")
                    Divider()
                    Value("Index size", megabytes(state.indexBytes))
                    Divider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Only while charging", fontSize = 15.sp)
                            Text(
                                if (state.chargingOnly) "Never uses your battery" else "On battery, stops at $BATTERY_FLOOR%",
                                fontSize = 13.sp, color = PixelyColors.TextFaint)
                        }
                        Switch(
                            checked = state.chargingOnly,
                            onCheckedChange = onChargingOnly,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = PixelyColors.Accent,
                                checkedThumbColor = PixelyColors.OnAccent,
                                uncheckedTrackColor = PixelyColors.Raised,
                                uncheckedThumbColor = PixelyColors.TextMuted,
                                uncheckedBorderColor = PixelyColors.Line,
                            ),
                        )
                    }
                    Divider()
                    Options(
                        title = "Detail level",
                        hint = DETAIL_HINTS.getValue(state.detail),
                        options = Detail.entries,
                        selected = state.detail,
                        label = { it.label },
                        enabled = !state.busy,
                        onPick = onDetail,
                    )
                    Divider()
                    Options(
                        title = "Search size",
                        hint = SIZE_HINTS.getValue(state.searchSize),
                        options = SEARCH_SIZES,
                        selected = state.searchSize,
                        label = ::sizeLabel,
                        enabled = !state.busy,
                        onPick = onSearchSize,
                    )
                }
            }
            state.note?.let { Note(it) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Model")
            Card {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("EmbeddingGemma 2 · 740M", fontSize = 15.sp)
                    Text("On this phone · text + image · Apache 2.0", fontSize = 13.sp, color = PixelyColors.TextFaint)
                }
            }
        }
        Card {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onDiagnostics).heightIn(min = 60.dp).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Diagnostics", fontSize = 15.sp)
                    Text("Speed, failures and a log to send if something's wrong", fontSize = 13.sp, color = PixelyColors.TextFaint)
                }
                Icon(Icons.Back, contentDescription = null, tint = PixelyColors.TextFaint, modifier = Modifier.size(18.dp).rotate(180f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { confirm = Confirm.REBUILD },
                enabled = !state.busy,
                modifier = Modifier.weight(1f).height(48.dp),
                border = BorderStroke(1.dp, PixelyColors.Line),
            ) { Text("Rebuild index", color = PixelyColors.Text, fontSize = 15.sp) }
            OutlinedButton(
                onClick = { confirm = Confirm.DELETE },
                enabled = !state.busy,
                modifier = Modifier.weight(1f).height(48.dp),
                border = BorderStroke(1.dp, PixelyColors.DangerLine),
            ) { Text("Delete index", color = PixelyColors.Danger, fontSize = 15.sp) }
        }
    }
    confirm?.let { which ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = PixelyColors.Bar,
            title = { Text(which.title) },
            text = { Text(which.body, color = PixelyColors.TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (which == Confirm.REBUILD) onRebuild() else onDelete()
                }) { Text(which.action, color = if (which == Confirm.DELETE) PixelyColors.Danger else PixelyColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = PixelyColors.Text) } },
        )
    }
}

private enum class Confirm(val title: String, val body: String, val action: String) {
    REBUILD(
        "Rebuild the index?",
        "Every photo is indexed again, which takes hours on a big library. Search keeps working on the old index meanwhile.",
        "Rebuild",
    ),
    DELETE(
        "Delete the index?",
        "Search stops finding photos until it's rebuilt. Your photos aren't touched. Indexing stays paused until you resume it.",
        "Delete",
    ),
}

@Composable
private fun NoInternet() {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PixelyColors.GoodCard)
            .border(1.dp, PixelyColors.GoodLine, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Shield, contentDescription = null, tint = PixelyColors.Good, modifier = Modifier.size(26.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("No internet permission", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = PixelyColors.GoodTitle)
            Text(
                "Pixely can't connect to the internet. You can confirm it in Android's app info.",
                fontSize = 14.sp, lineHeight = 20.sp, color = PixelyColors.GoodBody)
        }
    }
}

@Composable
private fun Value(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, color = PixelyColors.TextMuted)
    }
}

/** A setting with a few fixed choices, as a row of pills under its name. */
@Composable
private fun <T> Options(
    title: String,
    hint: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    enabled: Boolean,
    onPick: (T) -> Unit,
) {
    Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp)
            Text(hint, fontSize = 13.sp, color = PixelyColors.TextFaint)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                val on = option == selected
                Box(
                    Modifier.weight(1f).height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) PixelyColors.AccentTint else PixelyColors.Bar)
                        .border(1.dp, if (on) PixelyColors.Accent else PixelyColors.Line, RoundedCornerShape(12.dp))
                        .selectable(selected = on, enabled = enabled, role = Role.RadioButton) { onPick(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(option), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) PixelyColors.AccentText else PixelyColors.Text)
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PixelyColors.AccentTint).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Info, contentDescription = null, tint = PixelyColors.Accent, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = PixelyColors.NoteText)
    }
}

@Composable
internal fun Card(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PixelyColors.Bar)
            .border(1.dp, PixelyColors.Raised, RoundedCornerShape(20.dp)),
    ) { content() }
}

@Composable
internal fun Divider() = HorizontalDivider(color = PixelyColors.Raised)

/** "72 MB"; under 1 MB still reads as a size, not zero. */
fun megabytes(bytes: Long): String = when {
    bytes < 1_000_000 -> "under 1 MB"
    else -> "${(bytes + 500_000) / 1_000_000} MB"
}
