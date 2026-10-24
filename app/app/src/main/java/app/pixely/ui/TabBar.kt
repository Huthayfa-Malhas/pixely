package app.pixely.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pixely.ui.theme.PixelyColors

enum class Tab(val label: String, val icon: ImageVector) {
    PHOTOS("Photos", Icons.Grid), SEARCH("Search", Icons.Search), ALBUMS("Albums", Icons.Albums),
}

/** Bottom navigation. */
@Composable
fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(Modifier.fillMaxWidth().background(PixelyColors.Bar).navigationBarsPadding().height(80.dp)) {
        Tab.entries.forEach { tab ->
            val on = tab == selected
            Column(
                Modifier.weight(1f).fillMaxHeight().selectable(selected = on, role = Role.Tab) { onSelect(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Box(
                    Modifier.size(64.dp, 32.dp).background(if (on) PixelyColors.AccentSoft else PixelyColors.Bar, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = if (on) PixelyColors.Accent else PixelyColors.TextMuted, modifier = Modifier.size(22.dp))
                }
                Text(tab.label, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, color = if (on) PixelyColors.Text else PixelyColors.TextMuted)
            }
        }
    }
}

/** The way into Settings, top right of every tab. */
@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.iconButtonColors(containerColor = PixelyColors.Field),
    ) {
        Icon(Icons.Gear, contentDescription = "Settings", tint = PixelyColors.Text, modifier = Modifier.size(22.dp))
    }
}
