package com.example.samsungcalendarclone

import com.example.samsungcalendarclone.domain.model.editorInstant
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class EventTimesTest {
    private val athens = ZoneId.of("Europe/Athens")
    @Test(expected = IllegalArgumentException::class) fun rejectsNonexistentSpringTime() {
        editorInstant(LocalDate.of(2026, 3, 29), 3 * 60 + 30, athens)
    }
    @Test fun preservesOriginalOverlapOffsetAndSubminutePrecision() {
        val original = Instant.parse("2026-10-25T01:30:24.123Z")
        assertEquals(original, editorInstant(LocalDate.of(2026, 10, 25), 3 * 60 + 30, athens, original.toEpochMilli()))
    }
    @Test fun changedTimeRetainsValidOriginalOverlapOffset() {
        val original = Instant.parse("2026-10-25T01:30:00Z")
        assertEquals(Instant.parse("2026-10-25T01:45:00Z"), editorInstant(LocalDate.of(2026, 10, 25), 3 * 60 + 45, athens, original.toEpochMilli()))
    }
    @Test fun newAmbiguousTimeUsesEarlierOffset() {
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), editorInstant(LocalDate.of(2026, 10, 25), 3 * 60 + 30, athens))
    }
}
