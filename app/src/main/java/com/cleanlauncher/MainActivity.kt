package com.cleanlauncher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import com.cleanlauncher.data.AppRepository
import com.cleanlauncher.ui.LauncherRoot

class MainActivity : ComponentActivity() {

    private val homePress = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        AppRepository.init(this)
        setContent {
            LauncherRoot(this, homePress.intValue)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home-Taste gedrückt, während der Launcher schon sichtbar war
        val alreadyOnHome = hasWindowFocus() &&
            (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) == 0
        if (Intent.ACTION_MAIN == intent.action && alreadyOnHome) {
            homePress.intValue++
        }
    }
}
