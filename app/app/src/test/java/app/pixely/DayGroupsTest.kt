package app.pixely

import app.pixely.data.db.GridPhoto
import app.pixely.ui.photos.GridItem
import app.pixely.ui.photos.dayLabel
import app.pixely.ui.photos.groupByDay
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DayGroupsTest {

    private val today = LocalDate.of(2026, 10, 22)

    private fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `labels read like the design`() {
        assertEquals("Today", dayLabel(today, today, Locale.US))
        assertEquals("Yesterday", dayLabel(today.minusDays(1), today, Locale.US))
        assertEquals("Sat, Oct 17", dayLabel(LocalDate.of(2026, 10, 17), today, Locale.US))
        assertEquals("Fri, Oct 17, 2025", dayLabel(LocalDate.of(2025, 10, 17), today, Locale.US))
    }

    @Test
    fun `a header starts each day and cells keep their list position`() {
        val photos = listOf(
            GridPhoto(1, "a", at(today, 18), 1, 1),
            GridPhoto(2, "b", at(today, 9), 1, 1),
            GridPhoto(3, "c", at(today.minusDays(5), 12), 1, 1),
        )
        val items = groupByDay(photos, today, ZoneOffset.UTC, Locale.US)

        assertEquals(listOf("h:Today", "p:1", "p:2", "h:Sat, Oct 17", "p:3"), items.map { it.key })
        assertEquals(2, (items.last() as GridItem.Cell).index)
    }
}
