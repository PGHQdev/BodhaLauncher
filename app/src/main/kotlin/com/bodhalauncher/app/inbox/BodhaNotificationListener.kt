package com.bodhalauncher.app.inbox

import android.app.Notification
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateOf
import android.app.PendingIntent
import com.bodhalauncher.app.data.BodhaDatabase
import com.bodhalauncher.engine.IMPORTANCE_DEFAULT
import com.bodhalauncher.engine.InboxRow
import com.bodhalauncher.engine.NotificationSignals
import com.bodhalauncher.engine.classifyNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * The digest's one edge into the shade (#161, ADR 0015). Declared in the
 * manifest but inert until the user grants access through the education flow;
 * Android binds it only after the grant. Reads signals, never content: what is
 * written down is the classifier's placement plus timestamps, in a type that
 * cannot represent a title, a body or a sender name.
 */
class BodhaNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        instance = this
        connected.value = true
        // The shade as it stands counts too — notifications that arrived before
        // the bind, or survived a reboot, still describe the day. The live rows
        // rebuild from the same walk, so after a reboot the inbox holds exactly
        // what the shade holds: nothing until notifications re-arrive.
        live.value = emptyMap()
        activeNotifications?.forEach { record(it) }
    }

    override fun onListenerDisconnected() {
        connected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        record(sbn, rankingMap)
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap?,
        reason: Int,
    ) {
        // REASON_LOCKDOWN obliges deleting our copy; with metadata-only storage
        // that means dropping the row (ADR 0015). Every other removal — the user
        // clearing the shade included — leaves the day's count standing.
        if (reason == REASON_LOCKDOWN) {
            val hash = keyHash(sbn.key)
            scope.launch { BodhaDatabase.get(applicationContext).notificationLog().deleteByKey(hash) }
        }
        // The row goes however the notification went — rows describe now, and
        // only the count keeps describing the day (ADR 0015).
        live.value = live.value - sbn.key
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        // In-memory only, so the rows go with the process that held them.
        live.value = emptyMap()
        scope.cancel()
        super.onDestroy()
    }

    private fun record(sbn: StatusBarNotification, rankingMap: RankingMap? = null) {
        // The default SMS and phone role holders are excluded at the source:
        // not rendered, not stored, not counted (ADR 0015). Read per event, so
        // a change of default app excludes the new holder from that moment.
        if (sbn.packageName in excludedPackages()) return

        val ranking = Ranking().takeIf {
            (rankingMap ?: currentRanking).getRanking(sbn.key, it)
        }
        val placement = classifyNotification(signals(sbn.notification, ranking))
        // The live row the inbox renders (#162): keyed by the system's own key,
        // so a conversation updating in place stays one row showing the latest
        // state. What the system gave — a redacted notification included — is
        // held in memory only and never written anywhere.
        live.value = live.value + (sbn.key to LiveNotification(
            row = InboxRow(
                key = sbn.key,
                appPackage = sbn.packageName,
                section = placement.section,
                postedAtMillis = sbn.postTime,
            ),
            title = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            line = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            contentIntent = sbn.notification.contentIntent,
        ))
        val entity = NotificationRecordEntity(
            keyHash = keyHash(sbn.key),
            appPackage = sbn.packageName,
            section = placement.section.name,
            category = sbn.notification.category,
            postedAtMillis = sbn.postTime,
            updatedAtMillis = System.currentTimeMillis(),
        )
        scope.launch { BodhaDatabase.get(applicationContext).notificationLog().upsert(entity) }
    }

    // With no ranking to read, the neutral signals send the notification to the
    // classifier's catch-all rather than misfiling it as Silent on a signal
    // that was never actually read (#161).
    private fun signals(notification: Notification, ranking: Ranking?): NotificationSignals =
        NotificationSignals(
            rankingSaysConversation =
                if (Build.VERSION.SDK_INT >= 31) ranking?.isConversation else null,
            hasMessagingStyle = notification.extras.getString(Notification.EXTRA_TEMPLATE) ==
                Notification.MessagingStyle::class.java.name,
            category = notification.category,
            importance = ranking?.importance ?: IMPORTANCE_DEFAULT,
            everAudiblyAlerted = ranking == null || ranking.lastAudiblyAlertedMillis > 0,
        )

    private fun excludedPackages(): Set<String> = setOfNotNull(
        Telephony.Sms.getDefaultSmsPackage(this),
        getSystemService(TelecomManager::class.java)?.defaultDialerPackage,
    )

    companion object {
        /** Whether the system currently holds the listener bound; the slot names a drop (#161). */
        val connected = mutableStateOf(false)

        /**
         * The live shade, keyed by the system's notification key (#162): what
         * the inbox renders, in memory only. Rebuilt on every bind, emptied
         * with the process — a reboot leaves it empty until notifications
         * re-arrive, which reads as nothing waiting rather than an error.
         */
        val live = mutableStateOf<Map<String, LiveNotification>>(emptyMap())

        private var instance: BodhaNotificationListener? = null

        fun keyHash(key: String): String =
            MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}

/**
 * One live notification as the inbox shows it (#162): the engine's placement,
 * plus what the system gave us to display — a redacted notification carries
 * whatever the system left in — and the notification's own content intent, so
 * opening a row goes straight to its destination with no trampoline through
 * Bodha. Never persisted; a field of this class is not a field of any entity.
 */
class LiveNotification(
    val row: InboxRow,
    val title: String?,
    val line: String?,
    val contentIntent: PendingIntent?,
)
