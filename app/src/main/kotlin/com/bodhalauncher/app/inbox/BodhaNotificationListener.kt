package com.bodhalauncher.app.inbox

import android.app.Notification
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateOf
import com.bodhalauncher.app.data.BodhaDatabase
import com.bodhalauncher.engine.IMPORTANCE_DEFAULT
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
        connected.value = true
        // The shade as it stands counts too — notifications that arrived before
        // the bind, or survived a reboot, still describe the day.
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
    }

    override fun onDestroy() {
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

        fun keyHash(key: String): String =
            MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
