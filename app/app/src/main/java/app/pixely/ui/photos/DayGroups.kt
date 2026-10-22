package app.pixely.ui.photos

import app.pixely.data.db.GridPhoto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One row of the Photos grid: a day header across the full width, or a photo cell. */
sealed interface GridItem {
    val key: String

    data class Header(val label: String) : GridItem {
        override val key get() = "h:$label"
    }

    /** [index] is the photo's position in the full list, which the viewer pages through. */
    data class Cell(val photo: GridPhoto, val index: Int) : GridItem {
        override val key get() = "p:${photo.id}"
    }
}

/** Photos are already newest first; a header goes in front of each new day. */
fun groupByDay(photos: List<GridPhoto>, today: LocalDate, zone: ZoneId, locale: Locale = Locale.getDefault()): List<GridItem> {
    val items = ArrayList<GridItem>(photos.size + photos.size / 8)
    var day: LocalDate? = null
    photos.forEachIndexed { i, photo ->
        val d = Instant.ofEpochMilli(photo.takenAt).atZone(zone).toLocalDate()
        if (d != day) {
            items += GridItem.Header(dayLabel(d, today, locale))
            day = d
        }
        items += GridItem.Cell(photo, i)
    }
    return items
}

/** "Today", "Yesterday", "Sat, Oct 17", or "Sat, Oct 17, 2025" outside this year. */
fun dayLabel(day: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String = when {
    day == today -> "Today"
    day == today.minusDays(1) -> "Yesterday"
    day.year == today.year -> day.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
    else -> day.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", locale))
}
