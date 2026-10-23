package app.pixely

import app.pixely.data.search.SearchQuery
import app.pixely.ui.search.DateFilter
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTest {

    @Test
    fun `no and without become the exclusion`() {
        assertEquals(SearchQuery("city at night", "people"), SearchQuery.parse("city at night, no people"))
        assertEquals(SearchQuery("beach", "umbrellas"), SearchQuery.parse("beach without umbrellas"))
        assertEquals(SearchQuery("a kitchen", "people"), SearchQuery.parse("a kitchen with no people"))
        assertEquals(SearchQuery("", "people"), SearchQuery.parse("no people"))
        assertEquals("a photo", SearchQuery.parse("no people").words)
        assertEquals(SearchQuery("red jacket, white sneakers"), SearchQuery.parse("red jacket, white sneakers"))
    }

    @Test
    fun `words that only contain no are left alone`() {
        assertEquals(SearchQuery("snow on a mountain"), SearchQuery.parse("snow on a mountain"))
        assertEquals(SearchQuery("notebook"), SearchQuery.parse("notebook"))
    }

    @Test
    fun `date filters cover whole days`() {
        val today = LocalDate.of(2026, 10, 23)
        val day = 24 * 3600_000L
        val utc = ZoneOffset.UTC
        assertNull(DateFilter.ANY.range(today, utc))
        val year = DateFilter.THIS_YEAR.range(today, utc)!!
        assertEquals(LocalDate.of(2026, 1, 1).toEpochDay() * day, year.from)
        assertEquals(LocalDate.of(2026, 10, 24).toEpochDay() * day, year.until)
        val last = DateFilter.LAST_YEAR.range(today, utc)!!
        assertEquals(LocalDate.of(2025, 1, 1).toEpochDay() * day, last.from)
        assertEquals(year.from, last.until)
        assertEquals(30 * day, DateFilter.DAYS_30.range(today, utc)!!.let { it.until - it.from })
    }
}
