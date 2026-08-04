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
import androidx.compose.ui.unit.sp

/**
 * The session-end moment (#75): the boundary the user set for themselves,
 * honored without force. Serif phrase (the voice), sans rows (the machinery) —
 * ADR 0010. No countdown preceded it and nothing here fights the user:
 * dismissing is closing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionEndSheet(
    phrase: String,
    onClose: () -> Unit,
    onAddFive: () -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.ground) {
        SessionEndSheetContent(
            phrase = phrase,
            onClose = onClose,
            onAddFive = onAddFive,
            onContinue = onContinue,
        )
    }
}

/** The sheet's face, separate so the screenshot gate can photograph it (#26). */
@Composable
fun SessionEndSheetContent(
    phrase: String,
    onClose: () -> Unit,
    onAddFive: () -> Unit,
    onContinue: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
        Text(
            text = phrase,
            color = colors.ink,
            fontFamily = BodhaFaces.serif,
            fontSize = 22.sp,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        SheetRow("Close the app", onClose)
        SheetRow("Add 5 minutes", onAddFive)
        SheetRow("Continue without a timer", onContinue)
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Spacer(Modifier.height(24.dp))
    }
}
