package com.example.samsungcalendarclone

import com.example.samsungcalendarclone.domain.model.untilNextDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class ClockTest {
    private val athens = ZoneId.of("Europe/Athens")
    @Test fun ordinaryEvening() {
        assertEquals(Duration.ofMinutes(90), untilNextDay(ZonedDateTime.of(2026, 10, 10, 22, 30, 0, 0, athens)))
    }
    @Test fun springForwardDayIsShorter() {
        // 29 March 2026 has 23 hours in Athens; from 00:00 the next day is 23 hours away.
        assertEquals(Duration.ofHours(23), untilNextDay(ZonedDateTime.of(2026, 3, 29, 0, 0, 0, 0, athens)))
    }
    @Test fun fallBackDayIsLonger() {
        assertEquals(Duration.ofHours(25), untilNextDay(ZonedDateTime.of(2026, 10, 25, 0, 0, 0, 0, athens)))
    }
}
