package com.cleanlauncher.system

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.cleanlauncher.data.Prefs
import com.cleanlauncher.data.WallpaperRepo
import com.cleanlauncher.ui.DynamicIsland
import com.cleanlauncher.ui.ShadeKind
import com.cleanlauncher.ui.ShadeRoot
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Lifecycle für Compose-Fenster außerhalb einer Activity. */
class OverlayLifecycleOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val ssc = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = ssc.savedStateRegistry

    fun start() {
        ssc.performAttach()
        ssc.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

/** Ereignisse für die Dynamic Island (Laden). */
object IslandEvents {
    var chargeTick by mutableIntStateOf(0)
    var chargeLevel by mutableIntStateOf(0)
}

/**
 * Systemweite iOS-Elemente über allen Apps (über die Bedienungshilfe):
 * Gestenleiste statt 3 Tasten, Zurück-Wischen, Kontrollzentrum/Mitteilungszentrale, Dynamic Island.
 */
class OverlayManager(private val service: AccessibilityService) {

    private val wm = service.getSystemService(WindowManager::class.java)
    private val prefs = Prefs.get(service)
    private val owner = OverlayLifecycleOwner().also { it.start() }
    private val scope = MainScope()
    private val density = service.resources.displayMetrics.density

    private var bar: View? = null
    private var leftEdge: View? = null
    private var rightEdge: View? = null
    private var topTrigger: View? = null
    private var island: View? = null
    private var shade: View? = null
    private var lock: View? = null
    private var lockUnlocked by mutableStateOf(false)
    private var lockReset by mutableIntStateOf(0)
    private val km = service.getSystemService(android.app.KeyguardManager::class.java)
    private var shadeCloseTick by mutableIntStateOf(0)
    private var shadeKind by mutableStateOf(ShadeKind.NOTIFICATIONS)

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val bm = c.getSystemService(BatteryManager::class.java)
            IslandEvents.chargeLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            IslandEvents.chargeTick++
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> if (prefs.lockEnabled) showLock()
                Intent.ACTION_SCREEN_ON -> lockUnlocked = !km.isKeyguardLocked
                Intent.ACTION_USER_PRESENT -> lockUnlocked = true
            }
        }
    }

    fun start() {
        WallpaperRepo.init(service)
        MediaRepo.init(service)
        service.registerReceiver(powerReceiver, IntentFilter(Intent.ACTION_POWER_CONNECTED))
        service.registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )
        scope.launch {
            snapshotFlow {
                listOf(
                    prefs.gestureBar, prefs.gestureBack, prefs.gestureBarStyle, prefs.iosShade, prefs.lockEnabled,
                    prefs.dynamicIsland, prefs.islandOffsetY, prefs.islandWidth,
                )
            }.collect { sync(rebuild = true) }
        }
    }

    fun stop() {
        runCatching { service.unregisterReceiver(powerReceiver) }
        runCatching { service.unregisterReceiver(screenReceiver) }
        listOf(bar, leftEdge, rightEdge, topTrigger, island, shade, lock).forEach { remove(it) }
        lock = null
        bar = null; leftEdge = null; rightEdge = null; topTrigger = null; island = null; shade = null
        owner.destroy()
        scope.cancel()
    }

    fun onConfigurationChanged() = sync(rebuild = true)

    private fun landscape() = service.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun dimen(name: String, fallbackDp: Int): Int {
        val id = service.resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) service.resources.getDimensionPixelSize(id) else (fallbackDp * density).toInt()
    }

    private fun remove(v: View?) {
        if (v != null) runCatching { wm.removeView(v) }
    }

    private fun params(w: Int, h: Int, gravity: Int, focusable: Boolean = false): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        return WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT).apply {
            this.gravity = gravity
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun composeView(content: @Composable () -> Unit) = ComposeView(service).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setContent(content)
    }

    /** Nutzt das Handy schon System-Wischgesten? Dann braucht es unsere Leiste nicht. */
    private fun systemGestures(): Boolean = runCatching {
        android.provider.Settings.Secure.getInt(service.contentResolver, "navigation_mode", 0) == 2 ||
            android.provider.Settings.Global.getInt(service.contentResolver, "force_fsg_nav_bar", 0) == 1
    }.getOrDefault(false)

    private fun sync(rebuild: Boolean) {
        val land = landscape()
        val sysGestures = systemGestures()
        if (rebuild) {
            listOf(bar, leftEdge, rightEdge, topTrigger, island).forEach { remove(it) }
            bar = null; leftEdge = null; rightEdge = null; topTrigger = null; island = null
        }
        try {
            if (prefs.gestureBar && !land && !sysGestures && bar == null) addBar()
            if (prefs.gestureBar && prefs.gestureBack && !sysGestures && leftEdge == null) addEdges()
            if (prefs.iosShade && topTrigger == null) addTopTrigger()
            if (prefs.dynamicIsland && !land && island == null) addIsland()
        } catch (e: Exception) {
            // z. B. wenn das Fenster nicht angezeigt werden darf
        }
    }

    // ---------------- Gestenleiste ----------------

    private fun addBar() {
        val h = dimen("navigation_bar_height", 48).coerceAtLeast((24 * density).toInt())
        val v = GestureBarView(
            service, prefs.gestureBarStyle,
            onHome = { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) },
            onRecents = { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) },
            onSwitch = {
                if (prefs.gestureSwitchApps) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                    Handler(Looper.getMainLooper()).postDelayed({
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                    }, 300)
                }
            },
        )
        wm.addView(v, params(WindowManager.LayoutParams.MATCH_PARENT, h, Gravity.BOTTOM))
        bar = v
    }

    private fun addEdges() {
        val w = (18 * density).toInt()
        val h = (service.resources.displayMetrics.heightPixels * 0.55f).toInt()
        val back = { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); Unit }
        val l = BackEdgeView(service, left = true, onBack = back)
        val r = BackEdgeView(service, left = false, onBack = back)
        wm.addView(l, params(w, h, Gravity.START or Gravity.CENTER_VERTICAL))
        wm.addView(r, params(w, h, Gravity.END or Gravity.CENTER_VERTICAL))
        leftEdge = l
        rightEdge = r
    }

    // ---------------- Runterwischen ----------------

    private fun addTopTrigger() {
        val h = dimen("status_bar_height", 28).coerceAtLeast((20 * density).toInt())
        val v = TopTriggerView(service) { fx ->
            showShade(if (fx > 0.6f) ShadeKind.CONTROLS else ShadeKind.NOTIFICATIONS)
        }
        wm.addView(v, params(WindowManager.LayoutParams.MATCH_PARENT, h, Gravity.TOP))
        topTrigger = v
        // Island muss über dem Auslöser liegen
        island?.let {
            remove(it)
            island = null
            if (prefs.dynamicIsland && !landscape()) addIsland()
        }
    }

    fun showShade(kind: ShadeKind) {
        shadeKind = kind
        if (shade != null) return
        val root = object : FrameLayout(service) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    shadeCloseTick++
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        shadeCloseTick = 0
        // Compose sucht den Lifecycle am Fenster-Wurzelelement
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        root.addView(
            composeView {
                ShadeRoot(
                    initial = shadeKind,
                    closeTick = shadeCloseTick,
                    globalAction = { service.performGlobalAction(it) },
                    onClosed = { hideShade() },
                )
            },
        )
        wm.addView(root, params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, Gravity.TOP, focusable = true))
        shade = root
    }

    fun hideShade() {
        remove(shade)
        shade = null
    }

    // ---------------- iOS-Sperrbildschirm (sofort, ohne Verzögerung) ----------------

    val lockShowing: Boolean get() = lock != null

    fun showLock() {
        lockUnlocked = !km.isKeyguardLocked
        if (lock != null) {
            lockReset++
            return
        }
        val am = service.getSystemService(android.media.AudioManager::class.java)
        if (am.mode == android.media.AudioManager.MODE_IN_CALL || am.mode == android.media.AudioManager.MODE_IN_COMMUNICATION ||
            am.mode == android.media.AudioManager.MODE_RINGTONE
        ) return
        hideShade()
        val root = FrameLayout(service)
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        root.addView(
            composeView {
                com.cleanlauncher.ui.LauncherTheme(prefs, dark = true) {
                    com.cleanlauncher.lock.LockScreen(
                        prefs = prefs,
                        unlocked = lockUnlocked,
                        resetTick = lockReset,
                        onUnlock = { hideLock() },
                        onOpenNotification = { n ->
                            hideLock()
                            NotificationRepo.open(n)
                        },
                    )
                }
            },
        )
        try {
            wm.addView(root, params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, Gravity.TOP))
            lock = root
            // Dynamic Island soll über dem Sperrbildschirm liegen
            if (island != null) {
                remove(island)
                island = null
                if (prefs.dynamicIsland && !landscape()) addIsland()
            }
        } catch (e: Exception) {
            lock = null
        }
    }

    fun hideLock() {
        remove(lock)
        lock = null
    }

    /** Ein anderes Fenster kommt nach vorne (Wecker, Anruf, Kamera …) -> Sperrbildschirm weg. */
    fun onWindowChanged(pkg: String?) {
        if (lock == null || pkg == null) return
        val ignore = setOf(service.packageName, "com.android.systemui", "android", "com.miui.home", "com.miui.aod", "com.android.keyguard")
        if (pkg in ignore || pkg.contains("keyguard") || pkg.contains("inputmethod")) return
        hideLock()
    }

    // ---------------- Dynamic Island ----------------

    private fun addIsland() {
        val sb = dimen("status_bar_height", 28)
        val pillH = (32 * density).toInt()
        val y = ((sb - pillH) / 2).coerceAtLeast((4 * density).toInt()) + (prefs.islandOffsetY * density).toInt()
        val v = composeView {
            DynamicIsland(
                prefs = prefs,
                onOpenCenter = { showShade(ShadeKind.NOTIFICATIONS) },
            )
        }
        val p = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        p.y = y
        wm.addView(v, p)
        island = v
    }
}

