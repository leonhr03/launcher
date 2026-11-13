package com.cleanlauncher.ui

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cleanlauncher.SettingsActivity
import com.cleanlauncher.data.AppInfo
import com.cleanlauncher.data.AppRepository
import com.cleanlauncher.data.GestureAction
import com.cleanlauncher.data.Prefs
import com.cleanlauncher.system.SystemActions

enum class IconLocation { HOME, DOCK, DRAWER }

/** Hält den UI-Zustand des Startbildschirms und alle Aktionen (Dock, Ausblenden, Tauschen …). */
class LauncherController(val context: Context, val prefs: Prefs) {

    var drawerOpen by mutableStateOf(false)
    var drawerFocus by mutableStateOf(false)
    var editMode by mutableStateOf(false)
    var selectedKey by mutableStateOf<String?>(null)
    var menuKey by mutableStateOf<String?>(null)
    var renameApp by mutableStateOf<AppInfo?>(null)
    var view: View? = null

    // Aktuell sichtbare Listen (werden von der UI bei jeder Komposition aktualisiert)
    var homeKeys: List<String> = emptyList()
    var dockKeys: List<String> = emptyList()

    fun haptic(long: Boolean = false) {
        if (!prefs.haptics) return
        view?.performHapticFeedback(
            if (long) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY,
        )
    }

    private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    // ---------------- Gesten ----------------

    fun perform(action: GestureAction) {
        if (editMode) return
        if (action != GestureAction.NONE) haptic()
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.APP_LIBRARY -> openDrawer(prefs.drawerAutoKeyboard)
            GestureAction.SEARCH -> openDrawer(true)
            GestureAction.NOTIFICATIONS -> SystemActions.expandNotifications(context)
            GestureAction.QUICK_SETTINGS -> SystemActions.expandQuickSettings(context)
            GestureAction.EDIT_MODE -> enterEditMode()
            GestureAction.LAUNCHER_SETTINGS -> openSettings()
            GestureAction.LOCK_SCREEN -> SystemActions.lockScreen(context)
            GestureAction.RECENTS -> SystemActions.recents(context)
        }
    }

    fun openDrawer(focus: Boolean) {
        drawerFocus = focus
        drawerOpen = true
    }

    fun closeDrawer() {
        drawerOpen = false
        drawerFocus = false
        menuKey = null
    }

    fun enterEditMode() {
        menuKey = null
        selectedKey = null
        editMode = true
        haptic(true)
    }

    fun exitEditMode() {
        editMode = false
        selectedKey = null
    }

    fun openSettings() {
        context.startActivity(
            Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun launch(app: AppInfo, bounds: Rect?) {
        menuKey = null
        AppRepository.launch(context, app, view, bounds, prefs.openAnimation)
    }

    // ---------------- Erster Start / neue Apps ----------------

    fun syncApps(apps: List<AppInfo>) {
        if (apps.isEmpty()) return
        val keys = apps.map { it.key }
        if (!prefs.initialized) {
            val dock = AppRepository.defaultDock(context, apps).take(prefs.dockCount)
            prefs.dockApps = dock
            prefs.homeOrder = keys.filter { it !in dock }
            prefs.knownApps = keys
            prefs.initialized = true
            return
        }
        val known = prefs.knownApps.toSet()
        val newKeys = keys.filter { it !in known }
        if (newKeys.isNotEmpty() && prefs.autoAddNewApps) {
            val existing = (prefs.homeOrder + prefs.dockApps).toSet()
            prefs.homeOrder = prefs.homeOrder + newKeys.filter { it !in existing }
        }
        if (known != keys.toSet()) prefs.knownApps = keys
    }

    // ---------------- Anordnung ----------------

    fun isInDock(key: String) = key in dockKeys
    fun isOnHome(key: String) = key in homeKeys

    fun addToDock(key: String) {
        if (!prefs.dockEnabled) {
            toast("Das Dock ist in den Einstellungen ausgeschaltet")
            return
        }
        if (dockKeys.size >= prefs.dockCount) {
            toast("Das Dock ist voll (max. ${prefs.dockCount})")
            return
        }
        prefs.dockApps = dockKeys + key
        prefs.homeOrder = homeKeys - key
    }

    fun removeFromDock(key: String) {
        prefs.dockApps = dockKeys - key
        prefs.homeOrder = homeKeys - key + key
    }

    fun removeFromHome(key: String) {
        prefs.homeOrder = homeKeys - key
        if (key in dockKeys) prefs.dockApps = dockKeys - key
    }

    fun addToHome(key: String) {
        if (key in homeKeys || key in dockKeys) return
        prefs.homeOrder = homeKeys + key
        toast("Zum Home-Bildschirm hinzugefügt")
    }

    fun hide(key: String) {
        prefs.hiddenApps = (prefs.hiddenApps - key) + key
        prefs.homeOrder = homeKeys - key
        prefs.dockApps = dockKeys - key
        toast("App ausgeblendet – in den Einstellungen wieder einblendbar")
    }

    fun unhide(key: String) {
        prefs.hiddenApps = prefs.hiddenApps - key
        if (key !in prefs.homeOrder && key !in prefs.dockApps) prefs.homeOrder = prefs.homeOrder + key
    }

    fun rename(key: String, label: String?) {
        prefs.customLabels = if (label.isNullOrBlank()) prefs.customLabels - key
        else prefs.customLabels + (key to label.trim())
    }

    /** Bearbeitungsmodus: erst ein Symbol antippen, dann ein zweites -> Plätze tauschen. */
    fun onEditTap(key: String) {
        val sel = selectedKey
        when {
            sel == null -> {
                selectedKey = key
                haptic()
            }
            sel == key -> selectedKey = null
            else -> {
                swap(sel, key)
                selectedKey = null
                haptic()
            }
        }
    }

    private fun swap(a: String, b: String) {
        val home = homeKeys.toMutableList()
        val dock = dockKeys.toMutableList()
        val ha = home.indexOf(a)
        val hb = home.indexOf(b)
        val da = dock.indexOf(a)
        val db = dock.indexOf(b)
        if (ha >= 0) home[ha] = b else if (da >= 0) dock[da] = b
        if (hb >= 0) home[hb] = a else if (db >= 0) dock[db] = a
        prefs.homeOrder = home
        prefs.dockApps = dock
    }

    /** Verschiebt das ausgewählte Symbol ans Ende des Home-Bildschirms. */
    fun moveSelectedToHomeEnd() {
        val key = selectedKey ?: return
        prefs.dockApps = dockKeys - key
        prefs.homeOrder = (homeKeys - key) + key
        selectedKey = null
        haptic()
    }

    fun moveSelectedToDock() {
        val key = selectedKey ?: return
        if (key in dockKeys) return
        selectedKey = null
        addToDock(key)
        haptic()
    }
}
