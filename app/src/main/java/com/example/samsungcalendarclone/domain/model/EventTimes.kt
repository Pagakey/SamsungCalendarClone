package com.example.samsungcalendarclone.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Preserve an unchanged timestamp (including seconds and DST overlap offset). Reject DST gaps. */
fun editorInstant(date: LocalDate, minute: Int, zone: ZoneId, originalMillis: Long? = null): Instant {
    val local = date.atTime(LocalTime.of(minute / 60, minute % 60))
    val original = originalMillis?.let { Instant.ofEpochMilli(it).atZone(zone) }
    if (original != null && original.toLocalDate() == date && original.hour * 60 + original.minute == minute) {
        return original.toInstant()
    }
    val offsets = zone.rules.getValidOffsets(local)
    require(offsets.isNotEmpty()) { "That time does not exist because the clocks move forward. Choose another time." }
    return local.toInstant(original?.offset?.takeIf { it in offsets } ?: offsets.first())
}