/** Unsichtbare Leiste über den 3 Tasten: hochwischen = Home, hochwischen & halten = Übersicht, seitlich = letzte App. */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class GestureBarView(
    context: Context,
    private val barStyle: Int,
    private val onHome: () -> Unit,
    private val onRecents: () -> Unit,
    private val onSwitch: () -> Unit,
) : View(context) {
    private val d = resources.displayMetrics.density
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (barStyle == 2) Color.BLACK else Color.WHITE }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 0, 0, 0) }
    private val bg = when (barStyle) {
        1 -> Color.BLACK
        2 -> Color.WHITE
        else -> Color.TRANSPARENT
    }
    private var downX = 0f
    private var downY = 0f
    private var lift = 0f
    private var shift = 0f
    private var held = false
    private var lastMove = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val holdCheck = object : Runnable {
        override fun run() {
            if (!held && lift > 50 * d && System.currentTimeMillis() - lastMove > 250) {
                held = true
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            } else if (!held) handler.postDelayed(this, 60)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (bg != Color.TRANSPARENT) canvas.drawColor(bg)
        val w = 120 * d
        val h = 5 * d
        val cx = width / 2f + shift
        // Strich unten wie beim iPhone
        val cy = height - 9 * d - (lift / 6).coerceAtMost(height / 2f)
        if (bg == Color.TRANSPARENT) {
            val o = 1.2f * d
            canvas.drawRoundRect(RectF(cx - w / 2 - o, cy - h / 2 - o, cx + w / 2 + o, cy + h / 2 + o), h, h, outline)
        }
        canvas.drawRoundRect(RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), h, h, pill)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                lift = 0f
                shift = 0f
                held = false
                lastMove = System.currentTimeMillis()
                handler.postDelayed(holdCheck, 60)
            }
            MotionEvent.ACTION_MOVE -> {
                val newLift = downY - e.rawY
                if (abs(newLift - lift) > 4 * d) lastMove = System.currentTimeMillis()
                lift = newLift
                shift = (e.rawX - downX).coerceIn(-60 * d, 60 * d)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(holdCheck)
                val dx = e.rawX - downX
                when {
                    held -> onRecents()
                    lift > 24 * d -> onHome()
                    abs(dx) > 60 * d -> onSwitch()
                }
                if (held || lift > 24 * d || abs(dx) > 60 * d) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                lift = 0f
                shift = 0f
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(holdCheck)
                lift = 0f
                shift = 0f
                invalidate()
            }
        }
        return true
    }
}

