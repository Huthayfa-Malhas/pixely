package app.pixely.ui.diagnostics

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.pixely.data.diagnostics.Report
import app.pixely.data.diagnostics.Section
import app.pixely.ui.Icons
import app.pixely.ui.search.SectionLabel
import app.pixely.ui.settings.Card
import app.pixely.ui.settings.Divider
import app.pixely.ui.theme.Bricolage
import app.pixely.ui.theme.PixelyColors
import java.io.File

/** Log lines on the screen; the export has them all. */
private const val EVENTS_SHOWN = 100

/** What a tester sends when something's wrong: phone, model, indexing numbers, failures and the log. */
@Composable
fun DiagnosticsScreen(report: Report?, onBack: () -> Unit, onShare: () -> Unit) {
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
                Text("Diagnostics", fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Speed, failures and what indexing did, to send when something's wrong. Nothing here leaves your phone unless you share it, and it says nothing about what's in your photos.",
                    fontSize = 14.sp, lineHeight = 20.sp, color = PixelyColors.TextMuted)
                OutlinedButton(
                    onClick = onShare,
                    enabled = report != null,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    border = BorderStroke(1.dp, PixelyColors.Line),
                ) {
                    Icon(Icons.Share, contentDescription = null, tint = PixelyColors.Text, modifier = Modifier.size(18.dp))
                    Text("Share as a text file", color = PixelyColors.Text, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        if (report == null) {
            item { Text("Gathering…", fontSize = 14.sp, color = PixelyColors.TextFaint) }
            return@LazyColumn
        }
        items(report.sections) { Facts(it) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Failed photos · ${report.failedCount}")
                Card {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (report.failures.isEmpty()) Text("None", fontSize = 14.sp, color = PixelyColors.TextFaint)
                        report.failures.forEach { f ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(f.line, fontSize = 13.sp)
                                Text(f.reason ?: "No reason in the log", fontSize = 12.sp, color = PixelyColors.TextFaint)
                            }
                        }
                        if (report.failedCount > report.failures.size) {
                            Text("Newest ${report.failures.size} shown", fontSize = 12.sp, color = PixelyColors.TextFaint)
                        }
                    }
                }
            }
        }
        item { SectionLabel("Log · newest first") }
        val events = report.events.asReversed().take(EVENTS_SHOWN)
        if (events.isEmpty()) item { Text("Nothing yet", fontSize = 14.sp, color = PixelyColors.TextFaint) }
        items(events) { line ->
            Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, color = PixelyColors.TextMuted)
        }
    }
}

@Composable
private fun Facts(section: Section) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(section.title)
        Card {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                section.rows.forEachIndexed { i, (label, value) ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, fontSize = 14.sp, modifier = Modifier.weight(0.42f))
                        Box(Modifier.weight(0.58f), contentAlignment = Alignment.CenterEnd) {
                            Text(value, fontSize = 14.sp, color = PixelyColors.TextMuted, textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }
    }
}

/** Opens the share sheet with [file]; the authority is the manifest's FileProvider. */
fun shareDiagnostics(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, "Pixely diagnostics")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share diagnostics"))
}
