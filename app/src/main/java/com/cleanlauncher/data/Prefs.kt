package com.cleanlauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class IconShape(val title: String) {
    ORIGINAL("Original (wie vom System)"),
    SQUIRCLE("Squircle (iOS)"),
    ROUNDED("Abgerundetes Quadrat"),
    CIRCLE("Kreis"),
    SQUARE("Quadrat"),
    TEARDROP("Tropfen"),
}

enum class WidgetStyle(val title: String) {
    LIGHT("Hell (iOS)"),
    DARK("Dunkel"),
    GLASS("Glas"),
}

enum class GestureAction(val title: String) {
    NONE("Nichts"),
    APP_LIBRARY("App-Mediathek öffnen"),
    SEARCH("Suche öffnen"),
    NOTIFICATIONS("Benachrichtigungen öffnen"),
    QUICK_SETTINGS("Schnelleinstellungen öffnen"),
    EDIT_MODE("Bearbeitungsmodus"),
    LAUNCHER_SETTINGS("Launcher-Einstellungen"),
    LOCK_SCREEN("Bildschirm sperren"),
    RECENTS("Zuletzt verwendete Apps"),
    NOTIFICATION_CENTER("Mitteilungszentrale (iOS)"),
    CONTROL_CENTER("Kontrollzentrum (iOS)"),
}

enum class DrawerStyle(val title: String) {
    IOS("App-Mediathek (Kategorien, wie iOS)"),
    LIST("Liste (alphabetisch)"),
    GRID("Raster"),
}

/**
 * Alle Einstellungen des Launchers. Jede Eigenschaft ist gleichzeitig Compose-State,
 * d. h. Änderungen in den Einstellungen wirken sofort auf dem Startbildschirm.
 */
