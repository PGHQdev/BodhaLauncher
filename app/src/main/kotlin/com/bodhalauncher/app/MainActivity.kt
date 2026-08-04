package com.bodhalauncher.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bodhalauncher.app.ui.BodhaTheme
import com.bodhalauncher.app.ui.HomeScreen
import com.bodhalauncher.app.ui.LocalBodhaColors
import com.bodhalauncher.engine.HomeInputs
import com.bodhalauncher.engine.resolveHome

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val intentPrompt = (application as BodhaApp).intentPrompt
        setContent {
            BodhaTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Inputs fill in as their features ship (pins #52, intention #53, …).
                    HomeScreen(state = resolveHome(HomeInputs()))

                    // Temporary prompt-due signal; the bottom sheet (#55) replaces it.
                    val due by intentPrompt.promptDue
                    if (due != null) {
                        Text(
                            text = "What are you here for?",
                            color = LocalBodhaColors.current.accent,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .safeDrawingPadding()
                                .padding(bottom = 96.dp)
                                .clickable { intentPrompt.dismiss() },
                        )
                    }
                }
            }
        }
    }
}
