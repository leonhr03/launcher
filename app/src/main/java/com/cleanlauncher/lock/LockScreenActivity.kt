package com.cleanlauncher.lock

import android.app.AlarmManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlauncher.data.AppRepository
import com.cleanlauncher.data.BuiltinWidget
import com.cleanlauncher.data.Prefs
import com.cleanlauncher.data.WallpaperRepo
import com.cleanlauncher.data.WidgetStyle
import com.cleanlauncher.system.Notif
import com.cleanlauncher.system.NotificationRepo
import com.cleanlauncher.ui.BuiltinWidgetView
import com.cleanlauncher.ui.IconImage
import com.cleanlauncher.ui.InterFamily
import com.cleanlauncher.ui.Ios
import com.cleanlauncher.ui.LauncherTheme
import com.cleanlauncher.ui.WidgetCard
import com.cleanlauncher.ui.frosted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

/**
 * iOS-artiger Sperrbildschirm. Er liegt ÜBER der System-Sperre:
 * Nach oben wischen -> das System fragt PIN/Fingerabdruck ab (sicher), danach Startbildschirm.
 *
 * Diese Activity ist nur der Weg ohne Bedienungshilfe. Sie bleibt nach dem Entsperren
 * im Hintergrund am Leben, damit sie beim nächsten Ausschalten sofort wieder da ist.
 */
class LockScreenActivity : ComponentActivity() {

    private var unlocked by mutableStateOf(false)
    private var resetTick by mutableIntStateOf(0)
    private val km by lazy { getSystemService(KeyguardManager::class.java) }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> unlocked = true
                Intent.ACTION_SCREEN_OFF -> {
                    unlocked = !km.isKeyguardLocked
                    resetTick++
                }
                Intent.ACTION_SCREEN_ON -> unlocked = !km.isKeyguardLocked
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        AppRepository.init(this)
        com.cleanlauncher.system.MediaRepo.init(this)
        WallpaperRepo.init(this)
        unlocked = !km.isKeyguardLocked
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(receiver, f)
        setContent {
            val prefs = remember { Prefs.get(this) }
            LauncherTheme(prefs, dark = true) {
                LockScreen(
                    prefs = prefs,
                    unlocked = unlocked,
                    resetTick = resetTick,
                    onUnlock = { reset -> unlock(cancelled = reset) },
                    onOpenNotification = { n -> unlock(cancelled = {}) { UnlockActivity.runAction(this, UnlockActivity.actionIntent(UnlockActivity.ACTION_NOTIF, n.key)) } },
                    onCamera = { openSecureCamera(this) },
                    onCustomize = {
                        unlock(cancelled = {}) {
                            runCatching { startActivity(Intent(this, LockCustomizeActivity::class.java)) }
                        }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        unlocked = !km.isKeyguardLocked
        resetTick++
    }

    override fun onResume() {
        super.onResume()
        unlocked = !km.isKeyguardLocked
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    /** Nicht beenden, nur nach hinten legen -> beim nächsten Mal ohne Neuaufbau sofort sichtbar. */
    private fun hideUnlocked() {
        if (!moveTaskToBack(true)) finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    /** Entsperren: bei gesperrtem Gerät fragt das System nach PIN/Fingerabdruck. */
    private fun unlock(cancelled: () -> Unit, after: (() -> Unit)? = null) {
        if (km.isKeyguardLocked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    after?.invoke()
                    hideUnlocked()
                }

                override fun onDismissCancelled() = cancelled()
                override fun onDismissError() = cancelled()
            })
        } else {
            after?.invoke()
            hideUnlocked()
        }
    }
}

/** Kamera ohne Entsperren (wie beim iPhone). */
fun openSecureCamera(context: Context) {
    try {
        context.startActivity(
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}

/** Schriften für die Uhr – wie die Stil-Auswahl beim iPhone. */
val LOCK_CLOCK_FONTS = listOf("Standard", "System", "Schmal", "Serif", "Mono", "Schrift")

fun lockClockFamily(font: Int): FontFamily = when (font) {
    1 -> FontFamily.SansSerif
    2 -> FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))
    3 -> FontFamily.Serif
    4 -> FontFamily.Monospace
    5 -> FontFamily.Cursive
    else -> InterFamily
}

/** Große Uhr des Sperrbildschirms – auch in der Anpassen-Vorschau benutzt. */
@Composable
fun LockClockText(prefs: Prefs, now: Long, color: Color = Color(prefs.lockClockColor)) {
    val family = remember(prefs.lockClockFont) { lockClockFamily(prefs.lockClockFont) }
    val size = prefs.lockClockSize
    Text(
        DateFormat.format(if (prefs.clock24h) "HH:mm" else "h:mm", Date(now)).toString(),
        color = color,
        fontSize = size.sp,
        fontFamily = family,
        fontWeight = FontWeight(prefs.lockClockWeight.coerceIn(100, 900)),
        lineHeight = (size * 1.05f).sp,
        maxLines = 1,
        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 3f), 18f)),
    )
}

