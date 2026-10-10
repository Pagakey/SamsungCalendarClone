package com.example.samsungcalendarclone.domain.model

import java.time.Duration
import java.time.ZonedDateTime

/** Time until the next local day starts; DST days are 23 or 25 hours long. */
fun untilNextDay(now: ZonedDateTime): Duration =
    Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone))
