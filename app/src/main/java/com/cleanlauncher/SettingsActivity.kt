package com.cleanlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cleanlauncher.data.AppRepository
import com.cleanlauncher.data.WallpaperRepo
import com.cleanlauncher.ui.SettingsScreen

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppRepository.init(this)
        WallpaperRepo.init(this)
        if (intent?.getBooleanExtra("preview_lock", false) == true) {
            com.cleanlauncher.system.SystemActions.showLockOverlay()
        }
        setContent {
            SettingsScreen(onFinish = { finish() }, autoImport = intent?.getBooleanExtra("auto_import", false) == true)
        }
    }
}
