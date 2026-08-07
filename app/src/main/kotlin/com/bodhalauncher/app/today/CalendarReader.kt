package com.bodhalauncher.app.today

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.bodhalauncher.engine.DayEvent
import com.bodhalauncher.engine.ProviderInstance
import com.bodhalauncher.engine.dayStart
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The day slot's Android edge (#159): reads the calendar provider's expanded
 * instances and opens an event in the calendar app. Only reads — filtering,
 * ordering and falloff are the engine's `resolveDaySlot`, and nothing is ever
 * stored (ADR 0017: no new store).
 */
class CalendarReader(private val context: Context) {

    /**
     * Whether the provider reports any calendar rows at all, visible or not.
     * Visible-but-all-hidden is the user's own choice in their calendar app and
     * reads as granted-and-empty, not as this.
     */
    fun hasCalendars(): Boolean =
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID), null, null, null,
        )?.use { it.count > 0 } ?: false

    /**
     * Expanded instances overlapping [from]..[until], every calendar included —
     * hidden ones carry their visibility for the reducer to drop. All-day
     * instances arrive as UTC midnights and convert accordingly.
     */
    fun instances(from: LocalDateTime, until: LocalDateTime): List<ProviderInstance> {
        val zone = ZoneId.systemDefault()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, from.atZone(zone).toInstant().toEpochMilli())
        ContentUris.appendId(builder, until.atZone(zone).toInstant().toEpochMilli())
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.VISIBLE,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
        )
        val rows = mutableListOf<ProviderInstance>()
        context.contentResolver.query(builder.build(), projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val allDay = cursor.getInt(2) == 1
                rows += ProviderInstance(
                    eventId = cursor.getLong(0),
                    title = cursor.getString(1).orEmpty().ifEmpty { "(No title)" },
                    allDay = allDay,
                    begin = localTime(cursor.getLong(3), allDay),
                    end = localTime(cursor.getLong(4), allDay),
                    calendarVisible = cursor.getInt(5) == 1,
                    selfDeclined = cursor.getInt(6) ==
                        CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED,
                )
            }
        }
        return rows
    }

    /**
     * The current day key's window, 4am to 4am (ADR 0003). Starting at the day's
     * start rather than at `now` keeps in-progress and all-day instances in the
     * read; what has already ended is the reducer's to drop.
     */
    fun todayWindow(now: LocalDateTime): List<ProviderInstance> =
        instances(dayStart(now), dayStart(now).plusDays(1))

    /** Tapping a row opens the event in the calendar app; Bodha ships no event view. */
    fun open(event: DayEvent) {
        val zone = if (event.allDay) ZoneOffset.UTC else ZoneId.systemDefault()
        val intent = Intent(Intent.ACTION_VIEW)
            .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
            .putExtra(
                CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                event.begin.atZone(zone).toInstant().toEpochMilli(),
            )
            .putExtra(
                CalendarContract.EXTRA_EVENT_END_TIME,
                event.end.atZone(zone).toInstant().toEpochMilli(),
            )
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.allDay)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun localTime(epochMillis: Long, allDay: Boolean): LocalDateTime =
        LocalDateTime.ofInstant(
            Instant.ofEpochMilli(epochMillis),
            if (allDay) ZoneOffset.UTC else ZoneId.systemDefault(),
        )
}
