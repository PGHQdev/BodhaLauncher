package com.bodhalauncher.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.bodhalauncher.app.capability.CapabilityEducation
import com.bodhalauncher.app.data.BodhaDatabase
import com.bodhalauncher.app.home.IntentionStore
import com.bodhalauncher.app.inbox.BodhaNotificationListener
import com.bodhalauncher.app.today.CalendarReader
import com.bodhalauncher.app.ui.IntentionSheet
import com.bodhalauncher.app.ui.Sheet
import com.bodhalauncher.app.ui.SheetSlot
import com.bodhalauncher.app.ui.TodayScreen
import com.bodhalauncher.engine.Capability
import com.bodhalauncher.engine.DigestSection
import com.bodhalauncher.engine.DigestSlot
import com.bodhalauncher.engine.EducationEntry
import com.bodhalauncher.engine.dayKey
import com.bodhalauncher.engine.dayStart
import com.bodhalauncher.engine.resolveDaySlot
import com.bodhalauncher.engine.resolveDigestSlot
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Today as a surface of its own (#158): the day surface, and the one place the
 * intention is set from here on. The whole surface runs on the 4am day key
 * (ADR 0003), ticking each minute so crossing 4:00am on screen clears the
 * intention and rolls the header without a relaunch.
 */
@Composable
fun TodaySurface(
    intentionStore: IntentionStore,
    sheets: SheetSlot,
    education: CapabilityEducation,
    openInbox: () -> Unit,
) {
    val intention by intentionStore.intention
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay((60 - LocalDateTime.now().second) * 1000L)
            value = LocalDateTime.now()
        }
    }
    val day = dayKey(now)
    val text = intention?.textOn(now)
    // The editor is about this surface, so it leaves with it — the system Home
    // button must not park it in the app-wide slot (#132, ADR 0011).
    DisposableEffect(Unit) {
        onDispose { sheets.showing<Sheet.IntentionEditor>()?.let(sheets::close) }
    }
    val context = LocalContext.current
    val calendar = remember { CalendarReader(context) }
    // Read live on every tick and every return to the foreground; nothing is
    // cached, so a revoked grant is a plain ungranted read (ADR 0017, ADR 0009).
    val calendarGranted = education.granted(Capability.Calendar)
    val daySlot = remember(now, calendarGranted, education.resumeTick) {
        resolveDaySlot(
            granted = calendarGranted,
            educationShown = education.educationShown(Capability.Calendar),
            hasCalendars = !calendarGranted || calendar.hasCalendars(),
            instances = if (calendarGranted) calendar.todayWindow(now) else emptyList(),
            now = now,
            tomorrowInstances = if (calendarGranted) calendar.tomorrowWindow(now) else emptyList(),
        )
    }
    // The digest (#161): counts from the store under the day key, resolved
    // against the live grant and listener state so a drop or a revocation
    // renders as its named cause with the counts still standing.
    val digestGranted = education.granted(Capability.NotificationAccess)
    val listenerConnected by BodhaNotificationListener.connected
    val zone = ZoneId.systemDefault()
    val digestSlot by produceState<DigestSlot?>(
        null, now, digestGranted, listenerConnected, education.resumeTick,
    ) {
        val from = dayStart(now).atZone(zone).toInstant().toEpochMilli()
        val until = dayStart(now).plusDays(1).atZone(zone).toInstant().toEpochMilli()
        val counts = BodhaDatabase.get(context).notificationLog()
            .countsBetween(from, until)
            .associate { DigestSection.valueOf(it.section) to it.count }
        value = resolveDigestSlot(
            granted = digestGranted,
            educationShown = education.educationShown(Capability.NotificationAccess),
            listenerConnected = listenerConnected,
            sectionCounts = counts,
        )
    }
    TodayScreen(
        day = day,
        intention = text,
        onEditIntention = { sheets.open(Sheet.IntentionEditor()) },
        daySlot = daySlot,
        onEventTap = calendar::open,
        // A feature touch: arriving on Today never fires a system dialog; the
        // education sheet comes first, the runtime request only after (#157).
        onDayTurnOn = { education.ask(Capability.Calendar, EducationEntry.FeatureTouch) },
        digestSlot = digestSlot,
        onDigestTap = openInbox,
        onDigestTurnOn = { education.ask(Capability.NotificationAccess, EducationEntry.FeatureTouch) },
    )
    sheets.showing<Sheet.IntentionEditor>()?.let { sheet ->
        val dismiss = sheets.dismissedBy(sheet) { sheets.close(sheet) }
        IntentionSheet(
            current = text,
            // Kept for exactly one day: only the previous day key's text offers.
            suggestion = intention?.takeIf { it.dayKey == day.minusDays(1) }?.text,
            onSave = { intentionStore.set(it, LocalDateTime.now()); sheets.close(sheet) },
            onClear = { intentionStore.clear(); sheets.close(sheet) },
            onDismiss = dismiss,
        )
    }
}
