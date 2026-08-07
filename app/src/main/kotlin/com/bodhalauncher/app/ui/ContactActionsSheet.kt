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
import com.bodhalauncher.engine.SearchContact

/**
 * A contact result's Actions node (#186): call and message live here rather
 * than on the tap — the tap is the non-destructive open, and a mis-tap must
 * not place a call. Without a stored number the sheet offers only the open,
 * so no row ever leads to a dialer with nothing in it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactActionsSheet(
    contact: SearchContact,
    /** The contact's primary number, or null when none is stored. */
    phoneNumber: String?,
    onOpen: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
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
                // A contact's name is machinery, not Bodha's voice (ADR 0021).
                text = contact.name,
                color = colors.ink,
                style = BodhaType.title,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            SheetRow("Open contact", onOpen)
            if (phoneNumber != null) {
                SheetRow("Call", onCall)
                SheetRow("Message", onMessage)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            Spacer(Modifier.height(24.dp))
        }
    }
}
