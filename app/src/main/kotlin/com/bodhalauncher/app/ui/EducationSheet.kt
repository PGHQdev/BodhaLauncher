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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
            Text(
                text = screen.feature,
                color = colors.ink,
                fontFamily = FontFamily.Serif,
                fontSize = 22.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Statement(screen.dataAccessed)
            Statement(screen.processing)
            Statement(screen.withoutIt)
            Spacer(Modifier.height(8.dp))
            Entry("Open Android settings", onContinue)
            Entry("Not now", onDismiss)
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Statement(text: String) {
    Text(
        text = text,
        color = LocalBodhaColors.current.inkMuted,
        fontSize = 14.sp,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun Entry(label: String, onClick: () -> Unit) {
    val colors = LocalBodhaColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Text(
            text = label,
            color = colors.ink,
            fontSize = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
        )
    }
}
