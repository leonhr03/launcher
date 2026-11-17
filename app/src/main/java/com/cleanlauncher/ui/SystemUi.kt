package com.cleanlauncher.ui

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlauncher.data.AppRepository
import com.cleanlauncher.data.Prefs
import com.cleanlauncher.system.IslandEvents
import com.cleanlauncher.system.MediaRepo
import com.cleanlauncher.system.Notif
import com.cleanlauncher.system.NotificationRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ShadeKind { NOTIFICATIONS, CONTROLS }

private fun Context.startSafe(i: Intent): Boolean = try {
    startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: Exception) {
    false
}

// ============================================================================
// Mitteilungszentrale + Kontrollzentrum (Fenster beim Herunterwischen)
// ============================================================================

@Composable
fun ShadeRoot(initial: ShadeKind, closeTick: Int, globalAction: (Int) -> Unit, onClosed: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    val anim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            anim.animateTo(0f, tween(180))
            onClosed()
        }
    }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(240)) }
    LaunchedEffect(closeTick) { if (closeTick > 0) close() }
    val run: (Intent) -> Unit = { i -> if (ctx.startSafe(i)) close() }

    LauncherTheme(prefs, dark = true) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = anim.value }
                .pointerInput(Unit) { detectTapGestures { close() } }
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -60.dp.toPx()) close() },
                        onVerticalDrag = { ch, dy ->
                            total += dy
                            ch.consume()
                        },
                    )
                },
        ) {
            BlurredBackdrop(0.38f)
            val pager = rememberPagerState(initialPage = if (initial == ShadeKind.CONTROLS) 1 else 0) { 2 }
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = (1f - anim.value) * -120f }
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                HorizontalPager(pager, Modifier.weight(1f)) { p ->
                    if (p == 0) NotificationCenter(prefs, run) { close() }
                    else ControlCenter(prefs, run, globalAction) { close() }
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(2) { i ->
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (i == pager.currentPage) 0.95f else 0.35f)),
                        )
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                        .size(134.dp, 5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.8f))
                        .clickable { close() },
                )
            }
        }
    }
}

