package com.example.samsungcalendarclone.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Which occurrences of a repeating event a change applies to. */
enum class RecurrenceScope(val label: String) {
    THIS("This event"),
    FOLLOWING("This and following events"),
    ALL("All events"),
}

private fun ruleParts(rule: String) = rule.split(';').map { it.trim() }.filter { it.isNotEmpty() }
private fun String.key() = substringBefore('=').uppercase()

/** The `COUNT` of an RRULE, or null when the rule has none. */
fun ruleCount(rule: String): Int? = ruleParts(rule).firstOrNull { it.key() == "COUNT" }?.substringAfter('=')?.toIntOrNull()

/** RFC 5545 UNTIL ending just before [occurrenceBegin]: a date for all-day series, a UTC time otherwise. */
fun untilBefore(occurrenceBegin: Long, allDay: Boolean): String =
    if (allDay) Instant.ofEpochMilli(occurrenceBegin).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
        .format(DateTimeFormatter.BASIC_ISO_DATE)
    else Instant.ofEpochMilli(occurrenceBegin - 1000).atZone(ZoneOffset.UTC)
        .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))

/** Replaces any COUNT/UNTIL of [rule] with `UNTIL=[until]`, ending the series before a split point. */
fun ruleEndingAt(rule: String, until: String): String =
    (ruleParts(rule).filter { it.key() != "COUNT" && it.key() != "UNTIL" } + "UNTIL=$until").joinToString(";")

/** The rule for the new series after a split; a COUNT keeps only the [remaining] occurrences. */
fun ruleForFollowing(rule: String, remaining: Int?): String {
    val parts = ruleParts(rule)
    if (parts.none { it.key() == "COUNT" }) return parts.joinToString(";")
    requireNotNull(remaining) { "Missing remaining count" }
    return (parts.filter { it.key() != "COUNT" } + "COUNT=$remaining").joinToString(";")
}

private val exdateFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

/** EXDATE value for one occurrence, in the UTC form the provider matches against instance starts. */
fun exdateFor(occurrenceBegin: Long): String = Instant.ofEpochMilli(occurrenceBegin).atZone(ZoneOffset.UTC).format(exdateFormat)

fun exdatesWith(existing: String?, occurrenceBegin: Long): String =
    (existing.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() } + exdateFor(occurrenceBegin)).distinct().joinToString(",")

/** EXDATE entries before [time], or null when the list uses a form this app does not parse (such as TZID). */
fun exdatesBefore(exdates: String?, time: Long): Int? {
    val entries = exdates.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val instants = entries.map { runCatching {
        java.time.LocalDateTime.parse(it, exdateFormat).toInstant(ZoneOffset.UTC).toEpochMilli()
    }.getOrNull() ?: return null }
    return instants.count { it < time }
}

/** Provider DURATION for a series: whole days for all-day events, seconds otherwise. */
fun seriesDuration(begin: Long, end: Long, allDay: Boolean): String {
    val length = Duration.ofMillis(end - begin)
    return if (allDay) "P${maxOf(1, length.toDays())}D" else "P${length.seconds}S"
}

/**
 * Moves a series start by the wall-clock change made to one occurrence, so a DST change
 * between the first and the edited occurrence does not shift the series by an hour.
 */
fun shiftedSeriesStart(seriesStart: Long, occurrenceBegin: Long, newBegin: Long, zone: ZoneId): Long {
    fun local(millis: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    val change = Duration.between(local(occurrenceBegin), local(newBegin))
    val shifted = local(seriesStart).plus(change)
    val offsets = zone.rules.getValidOffsets(shifted)
    require(offsets.isNotEmpty()) { "That time doesn’t exist on the first day of this series because of a clock change." }
    return shifted.atZone(zone).toInstant().toEpochMilli()
}