class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("clean_launcher", Context.MODE_PRIVATE)

    private val all = mutableListOf<P<*>>()

    private inner class P<T>(
        val key: String,
        val def: T,
        val isLayout: Boolean,
        read: (SharedPreferences, String, T) -> T,
        val write: SharedPreferences.Editor.(String, T) -> Unit,
    ) : ReadWriteProperty<Any?, T> {
        val state = mutableStateOf(read(sp, key, def))

        init {
            all += this
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            sp.edit().apply { write(key, value) }.apply()
        }

        fun reset() {
            state.value = def
            sp.edit().remove(key).apply()
        }
    }

    private fun int(key: String, def: Int) =
        P(key, def, false, { s, k, d -> s.getInt(k, d) }, { k, v -> putInt(k, v) })

    private fun float(key: String, def: Float) =
        P(key, def, false, { s, k, d -> s.getFloat(k, d) }, { k, v -> putFloat(k, v) })

    private fun bool(key: String, def: Boolean, layout: Boolean = false) =
        P(key, def, layout, { s, k, d -> s.getBoolean(k, d) }, { k, v -> putBoolean(k, v) })

    private fun list(key: String) = P<List<String>>(
        key, emptyList(), true,
        { s, k, d -> s.getString(k, null)?.split('\n')?.filter { it.isNotEmpty() } ?: d },
        { k, v -> putString(k, v.joinToString("\n")) },
    )

    private fun map(key: String) = P<Map<String, String>>(
        key, emptyMap(), true,
        { s, k, d ->
            s.getString(k, null)?.split('\n')?.mapNotNull { line ->
                val i = line.indexOf('\t')
                if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
            }?.toMap() ?: d
        },
        { k, v -> putString(k, v.entries.joinToString("\n") { "${it.key}\t${it.value.replace('\n', ' ').replace('\t', ' ')}" }) },
    )

    private inline fun <reified E : Enum<E>> enum(key: String, def: E) = P(
        key, def, false,
        { s, k, d -> runCatching { enumValueOf<E>(s.getString(k, d.name)!!) }.getOrDefault(d) },
        { k, v -> putString(k, v.name) },
    )

    // ---------- Raster ----------
    var columns by int("columns", 4)
    var rows by int("rows", 6)
    var gridPaddingH by int("grid_padding_h", 12)
    var gridPaddingTop by int("grid_padding_top", 8)

    // ---------- Aussehen ----------
    var iosFont by bool("ios_font", true)
    var glass by bool("glass", true)
    var searchPill by bool("search_pill", true)
    var badges by bool("badges", true)
    var widgetStyle by enum("widget_style", WidgetStyle.LIGHT)
    var widgetCorner by int("widget_corner", 22)

    // ---------- App-Symbole ----------
    var iconSize by int("icon_size", 56)
    var iconShape by enum("icon_shape", IconShape.SQUIRCLE)
    var iconCornerPercent by int("icon_corner", 24)
    var iconShadow by bool("icon_shadow", false)
    var pressAnimation by bool("press_animation", true)
    var openAnimation by bool("open_animation", true)

    // ---------- Beschriftung ----------
    var showLabels by bool("show_labels", true)
    var labelSize by float("label_size", 12f)
    var labelColor by int("label_color", 0xFFFFFFFF.toInt())
    var labelShadow by bool("label_shadow", true)
    var labelBold by bool("label_bold", false)

    // ---------- Dock ----------
    var dockEnabled by bool("dock_enabled", true)
    var dockCount by int("dock_count", 4)
    var dockBackground by bool("dock_background", true)
    var dockOpacity by float("dock_opacity", 0.22f)
    var dockColor by int("dock_color", 0xFFFFFFFF.toInt())
    var dockCorner by int("dock_corner", 34)
    var dockMargin by int("dock_margin", 10)
    var dockShowLabels by bool("dock_labels", false)

    // ---------- Startbildschirm ----------
    var showPageIndicator by bool("page_indicator", true)
    var clock24h by bool("clock_24h", true)
    var wallpaperDim by float("wallpaper_dim", 0f)
    var hideStatusBar by bool("hide_status_bar", false)
    var darkStatusIcons by bool("dark_status_icons", false)
    var autoAddNewApps by bool("auto_add_new_apps", true)
    var lockPortrait by bool("lock_portrait", true)
    var pageIndicatorColor by int("page_indicator_color", 0xFFFFFFFF.toInt())

    // ---------- Sperrbildschirm ----------
    var lockEnabled by bool("lock_enabled", false)
    var lockNotifications by bool("lock_notifications", true)
    var lockHideContent by bool("lock_hide_content", true)
    var lockTorch by bool("lock_torch", true)
    var lockCamera by bool("lock_camera", true)
    var lockClockColor by int("lock_clock_color", 0xFFFFFFFF.toInt())
    var lockDim by float("lock_dim", 0.1f)
    /** Uhr-Stil wie iOS: 0 Standard, 1 System, 2 Schmal, 3 Serif, 4 Mono, 5 Schreibschrift */
    var lockClockFont by int("lock_clock_font", 0)
    var lockClockWeight by int("lock_clock_weight", 700)
    var lockClockSize by int("lock_clock_size", 96)
    /** iOS-Widgets unter der Uhr (Akku, Wecker, Kalender) */
    var lockWidgets by bool("lock_widgets", true)
    /** Mitteilungen pro App stapeln wie bei iOS */
    var lockStackNotifs by bool("lock_stack_notifs", true)
    /** Taschenlampe/Kamera nur durch Gedrückthalten (wie beim iPhone) */
    var lockHoldButtons by bool("lock_hold_buttons", true)
    /** Nach links wischen = Kamera */
    var lockSwipeCamera by bool("lock_swipe_camera", true)
    /** Nach rechts wischen = Heute-Ansicht mit Widgets */
    var lockTodayView by bool("lock_today_view", true)

    // ---------- Systemweit (Bedienungshilfe) ----------
    var gestureBar by bool("gesture_bar", false)
    var gestureBack by bool("gesture_back", true)
    var gestureBarDark by bool("gesture_bar_dark", true)
    /** 0 = transparent (nur Strich), 1 = schwarz, 2 = weiß */
    var gestureBarStyle by int("gesture_bar_style", 0)
    var gestureSwitchApps by bool("gesture_switch_apps", true)
    /** Eigene Gestenleiste auch zeigen, wenn das System angeblich schon Wischgesten nutzt */
    var gestureBarAlways by bool("gesture_bar_always", false)
    /** System-Tasten ganz ausblenden (braucht einmalig die ADB-Freigabe WRITE_SECURE_SETTINGS) */
    var gestureHideButtons by bool("gesture_hide_buttons", false)
    var iosShade by bool("ios_shade", false)
    var dynamicIsland by bool("dynamic_island", false)
    var islandOffsetY by int("island_offset_y", 0)
    var islandWidth by int("island_width", 110)

    // ---------- Gesten ----------
    var swipeUp by enum("gesture_swipe_up", GestureAction.APP_LIBRARY)
    var swipeDown by enum("gesture_swipe_down", GestureAction.SEARCH)
    var doubleTap by enum("gesture_double_tap", GestureAction.NONE)
    var longPress by enum("gesture_long_press", GestureAction.EDIT_MODE)
    var haptics by bool("haptics", true)

    // ---------- App-Mediathek ----------
    var drawerStyle by enum("drawer_style", DrawerStyle.IOS)
    var drawerAutoKeyboard by bool("drawer_auto_keyboard", false)
    var drawerOpacity by float("drawer_opacity", 0.35f)
    var drawerBlur by bool("drawer_blur", true)
    var drawerWebSearch by bool("drawer_web_search", true)

    // ---------- Layout / Apps (wird beim Design-Reset NICHT zurückgesetzt) ----------
    var homeOrder by list("home_order")
    var dockApps by list("dock_apps")
    var hiddenApps by list("hidden_apps")
    var knownApps by list("known_apps")
    var customLabels by map("custom_labels")
    var launchCounts by map("launch_counts")
    var pendingWidgets by list("pending_widgets")

    fun labelFor(key: String, original: String): String = customLabels[key] ?: original

    /** Setzt alle Design-Einstellungen zurück, Anordnung/versteckte Apps bleiben erhalten. */
    fun resetDesign() = all.filter { !it.isLayout }.forEach { it.reset() }

    /** Setzt wirklich alles zurück (inkl. Anordnung). */
    fun resetEverything() = all.forEach { it.reset() }

    companion object {
        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }
    }
}
