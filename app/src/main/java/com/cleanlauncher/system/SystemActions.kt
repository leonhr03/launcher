package com.cleanlauncher.system

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/** Optionale Bedienungshilfe – nur für "Bildschirm sperren", "Benachrichtigungen" und "Letzte Apps". */
class LauncherAccessibilityService : AccessibilityService() {
    var overlays: OverlayManager? = null
        private set

    override fun onServiceConnected() {
        instance = this
        overlays = OverlayManager(this).also { it.start() }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        overlays?.onConfigurationChanged()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        overlays?.stop()
        overlays = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: LauncherAccessibilityService? = null
    }
}

object SystemActions {

    val accessibilityEnabled: Boolean get() = LauncherAccessibilityService.instance != null

    fun openAccessibilitySettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    private fun needService(context: Context) {
        Toast.makeText(
            context,
            "Bitte aktiviere \"Clean Launcher – Gesten\" in den Bedienungshilfen",
            Toast.LENGTH_LONG,
        ).show()
        openAccessibilitySettings(context)
    }

    fun showShade(context: Context, kind: com.cleanlauncher.ui.ShadeKind) {
        val o = LauncherAccessibilityService.instance?.overlays
        if (o != null) o.showShade(kind) else needService(context)
    }

    fun lockScreen(context: Context) {
        val s = LauncherAccessibilityService.instance
        if (s != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Toast.makeText(context, "Benötigt Android 9 oder neuer", Toast.LENGTH_SHORT).show()
        } else {
            needService(context)
        }
    }

    fun recents(context: Context) {
        val s = LauncherAccessibilityService.instance
        if (s != null) s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) else needService(context)
    }

    fun expandNotifications(context: Context) {
        if (!statusBar(context, "expandNotificationsPanel")) {
            LauncherAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                ?: needService(context)
        }
    }

    fun expandQuickSettings(context: Context) {
        if (!statusBar(context, "expandSettingsPanel")) {
            LauncherAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                ?: needService(context)
        }
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    private fun statusBar(context: Context, method: String): Boolean = try {
        val service = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod(method).invoke(service)
        true
    } catch (e: Throwable) {
        false
    }

    fun setAsDefaultLauncher(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (i in intents) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
    }

    fun pickWallpaper(context: Context) {
        try {
            context.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Hintergrund wählen")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
        }
    }

    fun isDefaultLauncher(context: Context): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = context.packageManager.resolveActivity(i, 0)
        return res?.activityInfo?.packageName == context.packageName
    }
}
