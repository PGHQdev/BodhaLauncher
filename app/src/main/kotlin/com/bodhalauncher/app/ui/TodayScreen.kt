package com.bodhalauncher.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Today (#158, ADR 0017): the day key's date as a header, then the intention
 * slot. Renders what it is given and nothing else; the day key comes from the
 * engine, so the header and the slot cannot disagree about what day it is.
 */
@Composable
fun TodayScreen(
    day: LocalDate,
    intention: String?,
    onEditIntention: () -> Unit,
) {
    val colors = LocalBodhaColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .safeDrawingPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        // The device locale's medium date form; Settings' date-format control
        // (#141) reads into this header when it lands.
        Text(
            text = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
            color = colors.ink,
            style = BodhaType.title,
        )
        Spacer(Modifier.height(36.dp))
        // The slot is a block acted on once (ADR 0025); tinted when it holds
        // the current thing, plain in its named empty state.
        CardRow(
            title = intention ?: "Set today's intention",
            onClick = onEditIntention,
            emphasis = if (intention != null) Emphasis.Tinted else Emphasis.Plain,
            // Focus on arrival gives the surface a back key (ADR 0022).
            modifier = Modifier.focusOnOpen(),
        )
    }
}
