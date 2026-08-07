package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bodhalauncher.engine.SNOOZE_CHOICES

/**
 * The inbox row's actions (#163, #164): dealing with a notification once, on
 * the real notification. Same shape as the app actions sheet — the row's title
 * in the sans, since words the system gave are machinery (ADR 0021), over
 * hairline rows. Neither action stores anything, and nothing auto-replies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationActionsSheet(
    title: String,
    /** The source app's label, naming the mute row (#164). */
    sourceLabel: String,
    onHandled: () -> Unit,
    onSnooze: () -> Unit,
    /** Bodha-local (#164, ADR 0015): the app stops entering the inbox, the shade is untouched. */
    onMute: () -> Unit,
    /** Android's own notification settings for the source app — the real switch (#164). */
    onNotificationSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.ground) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .escapeDismisses(onDismiss)
                .focusOnOpen()
                .padding(horizontal = 28.dp),
        ) {
            Text(
                text = title,
                color = colors.ink,
                style = BodhaType.title,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            SheetRow("Handled", onHandled)
            SheetRow("Snooze", onSnooze)
            SheetRow("Mute $sourceLabel here", onMute)
            SheetRow("Notification settings", onNotificationSettings)
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * The snooze duration (#163): one decision, three fixed choices, dismissible
 * without consequence — nothing has been snoozed until a duration is chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoozeDurationSheet(
    onChoice: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.ground) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .escapeDismisses(onDismiss)
                .focusOnOpen()
                .padding(horizontal = 28.dp),
        ) {
            Text(
                text = "Snooze for",
                color = colors.ink,
                style = BodhaType.title,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            SNOOZE_CHOICES.forEach { choice ->
                SheetRow(choice.label) { onChoice(choice.durationMillis) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            Spacer(Modifier.height(24.dp))
        }
    }
}
