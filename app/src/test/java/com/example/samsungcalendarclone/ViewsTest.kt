package com.example.samsungcalendarclone

import com.example.samsungcalendarclone.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class ViewsTest {
    private val athens = ZoneId.of("Europe/Athens")
    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = date.atTime(hour, minute).atZone(athens).toInstant().toEpochMilli()
    private fun event(id: Long, date: LocalDate, from: Pair<Int, Int>, to: Pair<Int, Int>, endDate: LocalDate = date) =
        CalendarEvent(id, 1, "Event $id", at(date, from.first, from.second), at(endDate, to.first, to.second), false, 0, "")

    @Test fun weeksStartOnMonday() {
        val days = weekDates(LocalDate.of(2026, 10, 10))
        assertEquals(LocalDate.of(2026, 10, 5), days.first())
        assertEquals(LocalDate.of(2026, 10, 11), days.last())
        assertEquals(LocalDate.of(2026, 10, 5), weekDates(LocalDate.of(2026, 10, 5)).first())
    }
    @Test fun weekLabelsShortenSharedParts() {
        assertEquals("5 – 11 Oct", weekLabel(weekDates(LocalDate.of(2026, 10, 10)), Locale.US))
        assertEquals("28 Sep – 4 Oct", weekLabel(weekDates(LocalDate.of(2026, 10, 1)), Locale.US))
        assertEquals("28 Dec 2026 – 3 Jan 2027", weekLabel(weekDates(LocalDate.of(2026, 12, 30)), Locale.US))
    }
    @Test fun overlappingEventsShareColumnsAndSeparateGroupsDoNot() {
        val day = LocalDate.of(2026, 10, 12)
        val blocks = layoutDay(listOf(
            event(1, day, 9 to 0, 10 to 0), event(2, day, 9 to 30, 11 to 0), event(3, day, 10 to 0, 10 to 30),
            event(4, day, 14 to 0, 15 to 0),
        ), day, athens).associateBy { it.event.id }
        assertEquals(listOf(0, 1, 0), listOf(1L, 2L, 3L).map { blocks[it]!!.column })
        assertTrue(listOf(1L, 2L, 3L).all { blocks[it]!!.columns == 2 })
        assertEquals(0 to 1, blocks[4]!!.column to blocks[4]!!.columns)
        assertEquals(9 * 60 to 10 * 60, blocks[1]!!.startMinute to blocks[1]!!.endMinute)
    }
    @Test fun eventsCrossingMidnightAreClippedToEachDay() {
        val day = LocalDate.of(2026, 10, 12)
        val overnight = event(1, day, 22 to 0, 2 to 0, day.plusDays(1))
        assertEquals(22 * 60 to MINUTES_PER_DAY, layoutDay(listOf(overnight), day, athens).single().let { it.startMinute to it.endMinute })
        assertEquals(0 to 120, layoutDay(listOf(overnight), day.plusDays(1), athens).single().let { it.startMinute to it.endMinute })
    }
    @Test fun shortEventsGetMinimumHeightAndAllDayEventsAreLeftOut() {
        val day = LocalDate.of(2026, 10, 12)
        val blocks = layoutDay(listOf(event(1, day, 23 to 55, 23 to 55),
            CalendarEvent(2, 1, "All day", at(day, 0), at(day.plusDays(1), 0), true, 0, "")), day, athens)
        assertEquals(1, blocks.size)
        assertEquals(MINUTES_PER_DAY - 20 to MINUTES_PER_DAY, blocks.single().startMinute to blocks.single().endMinute)
    }
}
