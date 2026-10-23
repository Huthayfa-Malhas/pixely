package app.pixely.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pixely.data.db.GridPhoto
import app.pixely.ui.theme.Figtree
import app.pixely.ui.theme.PixelyColors
import app.pixely.ui.thumbs.Thumbnail
import coil3.compose.AsyncImage

/** The pill-shaped text field from the design; [onSearch] fires on the keyboard's search key. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focus: FocusRequester? = null,
    highlighted: Boolean = false,
    height: Dp = 52.dp,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = PixelyColors.Text, fontFamily = Figtree, fontSize = 16.sp),
        cursorBrush = SolidColor(PixelyColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(value) }),
        modifier = modifier.then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
        decorationBox = { field ->
            Row(
                Modifier
                    .height(height)
                    .clip(RoundedCornerShape(height / 2))
                    .background(PixelyColors.Field)
                    .then(if (highlighted) Modifier.border(2.dp, PixelyColors.Accent, RoundedCornerShape(height / 2)) else Modifier)
                    .padding(start = 16.dp, end = if (trailing != null) 6.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                leading?.invoke()
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = PixelyColors.TextFaint, fontSize = 16.sp)
                    field()
                }
                trailing?.invoke()
            }
        },
    )
}

/** An outlined suggestion or filter chip; [accent] is the "Without" style. */
@Composable
fun Chip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    height: Dp = 34.dp,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    trailingLabel: String? = null,
    onTrailing: (() -> Unit)? = null,
) {
    val color = if (accent) PixelyColors.AccentText else PixelyColors.Text
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(if (accent) PixelyColors.AccentTint else Color.Transparent)
            .border(BorderStroke(1.dp, if (accent) PixelyColors.Accent else PixelyColors.Line), RoundedCornerShape(height / 2))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = if (trailing != null) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.let { Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(14.dp)) }
        Text(label, color = color, fontSize = if (height > 34.dp) 14.sp else 13.sp, fontWeight = FontWeight.Medium)
        trailing?.let {
            Icon(
                it, contentDescription = trailingLabel, tint = color,
                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).then(if (onTrailing != null) Modifier.clickable(onClick = onTrailing) else Modifier).padding(3.dp),
            )
        }
    }
}

/** Results, best first, three across as in the design. */
@Composable
fun ResultsGrid(photos: List<GridPhoto>, onOpen: (Int) -> Unit, modifier: Modifier = Modifier, bottom: Dp = 16.dp) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = bottom),
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(photos, key = { _, p -> p.id }) { i, photo ->
            AsyncImage(
                model = Thumbnail(photo.uri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .background(PixelyColors.Field)
                    .semantics { contentDescription = "Result ${i + 1}" }
                    .clickable { onOpen(i) },
            )
        }
    }
}

/** Uppercase section label, as "TRY" and "RECENT" in the design. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, color = PixelyColors.TextFaint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
}
