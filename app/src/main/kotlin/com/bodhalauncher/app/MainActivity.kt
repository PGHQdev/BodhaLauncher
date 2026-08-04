package com.bodhalauncher.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bodhalauncher.app.ui.BodhaTheme
import com.bodhalauncher.app.ui.HomeScreen
import com.bodhalauncher.engine.HomeInputs
import com.bodhalauncher.engine.resolveHome

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BodhaTheme {
                // Inputs fill in as their features ship (pins #52, intention #53, …).
                HomeScreen(state = resolveHome(HomeInputs()))
            }
        }
    }
}
