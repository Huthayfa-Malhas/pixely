package app.pixely.ui.indexing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pixely.data.index.BATTERY_FLOOR
import app.pixely.data.index.Detail
import app.pixely.ui.Icons
import app.pixely.ui.theme.Bricolage
import app.pixely.ui.theme.PixelyColors
import app.pixely.ui.thumbs.Thumbnail
import coil3.compose.AsyncImage
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** "Getting your library ready": progress, what's holding it up, and the settings that decide its pace. */
@Composable
fun IndexingScreen(state: IndexingState, onChargingOnly: (Boolean) -> Unit, onPause: (Boolean) -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 32.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (state.status == IndexStatus.Done) "Your library is ready" else "Getting your library ready",
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 30.sp, lineHeight = 33.sp),
                )
                Text(
                    "Search works right away on photos that are done. The rest continues in the background.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PixelyColors.TextMuted,
                )
            }
            Progress(state, onPause)
            if (state.recent.isNotEmpty()) Recent(state)
            Options(state, onChargingOnly)
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Start searching") }
    }
}

@Composable
private fun Progress(state: IndexingState, onPause: (Boolean) -> Unit) {
    val number = NumberFormat.getIntegerInstance()
    Card {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    "${state.percent}%",
                    fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = (-0.8).sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${number.format(state.progress.finished)} of ${number.format(state.progress.total)} photos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PixelyColors.TextMuted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(PixelyColors.Raised)) {
                Box(Modifier.fillMaxWidth(state.percent / 100f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(PixelyColors.Accent))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val active = state.status is IndexStatus.Running || state.status == IndexStatus.Done
                Icon(Icons.Bolt, contentDescription = null, tint = if (active) PixelyColors.Good else PixelyColors.TextFaint, modifier = Modifier.size(16.dp))
                Text(statusLine(state.status), style = MaterialTheme.typography.bodyMedium, color = PixelyColors.TextMuted, modifier = Modifier.weight(1f))
                if (state.status != IndexStatus.Done) {
                    val paused = state.status == IndexStatus.PausedByUser
                    TextButton(onClick = { onPause(!paused) }) {
                        Text(if (paused) "Resume" else "Pause", color = PixelyColors.Accent)
                    }
                }
            }
            if (state.progress.failed > 0) {
                Text(
                    "${number.format(state.progress.failed)} couldn't be read and were skipped",
                    style = MaterialTheme.typography.bodySmall,
                    color = PixelyColors.TextFaint,
                )
            }
        }
    }
}

@Composable
private fun Recent(state: IndexingState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "JUST INDEXED",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
            color = PixelyColors.TextFaint,
        )
        Row(Modifier.clip(RoundedCornerShape(12.dp)), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            state.recent.forEach {
                AsyncImage(
                    model = Thumbnail(it.uri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).height(64.dp).background(PixelyColors.Field),
                )
            }
            repeat(5 - state.recent.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Options(state: IndexingState, onChargingOnly: (Boolean) -> Unit) {
    Card {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Only while charging", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (state.chargingOnly) "Never uses your battery" else "On battery, stops at $BATTERY_FLOOR%",
                        style = MaterialTheme.typography.bodySmall, color = PixelyColors.TextFaint)
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
            HorizontalDivider(color = PixelyColors.Raised)
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Detail level", style = MaterialTheme.typography.bodyLarge)
                    Text("Fine reads small things better. Change it in Settings", style = MaterialTheme.typography.bodySmall, color = PixelyColors.TextFaint)
                }
                Text(if (state.detail == Detail.FINE) "Fine" else "Fast", style = MaterialTheme.typography.bodyLarge, color = PixelyColors.TextMuted)
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PixelyColors.Bar)
            .border(1.dp, PixelyColors.Raised, RoundedCornerShape(20.dp)),
    ) { content() }
}

fun statusLine(status: IndexStatus): String = when (status) {
    IndexStatus.Done -> "Every photo is searchable"
    IndexStatus.PausedByUser -> "Paused · search works on what's done"
    is IndexStatus.Running -> (if (status.charging) "Charging" else "On battery") + (status.msLeft?.let { " · about ${duration(it)} left" } ?: "")
    is IndexStatus.WaitingForCharger ->
        if (status.batteryLow) "Battery under $BATTERY_FLOOR% · continues on the charger" else "Waiting for the charger to continue"
    is IndexStatus.Paused -> "Paused for today · continues at ${clock(status.resumeAt)}"
    IndexStatus.ModelMissing -> "The AI model isn't on this phone yet"
    IndexStatus.Starting -> "Starting…"
}

/** "3 h 10 min", "25 min", "under a minute". */
fun duration(ms: Long): String {
    val minutes = (ms / 60_000).toInt()
    return when {
        minutes < 1 -> "under a minute"
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

private fun clock(at: Long): String =
    Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
