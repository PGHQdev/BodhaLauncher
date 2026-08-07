package com.bodhalauncher.engine

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * ADR 0003: the day boundary is 4:00am local. The day key is the local date
 * shifted back four hours, so 1am still belongs to the previous evening's day.
 */
fun dayKey(now: LocalDateTime): LocalDate = now.minusHours(4).toLocalDate()

/**
 * Where a day began: 4:00am of that day key (ADR 0003).
 *
 * The 4am literal lives here and nowhere else, which is what this file is
 * deliberately the only home of. Awareness's Week view needs the start of a day
 * it names rather than the start of the current one (#176), and a second
 * `atTime(4, 0)` somewhere else is how a boundary quietly becomes two.
 */
fun dayStart(day: LocalDate): LocalDateTime = day.atTime(4, 0)

/** Where "today" began: the same rule, over the day [now] falls in. */
fun dayStart(now: LocalDateTime): LocalDateTime = dayStart(dayKey(now))

/**
 * The stored daily intention. The record survives its own expiry — Today offers
 * yesterday's text as a suggestion — but [textOn] yields nothing once stale;
 * the intention line never shows old content.
 */
data class DailyIntention(val text: String, val dayKey: LocalDate) {
    fun textOn(now: LocalDateTime): String? = text.takeIf { dayKey == dayKey(now) }
}
