package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.bodhalauncher.engine.EducationScreen

/**
 * The Bodha explanation screen (#18): a fact sheet, not a plea. States the data,
 * where it's processed, and what stays off; "Open Android settings" is the only
 * onward step. Serif feature name (the voice), sans statements (the machinery).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EducationSheet(
    screen: EducationScreen,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.ground) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = BodhaSpacing.page)) {
            Text(
                text = screen.feature,
                color = colors.ink,
                style = BodhaType.voiceTitle,
                modifier = Modifier.padding(vertical = BodhaSpacing.m),
            )
            Statement(screen.dataAccessed)
            Statement(screen.processing)
            Statement(screen.withoutIt)
            Spacer(Modifier.height(BodhaSpacing.s))
            ActionRow("Open Android settings", onContinue)
            ActionRow("Not now", onDismiss)
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            Spacer(Modifier.height(BodhaSpacing.xl))
        }
    }
}

@Composable
private fun Statement(text: String) {
    Text(
        text = text,
        color = LocalBodhaColors.current.inkMuted,
        style = BodhaType.label,
        modifier = Modifier.padding(vertical = BodhaSpacing.xs),
    )
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    val colors = LocalBodhaColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Text(
            text = label,
            color = colors.ink,
            style = BodhaType.body,
            modifier = Modifier
                .fillMaxWidth()
                .touchTargetFloor()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
        )
    }
}