private class Battery(val level: Int, val charging: Boolean)

@Composable
private fun rememberBattery(): Battery {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                i ?: return
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0 && s > 0) level = l * 100 / s
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        val sticky = ctx.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        r.onReceive(ctx, sticky)
        onDispose { runCatching { ctx.unregisterReceiver(r) } }
    }
    return Battery(level, charging)
}

private fun nextAlarm(context: Context): Long? = try {
    context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
} catch (_: Exception) {
    null
}

@Composable
fun LockScreen(
    prefs: Prefs,
    unlocked: Boolean,
    resetTick: Int,
    onUnlock: (reset: () -> Unit) -> Unit,
    onOpenNotification: (Notif) -> Unit,
    onCamera: () -> Unit,
    onCustomize: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val offset = remember { Animatable(0f) }
    val hx = remember { Animatable(0f) }
    val density = LocalDensity.current
    val conf = LocalConfiguration.current
    val screenH = with(density) { conf.screenHeightDp.dp.toPx() }
    val screenW = with(density) { conf.screenWidthDp.dp.toPx() }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(resetTick) {
        offset.snapTo(0f)
        hx.snapTo(0f)
        expanded = emptySet()
    }
    if (androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current != null) {
        BackHandler { if (hx.value != 0f) scope.launch { hx.animateTo(0f, tween(220)) } }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000)
        }
    }
    val battery = rememberBattery()
    val minute = now / 60_000
    val alarm = remember(minute, resetTick) { nextAlarm(ctx) }

    // "Wird geladen" kurz einblenden – beim Anstecken und beim Einschalten während des Ladens
    var chargeFlash by remember { mutableStateOf(false) }
    LaunchedEffect(battery.charging, resetTick) {
        if (battery.charging && battery.level >= 0) {
            chargeFlash = true
            delay(3500)
        }
        chargeFlash = false
    }

    val wallpaper = WallpaperRepo.lock
    val progress = (-offset.value / screenH).coerceIn(0f, 1f)
    val todayP = (hx.value / screenW).coerceIn(0f, 1f)
    val cameraP = (-hx.value / screenW).coerceIn(0f, 1f)

    // Nach oben wischen zum Entsperren – nur auf Uhr- und unterem Bereich, damit die Mitteilungen normal scrollen
    val unlockDrag = Modifier.pointerInput(screenH) {
        detectVerticalDragGestures(
            onVerticalDrag = { ch, dy ->
                ch.consume()
                scope.launch { offset.snapTo((offset.value + dy).coerceAtMost(0f)) }
            },
            onDragEnd = {
                scope.launch {
                    if (offset.value < -screenH * 0.12f) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        offset.animateTo(-screenH, tween(220))
                        onUnlock { scope.launch { offset.animateTo(0f, tween(250)) } }
                    } else {
                        offset.animateTo(0f, tween(250))
                    }
                }
            },
        )
    }

    // Seitlich wischen: links = Kamera, rechts = Heute-Ansicht (wie iOS)
    var dragStart by remember { mutableFloatStateOf(0f) }
    val sideDrag = Modifier.pointerInput(screenW, prefs.lockSwipeCamera, prefs.lockTodayView) {
        detectHorizontalDragGestures(
            onDragStart = { dragStart = hx.value },
            onHorizontalDrag = { ch, dx ->
                val fromToday = dragStart > screenW / 2
                val min = if (prefs.lockSwipeCamera && !fromToday) -screenW else 0f
                val max = if (prefs.lockTodayView) screenW else 0f
                if (min == 0f && max == 0f) return@detectHorizontalDragGestures
                ch.consume()
                scope.launch { hx.snapTo((hx.value + dx).coerceIn(min, max)) }
            },
            onDragCancel = { scope.launch { hx.animateTo(if (dragStart > screenW / 2) screenW else 0f, tween(220)) } },
            onDragEnd = {
                scope.launch {
                    val v = hx.value
                    if (dragStart > screenW / 2) {
                        hx.animateTo(if (v < screenW * 0.75f) 0f else screenW, tween(220))
                    } else when {
                        v > screenW * 0.25f -> hx.animateTo(screenW, tween(220))
                        v < -screenW * 0.3f -> {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            hx.animateTo(-screenW, tween(180))
                            onCamera()
                            delay(800)
                            hx.snapTo(0f)
                        }
                        else -> hx.animateTo(0f, tween(220))
                    }
                }
            },
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(sideDrag),
    ) {
        if (wallpaper != null) {
            Image(
                wallpaper, null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = 1f - progress * 0.6f
                    // Beim Hochwischen leicht herauszoomen wie beim iPhone
                    val s = 1f + progress * 0.08f
                    scaleX = s
                    scaleY = s
                },
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF1B2A4A), Color(0xFF4A3B6B), Color(0xFF1A1A2E))),
                ),
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (prefs.lockDim + todayP * 0.35f).coerceAtMost(0.9f))))

        // Heute-Ansicht (von links)
        if (todayP > 0f) {
            TodayView(
                prefs = prefs,
                now = now,
                alarm = alarm,
                modifier = Modifier.graphicsLayer {
                    translationX = hx.value - screenW
                    alpha = todayP
                },
            )
        }

        // Kamera-Vorschau (von rechts)
        if (cameraP > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = screenW + hx.value }
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) { CameraGlyph(Color.White.copy(alpha = 0.6f)) }
        }

        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = offset.value
                    translationX = hx.value
                    alpha = (1f - progress * 1.3f).coerceIn(0f, 1f) * (1f - todayP * 0.8f)
                }
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val clockColor = Color(prefs.lockClockColor)
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(unlockDrag)
                    .pointerInput(Unit) {
                        // Lange auf die Uhr drücken = Sperrbildschirm anpassen (wie iOS)
                        detectTapGestures(onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCustomize()
                        })
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(if (prefs.dynamicIsland) 34.dp else 10.dp))
                val open by animateFloatAsState(if (unlocked) 1f else 0f, tween(350), label = "padlock")
                Padlock(open = open, modifier = Modifier.size(22.dp, 26.dp))
                Spacer(Modifier.height(8.dp))
                val locale = Locale.getDefault()
                val datePattern = remember(locale) { DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM") }
                Text(
                    if (chargeFlash) "Wird geladen · ${battery.level} %" else DateFormat.format(datePattern, Date(now)).toString(),
                    color = if (chargeFlash) Ios.green else clockColor,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 2f), 12f)),
                )
                LockClockText(prefs, now, clockColor)
                if (prefs.lockWidgets) {
                    Spacer(Modifier.height(6.dp))
                    LockWidgetsRow(prefs, battery, alarm, now, clockColor)
                }
                Spacer(Modifier.height(12.dp))
            }

            // Mitteilungen
            val notifs = if (prefs.lockNotifications) NotificationRepo.list else emptyList()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (notifs.isEmpty()) {
                    Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp)) {
                        com.cleanlauncher.ui.MediaPlayerCard(prefs)
                        if (prefs.lockNotifications && !NotificationRepo.hasAccess(ctx)) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Mitteilungen erscheinen hier, sobald du in den Launcher-Einstellungen den Mitteilungszugriff erlaubst.",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .frosted(RoundedCornerShape(18.dp), Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.2f), prefs.glass)
                                    .padding(14.dp),
                            )
                        }
                    }
                }
                if (notifs.isNotEmpty()) {
                    val hide = prefs.lockHideContent && !unlocked
                    // Wie iOS: neue Mitteilungen pro App gestapelt
                    val groups = remember(notifs) { notifs.take(40).groupBy { it.pkg }.values.toList() }
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (com.cleanlauncher.system.MediaRepo.active) item(key = "media") { com.cleanlauncher.ui.MediaPlayerCard(prefs) }
                        for (group in groups) {
                            val pkg = group.first().pkg
                            val stacked = prefs.lockStackNotifs && group.size > 1 && pkg !in expanded
                            if (stacked) {
                                item(key = "stack_$pkg") {
                                    com.cleanlauncher.ui.SwipeToDismiss(
                                        enabled = group.any { it.clearable },
                                        onDismiss = { group.filter { it.clearable }.forEach { NotificationRepo.dismiss(it) } },
                                    ) {
                                        NotificationStack(prefs, group, hide) { expanded = expanded + pkg }
                                    }
                                }
                            } else {
                                if (prefs.lockStackNotifs && group.size > 1) {
                                    item(key = "head_$pkg") {
                                        GroupHeader(AppRepository.appFor(pkg)?.originalLabel ?: pkg) { expanded = expanded - pkg }
                                    }
                                }
                                items(group, key = { it.key }) { n ->
                                    com.cleanlauncher.ui.SwipeToDismiss(enabled = n.clearable, onDismiss = { NotificationRepo.dismiss(n) }) {
                                        NotificationCard(prefs, n, hideContent = hide) { onOpenNotification(n) }
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }
                }
            }

            // Taschenlampe & Kamera + Wisch-Bereich
            Column(Modifier.fillMaxWidth().then(unlockDrag), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 46.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (prefs.lockTorch) TorchButton(prefs.lockHoldButtons) else Spacer(Modifier.size(50.dp))
                    Spacer(Modifier.weight(1f))
                    if (prefs.lockCamera) {
                        RoundButton(onAction = onCamera, active = false, hold = prefs.lockHoldButtons) { CameraGlyph(Color.White) }
                    } else {
                        Spacer(Modifier.size(50.dp))
                    }
                }
                Text(
                    if (unlocked) "Zum Öffnen nach oben wischen" else "Zum Entsperren nach oben wischen",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.size(134.dp, 5.dp).clip(RoundedCornerShape(50)).background(Color.White))
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ---------------- Widgets unter der Uhr (iOS 16+) ----------------

@Composable
private fun LockWidgetsRow(prefs: Prefs, battery: Battery, alarm: Long?, now: Long, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleWidget(prefs) {
            val ringColor = when {
                battery.charging -> Ios.green
                battery.level in 0..20 -> Ios.red
                else -> color
            }
            Canvas(Modifier.size(46.dp)) {
                val sw = size.minDimension * 0.1f
                drawCircle(color.copy(alpha = 0.25f), size.minDimension / 2 - sw / 2, style = Stroke(sw))
                drawArc(
                    ringColor, -90f, 360f * (battery.level.coerceAtLeast(0) / 100f), false,
                    topLeft = Offset(sw / 2, sw / 2),
                    size = Size(size.width - sw, size.height - sw),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
            Text(
                if (battery.level >= 0) "${battery.level}" else "–",
                color = color,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (alarm != null) {
            CircleWidget(prefs) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AlarmGlyph(color, Modifier.size(16.dp))
                    Text(
                        DateFormat.format(if (prefs.clock24h) "HH:mm" else "h:mm", Date(alarm)).toString(),
                        color = color,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        CircleWidget(prefs) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    DateFormat.format("EEE", Date(now)).toString().trimEnd('.').uppercase(Locale.getDefault()),
                    color = Ios.red,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 11.sp,
                )
                Text(
                    DateFormat.format("d", Date(now)).toString(),
                    color = color,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun CircleWidget(prefs: Prefs, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(58.dp)
            .frosted(CircleShape, Color.White.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.28f), prefs.glass),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun AlarmGlyph(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.11f
        val c = Offset(w / 2, h * 0.56f)
        val r = w * 0.36f
        drawCircle(color, r, c, style = Stroke(sw))
        drawLine(color, c, Offset(c.x, c.y - r * 0.6f), sw, StrokeCap.Round)
        drawLine(color, c, Offset(c.x + r * 0.45f, c.y), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.08f, h * 0.22f), Offset(w * 0.26f, h * 0.06f), sw * 1.3f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.92f, h * 0.22f), Offset(w * 0.74f, h * 0.06f), sw * 1.3f, StrokeCap.Round)
    }
}

// ---------------- Heute-Ansicht (nach rechts wischen) ----------------

@Composable
private fun TodayView(prefs: Prefs, now: Long, alarm: Long?, modifier: Modifier) {
    val locale = Locale.getDefault()
    val datePattern = remember(locale) { DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM") }
    val textColor = if (prefs.widgetStyle == WidgetStyle.LIGHT) Color.Black else Color.White
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(if (prefs.dynamicIsland) 48.dp else 24.dp))
        Text(DateFormat.format(datePattern, Date(now)).toString(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(
            DateFormat.format(if (prefs.clock24h) "HH:mm" else "h:mm", Date(now)).toString(),
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BuiltinWidgetView(BuiltinWidget.BATTERY, prefs, Modifier.weight(1f).aspectRatio(1f))
            BuiltinWidgetView(BuiltinWidget.CALENDAR, prefs, Modifier.weight(1f).aspectRatio(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BuiltinWidgetView(BuiltinWidget.CLOCK_ANALOG, prefs, Modifier.weight(1f).aspectRatio(1f))
            WidgetCard(prefs, Modifier.weight(1f).aspectRatio(1f)) {
                Column(
                    Modifier.fillMaxSize().padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AlarmGlyph(Ios.orange, Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Wecker", color = textColor.copy(alpha = 0.7f), fontSize = 13.sp)
                    Text(
                        if (alarm != null) DateFormat.format(if (prefs.clock24h) "EE HH:mm" else "EE h:mm a", Date(alarm)).toString() else "Kein Wecker",
                        color = textColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        com.cleanlauncher.ui.MediaPlayerCard(prefs)
        Spacer(Modifier.height(20.dp))
        Text(
            "Zum Zurückkehren nach links wischen",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------- Mitteilungen ----------------

@Composable
private fun GroupHeader(label: String, onCollapse: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "Weniger anzeigen",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.22f))
                .clickable(onClick = onCollapse)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Gestapelte Mitteilungen einer App: oberste Karte + angedeutete Karten darunter. Tippen klappt auf. */
@Composable
private fun NotificationStack(prefs: Prefs, group: List<Notif>, hideContent: Boolean, onExpand: () -> Unit) {
    val layers = (group.size - 1).coerceAtMost(2)
    Box(Modifier.fillMaxWidth().padding(bottom = (layers * 8).dp)) {
        for (i in layers downTo 1) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(horizontal = (i * 10).dp)
                    .offset(y = (i * 8).dp)
                    .frosted(RoundedCornerShape(22.dp), Color.White.copy(alpha = 0.32f - i * 0.08f), Color.White.copy(alpha = 0.18f - i * 0.04f), prefs.glass),
            )
        }
        NotificationCard(prefs, group.first(), hideContent, more = group.size - 1, onClick = onExpand)
    }
}

@Composable
private fun NotificationCard(prefs: Prefs, n: Notif, hideContent: Boolean, more: Int = 0, onClick: () -> Unit) {
    val app = AppRepository.appFor(n.pkg)
    Row(
        Modifier
            .fillMaxWidth()
            .frosted(RoundedCornerShape(22.dp), Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.22f), prefs.glass)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app != null) IconImage(app, prefs, sizeDp = 38) else Spacer(Modifier.size(38.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (hideContent) (app?.originalLabel ?: n.pkg) else n.title.ifEmpty { app?.originalLabel ?: "" },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    DateFormat.format(if (prefs.clock24h) "HH:mm" else "h:mm", Date(n.time)).toString(),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                )
            }
            Text(
                if (hideContent) (if (more > 0) "${more + 1} Mitteilungen" else "Mitteilung") else n.text,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (more > 0 && !hideContent) {
                Text(
                    if (more == 1) "1 weitere Mitteilung" else "$more weitere Mitteilungen",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

// ---------------- Tasten ----------------

/** Runde Taste wie beim iPhone: Gedrückthalten (mit Vibration) löst aus, sonst normales Tippen. */
@Composable
private fun RoundButton(onAction: () -> Unit, active: Boolean, hold: Boolean, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    Box(
        Modifier
            .size(50.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .background(if (active) Color.White else Color.Black.copy(alpha = 0.35f))
            .pointerInput(hold) {
                detectTapGestures(
                    onPress = {
                        scope.launch { scale.animateTo(1.25f, tween(if (hold) 400 else 120)) }
                        tryAwaitRelease()
                        scope.launch { scale.animateTo(1f, tween(200)) }
                    },
                    onLongPress = if (hold) {
                        { _: Offset ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAction()
                        }
                    } else {
                        null
                    },
                    onTap = if (!hold) {
                        { _: Offset -> onAction() }
                    } else {
                        null
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun TorchButton(hold: Boolean) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cm = remember { ctx.getSystemService(CameraManager::class.java) }
    val camId = remember {
        try {
            cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        } catch (e: Exception) {
            null
        }
    }
    var on by remember { mutableStateOf(false) }
    DisposableEffect(camId) {
        val cb = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == camId) on = enabled
            }
        }
        runCatching { cm.registerTorchCallback(cb, Handler(Looper.getMainLooper())) }
        onDispose { runCatching { cm.unregisterTorchCallback(cb) } }
    }
    RoundButton({ camId?.let { runCatching { cm.setTorchMode(it, !on) } } }, active = on, hold = hold) {
        TorchGlyph(if (on) Color.Black else Color.White)
    }
}

@Composable
private fun TorchGlyph(color: Color) {
    Canvas(Modifier.size(14.dp, 24.dp)) {
        val w = size.width
        val h = size.height
        val head = Path().apply {
            moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h * 0.22f); lineTo(w * 0.78f, h * 0.42f)
            lineTo(w * 0.22f, h * 0.42f); lineTo(0f, h * 0.22f); close()
        }
        drawPath(head, color)
        drawRoundRect(color, Offset(w * 0.22f, h * 0.45f), Size(w * 0.56f, h * 0.55f), CornerRadius(w * 0.12f))
    }
}

@Composable
private fun CameraGlyph(color: Color) {
    Canvas(Modifier.size(26.dp, 20.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(color, Offset(w * 0.3f, 0f), Size(w * 0.4f, h * 0.3f), CornerRadius(w * 0.05f))
        drawRoundRect(color, Offset(0f, h * 0.18f), Size(w, h * 0.82f), CornerRadius(w * 0.14f))
        drawCircle(Color.Black.copy(alpha = 0.6f), h * 0.26f, Offset(w / 2, h * 0.58f))
        drawCircle(color, h * 0.18f, Offset(w / 2, h * 0.58f))
    }
}

/** Schloss, das sich beim Entsperren (Fingerabdruck) animiert öffnet – wie bei Face ID. */
@Composable
private fun Padlock(open: Float, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.15f
        val bodyTop = h * 0.45f
        val sl = w * 0.22f // linke Bügelseite
        val sr = w * 0.78f // rechte Bügelseite
        val r = (sr - sl) / 2
        val arcTop = sw / 2 + h * 0.08f * (1f - open)
        // Bügel: Halbkreis + zwei Beine
        drawArc(
            Color.White, 180f, 180f, false,
            topLeft = Offset(sl, arcTop),
            size = Size(r * 2, r * 2),
            style = Stroke(sw),
        )
        val legTop = arcTop + r
        drawLine(Color.White, Offset(sr, legTop), Offset(sr, bodyTop), sw)
        val leftEnd = bodyTop + (legTop + h * 0.06f - bodyTop) * open
        drawLine(Color.White, Offset(sl, legTop), Offset(sl, leftEnd.coerceAtLeast(legTop)), sw)
        drawRoundRect(Color.White, Offset(0f, bodyTop), Size(w, h - bodyTop), CornerRadius(w * 0.16f))
    }
}
