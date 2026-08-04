package com.bodhalauncher.engine

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * ADR 0003: the day boundary is 4:00am local. The day key is the local date
 * shifted back four hours, so 1am still belongs to the previous evening's day.
 */
fun dayKey(now: LocalDateTime): LocalDate = now.minusHours(4).toLocalDate()

/** Where "today" began: 4:00am of the current day key (ADR 0003). */
fun dayStart(now: LocalDateTime): LocalDateTime = dayKey(now).atTime(4, 0)

/**
 * The stored daily intention. The record survives its own expiry — Today offers
 * yesterday's text as a suggestion — but [textOn] yields nothing once stale;
 * the intention line never shows old content.
 */
data class DailyIntention(val text: String, val dayKey: LocalDate) {
    fun textOn(now: LocalDateTime): String? = text.takeIf { dayKey == dayKey(now) }
}