@Composable
private fun NotificationCenter(prefs: Prefs, run: (Intent) -> Unit, close: () -> Unit) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val locale = Locale.getDefault()
    val datePattern = remember(locale) { DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM") }
    val notifs = NotificationRepo.list
    val hasAccess = remember { NotificationRepo.hasAccess(ctx) }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(DateFormat.format(datePattern, Date(now)).toString(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    DateFormat.format(if (prefs.clock24h) "HH:mm" else "h:mm", Date(now)).toString(),
                    color = Color.White, fontSize = 84.sp, fontWeight = FontWeight.Bold, lineHeight = 88.sp,
                )
            }
        }
        if (MediaRepo.active) item { MediaPlayerCard(prefs) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Mitteilungszentrale", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (notifs.any { it.clearable }) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .frosted(CircleShape, Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.2f), prefs.glass)
                            .clickable { NotificationRepo.dismissAll() },
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", color = Color.White, fontSize = 14.sp) }
                }
            }
        }
        if (!hasAccess) {
            item {
                InfoCard(prefs, "Mitteilungen anzeigen", "Tippe hier und erlaube „Clean Launcher“ den Mitteilungszugriff.") {
                    run(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }
        } else if (notifs.isEmpty()) {
            item {
                Text(
                    "Keine älteren Mitteilungen",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        items(notifs, key = { it.key }) { n ->
            SwipeToDismiss(enabled = n.clearable, onDismiss = { NotificationRepo.dismiss(n) }) {
                NotifCard(prefs, n, hideContent = false) {
                    NotificationRepo.open(n)
                    close()
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
fun SwipeToDismiss(enabled: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableFloatStateOf(1000f) }
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .offset { IntOffset(offset.value.roundToInt(), 0) }
            .graphicsLayer { alpha = 1f - (abs(offset.value) / width).coerceIn(0f, 0.9f) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(offset.value) > width * 0.35f) {
                                offset.animateTo(if (offset.value > 0) width else -width, tween(150))
                                onDismiss()
                            } else offset.animateTo(0f, spring())
                        }
                    },
                    onHorizontalDrag = { ch, dx ->
                        ch.consume()
                        scope.launch { offset.snapTo(offset.value + dx) }
                    },
                )
            },
    ) { content() }
}

@Composable
fun NotifCard(prefs: Prefs, n: Notif, hideContent: Boolean, onClick: () -> Unit) {
    val app = AppRepository.appFor(n.pkg)
    Row(
        Modifier
            .fillMaxWidth()
            .frosted(RoundedCornerShape(22.dp), Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.22f), prefs.glass)
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
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text(
                    DateFormat.format(if (prefs.clock24h) "HH:mm" else "h:mm", Date(n.time)).toString(),
                    color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
                )
            }
            Text(
                if (hideContent) "Mitteilung" else n.text,
                color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InfoCard(prefs: Prefs, title: String, text: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .frosted(RoundedCornerShape(22.dp), Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.22f), prefs.glass)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(text, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
    }
}

/** Musik-Player (Sperrbildschirm, Mitteilungszentrale). */
@Composable
fun MediaPlayerCard(prefs: Prefs, modifier: Modifier = Modifier) {
    if (!MediaRepo.active) return
    Column(
        modifier
            .fillMaxWidth()
            .frosted(RoundedCornerShape(24.dp), Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.22f), prefs.glass)
            .clickable { MediaRepo.open() }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(56)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(MediaRepo.title.orEmpty(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(MediaRepo.artist.orEmpty(), color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (MediaRepo.playing) EqualizerBars(Color.White, Modifier.size(22.dp, 16.dp))
        }
        Spacer(Modifier.height(10.dp))
        MediaControls(Color.White)
    }
}

@Composable
private fun AlbumArt(sizeDp: Int) {
    val art = MediaRepo.art
    val app = MediaRepo.pkg?.let { AppRepository.appFor(it) }
    Box(Modifier.size(sizeDp.dp).clip(RoundedCornerShape((sizeDp / 5).dp)).background(Color.DarkGray)) {
        when {
            art != null -> Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            app != null -> Image(app.icon, null, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun MediaControls(color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        GlyphButton({ MediaRepo.previous() }) { SkipGlyph(color, forward = false) }
        GlyphButton({ MediaRepo.playPause() }) { if (MediaRepo.playing) PauseGlyph(color) else PlayGlyph(color) }
        GlyphButton({ MediaRepo.next() }) { SkipGlyph(color, forward = true) }
    }
}

@Composable
private fun GlyphButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun PlayGlyph(c: Color) = Canvas(Modifier.size(22.dp)) {
    drawPath(Path().apply { moveTo(size.width * 0.15f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * 0.15f, size.height); close() }, c)
}

@Composable
private fun PauseGlyph(c: Color) = Canvas(Modifier.size(22.dp)) {
    val w = size.width * 0.3f
    drawRoundRect(c, Offset(size.width * 0.1f, 0f), Size(w, size.height), CornerRadius(3f))
    drawRoundRect(c, Offset(size.width * 0.6f, 0f), Size(w, size.height), CornerRadius(3f))
}

@Composable
private fun SkipGlyph(c: Color, forward: Boolean) = Canvas(Modifier.size(26.dp, 18.dp)) {
    val w = size.width / 2
    for (i in 0..1) {
        val x0 = i * w
        val p = Path().apply {
            if (forward) { moveTo(x0, 0f); lineTo(x0 + w, size.height / 2); lineTo(x0, size.height) } else {
                moveTo(x0 + w, 0f); lineTo(x0, size.height / 2); lineTo(x0 + w, size.height)
            }
            close()
        }
        drawPath(p, c)
    }
}

@Composable
private fun EqualizerBars(color: Color, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "b")
    val c by t.animateFloat(0.5f, 0.9f, infiniteRepeatable(tween(360), RepeatMode.Reverse), label = "c")
    Canvas(modifier) {
        val bw = size.width / 5
        listOf(a, b, c, b * 0.8f).forEachIndexed { i, v ->
            val h = size.height * v
            drawRoundRect(color, Offset(i * bw * 1.35f, (size.height - h) / 2), Size(bw, h), CornerRadius(bw / 2))
        }
    }
}

// ---------------- Kontrollzentrum ----------------

private class SysState(
    val wifi: Boolean, val bt: Boolean, val airplane: Boolean, val rotationLocked: Boolean,
    val dnd: Boolean, val brightness: Float, val volume: Float,
)

private fun readState(ctx: Context): SysState {
    val cr = ctx.contentResolver
    val wifi = runCatching { ctx.applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled }.getOrDefault(false)
    val bt = runCatching { ctx.getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true }.getOrDefault(false)
    val air = runCatching { Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 }.getOrDefault(false)
    val rot = runCatching { Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1) == 0 }.getOrDefault(false)
    val dnd = runCatching {
        ctx.getSystemService(NotificationManager::class.java).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }.getOrDefault(false)
    val raw = runCatching { Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128) }.getOrDefault(128)
    val max = brightnessMax(raw)
    val am = ctx.getSystemService(AudioManager::class.java)
    val vol = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    return SysState(wifi, bt, air, rot, dnd, raw.toFloat() / max, vol)
}

private var seenBrightnessMax = 255
private fun brightnessMax(raw: Int): Int {
    if (raw > 255) seenBrightnessMax = maxOf(seenBrightnessMax, if (raw > 2047) 4095 else 2047)
    return seenBrightnessMax
}

@Composable
private fun ControlCenter(prefs: Prefs, run: (Intent) -> Unit, globalAction: (Int) -> Unit, close: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var st by remember { mutableStateOf(readState(ctx)) }
    LaunchedEffect(tick) {
        while (true) {
            st = readState(ctx)
            delay(1000)
        }
    }
    fun refreshSoon() {
        Handler(Looper.getMainLooper()).postDelayed({ tick++ }, 300)
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        val gap = 12.dp
        val unit = (maxWidth - gap * 3) / 4
        Column(Modifier.padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                // Verbindungen
                Tile(prefs, unit * 2 + gap, unit * 2 + gap) {
                    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            RoundToggle("✈", st.airplane, Ios.orange) { run(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }
                            RoundToggle("⇅", false, Ios.green) {
                                run(if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_WIRELESS_SETTINGS))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            RoundToggle("ᯤ", st.wifi, Ios.blue) {
                                if (Build.VERSION.SDK_INT >= 29) run(Intent(Settings.Panel.ACTION_WIFI))
                                else {
                                    @Suppress("DEPRECATION")
                                    runCatching { ctx.applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled = !st.wifi }
                                    refreshSoon()
                                }
                            }
                            RoundToggle("ᛒ", st.bt, Ios.blue) { run(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                        }
                    }
                }
                // Musik
                Tile(prefs, unit * 2 + gap, unit * 2 + gap, onClick = { MediaRepo.open(); close() }) {
                    Column(Modifier.fillMaxSize().padding(14.dp)) {
                        if (MediaRepo.active) {
                            AlbumArt(40)
                            Spacer(Modifier.height(6.dp))
                            Text(MediaRepo.title.orEmpty(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(MediaRepo.artist.orEmpty(), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.weight(1f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Box(Modifier.clickable { MediaRepo.previous() }.padding(4.dp)) { SkipGlyph(Color.White, false) }
                                Box(Modifier.clickable { MediaRepo.playPause() }.padding(4.dp)) { if (MediaRepo.playing) PauseGlyph(Color.White) else PlayGlyph(Color.White) }
                                Box(Modifier.clickable { MediaRepo.next() }.padding(4.dp)) { SkipGlyph(Color.White, true) }
                            }
                        } else {
                            Text("Nicht wiedergegeben", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                            Spacer(Modifier.weight(1f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                SkipGlyph(Color.White.copy(alpha = 0.4f), false)
                                PlayGlyph(Color.White.copy(alpha = 0.4f))
                                SkipGlyph(Color.White.copy(alpha = 0.4f), true)
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        SmallTile(prefs, unit, if (st.rotationLocked) "🔒" else "🔄", st.rotationLocked, Ios.red) {
                            if (Settings.System.canWrite(ctx)) {
                                Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (st.rotationLocked) 1 else 0)
                                refreshSoon()
                            } else run(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName)))
                        }
                        SmallTile(prefs, unit, "🌙", st.dnd, Color(0xFF5E5CE6)) {
                            val nm = ctx.getSystemService(NotificationManager::class.java)
                            if (nm.isNotificationPolicyAccessGranted) {
                                nm.setInterruptionFilter(if (st.dnd) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                                refreshSoon()
                            } else run(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        TorchTile(prefs, unit)
                        SmallTile(prefs, unit, "📷", false, Color.White) { run(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
                    }
                }
                VerticalSlider(prefs, unit, unit * 2 + gap, st.brightness, "☀") { v ->
                    if (Settings.System.canWrite(ctx)) {
                        val cr = ctx.contentResolver
                        runCatching {
                            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (v * seenBrightnessMax).roundToInt().coerceAtLeast(1))
                        }
                        st = readState(ctx)
                    } else run(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName)))
                }
                VerticalSlider(prefs, unit, unit * 2 + gap, st.volume, "🔊") { v ->
                    val am = ctx.getSystemService(AudioManager::class.java)
                    runCatching {
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, (v * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).roundToInt(), 0)
                    }
                    st = readState(ctx)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                SmallTile(prefs, unit, "⏰", false, Color.White) { run(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
                SmallTile(prefs, unit, "⏱", false, Color.White) { run(Intent(AlarmClock.ACTION_SHOW_TIMERS)) }
                SmallTile(prefs, unit, "🧮", false, Color.White) {
                    run(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
                }
                SmallTile(prefs, unit, "⚙", false, Color.White) {
                    close()
                    globalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                }
            }
            Text(
                "⚙ öffnet die Android-Schnelleinstellungen",
                color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun Tile(prefs: Prefs, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(w, h)
            .frosted(RoundedCornerShape(24.dp), Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.18f), prefs.glass)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

@Composable
private fun SmallTile(prefs: Prefs, size: androidx.compose.ui.unit.Dp, glyph: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .then(
                if (active) Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White)
                else Modifier.frosted(RoundedCornerShape(24.dp), Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.18f), prefs.glass),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 24.sp, color = if (active) activeColor else Color.White)
    }
}

@Composable
private fun TorchTile(prefs: Prefs, size: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val cm = remember { ctx.getSystemService(CameraManager::class.java) }
    val camId = remember {
        runCatching { cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } }.getOrNull()
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
    SmallTile(prefs, size, "🔦", on, Color.Black) { camId?.let { runCatching { cm.setTorchMode(it, !on) } } }
}

@Composable
private fun RoundToggle(glyph: String, active: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (active) color else Color.White.copy(alpha = 0.22f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun VerticalSlider(
    prefs: Prefs,
    w: androidx.compose.ui.unit.Dp,
    h: androidx.compose.ui.unit.Dp,
    value: Float,
    glyph: String,
    onChange: (Float) -> Unit,
) {
    var local by remember { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(value)
    val shown = if (dragging) local else value
    val hPx = with(LocalDensity.current) { h.toPx() }
    Box(
        Modifier
            .size(w, h)
            .frosted(RoundedCornerShape(24.dp), Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.18f), prefs.glass)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        local = current
                        dragging = true
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onVerticalDrag = { ch, dy ->
                        ch.consume()
                        local = (local - dy / hPx).coerceIn(0f, 1f)
                        onChange(local)
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    local = (1f - o.y / hPx).coerceIn(0f, 1f)
                    onChange(local)
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(shown.coerceIn(0f, 1f)).background(Color.White))
        Text(glyph, fontSize = 22.sp, modifier = Modifier.padding(bottom = 14.dp))
    }
}

// ============================================================================
// Dynamic Island
// ============================================================================

private enum class IslandMode { IDLE, MEDIA, CHARGE, NOTIF, MEDIA_EXPANDED }

@Composable
fun DynamicIsland(prefs: Prefs, onOpenCenter: () -> Unit) {
    val startTick = remember { NotificationRepo.postedTick }
    val startCharge = remember { IslandEvents.chargeTick }
    var notif by remember { mutableStateOf<Notif?>(null) }
    var charge by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(NotificationRepo.postedTick) {
        val n = NotificationRepo.lastPosted
        if (NotificationRepo.postedTick > startTick && n != null) {
            notif = n
            delay(4500)
            notif = null
        }
    }
    LaunchedEffect(IslandEvents.chargeTick) {
        if (IslandEvents.chargeTick > startCharge) {
            charge = true
            delay(3000)
            charge = false
        }
    }
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(7000)
            expanded = false
        }
    }
    val mode = when {
        notif != null -> IslandMode.NOTIF
        charge -> IslandMode.CHARGE
        expanded && MediaRepo.active -> IslandMode.MEDIA_EXPANDED
        MediaRepo.active && MediaRepo.playing -> IslandMode.MEDIA
        else -> IslandMode.IDLE
    }
    val big = mode == IslandMode.NOTIF || mode == IslandMode.MEDIA_EXPANDED

    LauncherTheme(prefs, dark = true) {
        Box(
            Modifier
                .padding(4.dp)
                .animateContentSize(spring(dampingRatio = 0.72f, stiffness = 420f))
                .clip(RoundedCornerShape(if (big) 34.dp else 20.dp))
                .background(Color.Black)
                .pointerInput(mode) {
                    detectTapGestures(
                        onTap = {
                            when (mode) {
                                IslandMode.NOTIF -> notif?.let {
                                    NotificationRepo.open(it)
                                    notif = null
                                }
                                IslandMode.MEDIA, IslandMode.IDLE -> if (MediaRepo.active) expanded = true else onOpenCenter()
                                IslandMode.MEDIA_EXPANDED -> expanded = false
                                IslandMode.CHARGE -> charge = false
                            }
                        },
                        onLongPress = { if (MediaRepo.active) expanded = true else onOpenCenter() },
                    )
                },
        ) {
            when (mode) {
                IslandMode.IDLE -> Box(Modifier.size(prefs.islandWidth.dp, 32.dp))
                IslandMode.MEDIA -> Row(
                    Modifier.size((prefs.islandWidth + 100).dp, 32.dp).padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlbumArt(20)
                    Spacer(Modifier.weight(1f))
                    EqualizerBars(Ios.orange, Modifier.size(20.dp, 14.dp))
                }
                IslandMode.CHARGE -> Row(
                    Modifier.size((prefs.islandWidth + 120).dp, 32.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⚡ Lädt", color = Ios.green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("${IslandEvents.chargeLevel} %", color = Ios.green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                IslandMode.NOTIF -> {
                    val n = notif!!
                    val app = AppRepository.appFor(n.pkg)
                    Row(Modifier.width(360.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (app != null) IconImage(app, prefs, sizeDp = 40)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.title.ifEmpty { app?.originalLabel ?: "" }, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(n.text, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                IslandMode.MEDIA_EXPANDED -> Column(Modifier.width(360.dp).padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AlbumArt(54)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(MediaRepo.title.orEmpty(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(MediaRepo.artist.orEmpty(), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (MediaRepo.playing) EqualizerBars(Ios.orange, Modifier.size(22.dp, 16.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    MediaControls(Color.White)
                }
            }
        }
    }
}