/** Schmaler, unsichtbarer Rand: nach innen wischen = Zurück. */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class BackEdgeView(context: Context, private val left: Boolean, private val onBack: () -> Unit) : View(context) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private var downX = 0f
    private var y = 0f
    private var pull = 0f

    override fun onDraw(canvas: Canvas) {
        if (pull <= 0f) return
        val p = (pull / (60 * d)).coerceIn(0f, 1f)
        val r = 18 * d * p
        val cx = if (left) 0f else width.toFloat()
        canvas.drawCircle(cx, y, r, paint)
        if (p > 0.6f) {
            val ax = if (left) r * 0.45f else width - r * 0.45f
            val s = 4 * d
            val path = Path().apply {
                if (left) {
                    moveTo(ax + s / 2, y - s); lineTo(ax - s / 2, y); lineTo(ax + s / 2, y + s)
                } else {
                    moveTo(ax - s / 2, y - s); lineTo(ax + s / 2, y); lineTo(ax - s / 2, y + s)
                }
            }
            canvas.drawPath(path, arrow)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                y = e.y
                pull = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                pull = if (left) e.rawX - downX else downX - e.rawX
                y = e.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (pull > 50 * d) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onBack()
                }
                pull = 0f
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                pull = 0f
                invalidate()
            }
        }
        return true
    }
}

/** Unsichtbarer Streifen auf der Statusleiste: nach unten wischen öffnet Kontrollzentrum/Mitteilungen. */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class TopTriggerView(context: Context, private val onPull: (Float) -> Unit) : View(context) {
    private val d = resources.displayMetrics.density
    private var downY = 0f
    private var downX = 0f
    private var fired = false

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = e.rawY
                downX = e.rawX
                fired = false
            }
            MotionEvent.ACTION_MOVE -> if (!fired && e.rawY - downY > 18 * d) {
                fired = true
                onPull(downX / resources.displayMetrics.widthPixels)
            }
        }
        return true
    }
}
