package com.bodhalauncher.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** 2x text for the dynamic-type goldens — layout must hold at this scale (#26). */
@Composable
internal fun LargeType(content: @Composable () -> Unit) {
    val density = LocalDensity.current.density
    CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f), content = content)
}
