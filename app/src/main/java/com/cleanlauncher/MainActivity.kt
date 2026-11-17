package com.cleanlauncher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.cleanlauncher.data.AppRepository
import com.cleanlauncher.data.MiuiImport
import com.cleanlauncher.data.Prefs
import com.cleanlauncher.data.WallpaperRepo
import com.cleanlauncher.lock.LockService
import com.cleanlauncher.ui.LauncherController
import com.cleanlauncher.ui.LauncherRoot
import com.cleanlauncher.widgets.Widgets

class MainActivity : ComponentActivity() {

    private val homePress = mutableIntStateOf(0)
    private lateinit var controller: LauncherController
    private var pendingWidget: Pair<Int, AppWidgetProviderInfo>? = null
    private var pendingPlacement: MiuiImport.PendingWidget? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        AppRepository.init(this)
        WallpaperRepo.init(this)
        Widgets.init(this)
        com.cleanlauncher.system.MediaRepo.init(this)
        controller = LauncherController(this, Prefs.get(this))
        controller.requestWidget = { info -> addWidget(info) }
        controller.requestReconfigure = { id -> reconfigure(id) }
        LockService.sync(this)
        setContent {
            LauncherRoot(this, controller, homePress.intValue)
        }
        // Sobald die Apps geladen sind, importierte Widgets anlegen
        lifecycleScope.launch {
            snapshotFlow { AppRepository.apps }.first { it.isNotEmpty() }
            delay(800)
            processImportedWidgets()
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            Widgets.host.startListening()
        } catch (_: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        processImportedWidgets()
    }

    /** Nach einem Xiaomi-Import: Widgets nacheinander wieder anlegen (an ihrer alten Position). */
    private fun processImportedWidgets() {
        if (pendingWidget != null || AppRepository.apps.isEmpty() || controller.byKey.isEmpty()) return
        val prefs = Prefs.get(this)
        while (prefs.pendingWidgets.isNotEmpty()) {
            val next = prefs.pendingWidgets.first()
            prefs.pendingWidgets = prefs.pendingWidgets.drop(1)
            val w = MiuiImport.PendingWidget.decode(next) ?: continue
            val info = Widgets.providers().firstOrNull { it.provider == w.provider } ?: continue
            addWidget(info, w)
            return
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            Widgets.host.stopListening()
        } catch (_: Exception) {
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val alreadyOnHome = hasWindowFocus() &&
            (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) == 0
        if (Intent.ACTION_MAIN == intent.action) {
            if (alreadyOnHome) homePress.intValue++
            else {
                controller.menu = null
                controller.closeDrawer()
                controller.openFolderId = null
            }
        }
    }

    // ---------------- Widgets hinzufügen ----------------

    private fun addWidget(info: AppWidgetProviderInfo, at: MiuiImport.PendingWidget? = null) {
        pendingPlacement = at
        val id = Widgets.host.allocateAppWidgetId()
        val ok = try {
            Widgets.manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)
        } catch (e: Exception) {
            false
        }
        pendingWidget = id to info
        if (ok) {
            configureOrPlace(id, info)
        } else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_BIND)
        }
    }

    private fun configureOrPlace(id: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            try {
                Widgets.host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null)
                return
            } catch (_: Exception) {
            }
        }
        pendingWidget = null
        controller.placeWidget(id, info, pendingPlacement)
        pendingPlacement = null
        processImportedWidgets()
    }

    private fun reconfigure(id: Int) {
        try {
            Widgets.host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_RECONFIGURE, null)
        } catch (_: Exception) {
            controller.toast("Dieses Widget lässt sich nicht bearbeiten")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val p = pendingWidget ?: return
        when (requestCode) {
            REQ_BIND -> if (resultCode == Activity.RESULT_OK) configureOrPlace(p.first, p.second)
            else {
                Widgets.delete(p.first)
                pendingWidget = null
                pendingPlacement = null
            }
            REQ_CONFIGURE -> {
                if (resultCode == Activity.RESULT_OK) controller.placeWidget(p.first, p.second, pendingPlacement)
                else Widgets.delete(p.first)
                pendingWidget = null
                pendingPlacement = null
            }
        }
    }

    companion object {
        private const val REQ_BIND = 11
        private const val REQ_CONFIGURE = 12
        private const val REQ_RECONFIGURE = 13
    }
}
