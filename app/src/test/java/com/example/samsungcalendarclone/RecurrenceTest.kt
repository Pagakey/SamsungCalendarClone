package com.example.samsungcalendarclone

import com.example.samsungcalendarclone.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class RecurrenceTest {
    private val athens = ZoneId.of("Europe/Athens")
    private fun ms(text: String) = Instant.parse(text).toEpochMilli()

    @Test fun untilEndsJustBeforeTheSplitOccurrence() {
        assertEquals("20300412T085959Z", untilBefore(ms("2030-04-12T09:00:00Z"), false))
        assertEquals("20300411", untilBefore(ms("2030-04-12T00:00:00Z"), true))
    }
    @Test fun endingRuleReplacesCountOrUntil() {
        assertEquals("FREQ=DAILY;UNTIL=20300411", ruleEndingAt("FREQ=DAILY;COUNT=5", "20300411"))
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20300411", ruleEndingAt("FREQ=WEEKLY;UNTIL=20301231;BYDAY=MO,WE", "20300411"))
    }
    @Test fun followingRuleKeepsRemainingCount() {
        assertEquals(5, ruleCount("FREQ=DAILY;COUNT=5"))
        assertNull(ruleCount("FREQ=DAILY"))
        assertEquals("FREQ=DAILY;COUNT=2", ruleForFollowing("FREQ=DAILY;COUNT=5", 2))
        assertEquals("FREQ=DAILY;UNTIL=20301231", ruleForFollowing("FREQ=DAILY;UNTIL=20301231", null))
    }
    @Test fun seriesDurationUsesDaysOrSeconds() {
        assertEquals("P3600S", seriesDuration(0, 3_600_000, false))
        assertEquals("P2D", seriesDuration(0, 2 * 86_400_000L, true))
    }
    @Test fun seriesShiftFollowsWallClockAcrossDst() {
        // First occurrence 09:00 EET, edited occurrence 09:00 EEST moved to 10:00: the series moves to 10:00 EET.
        assertEquals(ms("2026-03-01T08:00:00Z"),
            shiftedSeriesStart(ms("2026-03-01T07:00:00Z"), ms("2026-04-01T06:00:00Z"), ms("2026-04-01T07:00:00Z"), athens))
    }
    @Test(expected = IllegalArgumentException::class) fun seriesShiftIntoDstGapIsRejected() {
        // Series starts on the spring-forward day at 02:30; moving it an hour later lands in the gap.
        shiftedSeriesStart(ms("2026-03-29T00:30:00Z"), ms("2026-04-05T23:30:00Z"), ms("2026-04-06T00:30:00Z"), athens)
    }
}

class ExdateTest {
    @Test fun exdatesAppendInUtcAndCountBeforeASplit() {
        val begin = Instant.parse("2030-04-11T09:00:00Z").toEpochMilli()
        assertEquals("20300411T090000Z", exdateFor(begin))
        assertEquals("20300410T090000Z,20300411T090000Z", exdatesWith("20300410T090000Z", begin))
        assertEquals(1, exdatesBefore("20300410T090000Z,20300412T090000Z", begin))
        assertEquals(0, exdatesBefore(null, begin))
        assertNull(exdatesBefore("TZID=Europe/Athens;20300410T120000", begin))
    }
}
