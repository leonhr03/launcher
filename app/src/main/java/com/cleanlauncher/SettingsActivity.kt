package com.cleanlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cleanlauncher.data.AppRepository
import com.cleanlauncher.ui.SettingsScreen

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppRepository.init(this)
        setContent {
            SettingsScreen(onFinish = { finish() })
        }
    }
}
