package com.cleanlauncher.lock

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlauncher.data.AppRepository
import com.cleanlauncher.data.Prefs
import com.cleanlauncher.data.WallpaperRepo
import com.cleanlauncher.system.Notif
import com.cleanlauncher.system.NotificationRepo
import com.cleanlauncher.ui.IconImage
import com.cleanlauncher.ui.Ios
import com.cleanlauncher.ui.LauncherTheme
import com.cleanlauncher.ui.frosted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

/**
 * iOS-artiger Sperrbildschirm. Er liegt ÜBER der System-Sperre:
 * Nach oben wischen -> das System fragt PIN/Fingerabdruck ab (sicher), danach Startbildschirm.
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
                    onUnlock = { done -> unlock(cancelled = done) },
                    onOpenNotification = { n -> openNotification(n) },
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

    override fun onStop() {
        super.onStop()
        // Wurde über die Home-Taste o. Ä. verlassen, während entsperrt -> schließen
        val pm = getSystemService(PowerManager::class.java)
        if (!km.isKeyguardLocked && pm.isInteractive) finish()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    private fun finishUnlocked() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    /** Entsperren: bei gesperrtem Gerät fragt das System nach PIN/Fingerabdruck. */
    private fun unlock(cancelled: () -> Unit, after: (() -> Unit)? = null) {
        if (km.isKeyguardLocked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    after?.invoke()
                    finishUnlocked()
                }

                override fun onDismissCancelled() = cancelled()
                override fun onDismissError() = cancelled()
            })
        } else {
            after?.invoke()
            finishUnlocked()
        }
    }

    private fun openNotification(n: Notif) {
        unlock(cancelled = {}) {
            try {
                n.contentIntent?.send()
            } catch (_: Exception) {
                AppRepository.appFor(n.pkg)?.let { AppRepository.launch(this, it, null, null, false) }
            }
        }
    }
}

@Composable
private fun LockScreen(
    prefs: Prefs,
    unlocked: Boolean,
    resetTick: Int,
    onUnlock: (reset: () -> Unit) -> Unit,
    onOpenNotification: (Notif) -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val screenH = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    LaunchedEffect(resetTick) { offset.snapTo(0f) }
    BackHandler { }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000)
        }
    }

    val wallpaper = WallpaperRepo.lock
    val progress = (-offset.value / screenH).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { ch, dy ->
                        ch.consume()
                        scope.launch { offset.snapTo((offset.value + dy).coerceAtMost(0f)) }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (offset.value < -size.height * 0.18f) {
                                offset.animateTo(-size.height.toFloat(), tween(220))
                                onUnlock { scope.launch { offset.animateTo(0f, tween(250)) } }
                            } else {
                                offset.animateTo(0f, tween(250))
                            }
                        }
                    },
                )
            },
    ) {
        if (wallpaper != null) {
            Image(
                wallpaper, null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - progress * 0.6f },
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
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = prefs.lockDim)))

        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = offset.value
                    alpha = 1f - progress * 1.3f
                }
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(10.dp))
            Padlock(open = unlocked, modifier = Modifier.size(22.dp, 26.dp))
            Spacer(Modifier.height(8.dp))
            val locale = Locale.getDefault()
            val datePattern = remember(locale) { DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM") }
            val clockColor = Color(prefs.lockClockColor)
            Text(
                DateFormat.format(datePattern, Date(now)).toString(),
                color = clockColor,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 2f), 12f)),
            )
            Text(
                DateFormat.format(if (prefs.clock24h) "HH:mm" else "h:mm", Date(now)).toString(),
                color = clockColor,
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 100.sp,
                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 3f), 18f)),
            )

            // Mitteilungen
            val notifs = if (prefs.lockNotifications) NotificationRepo.list else emptyList()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (notifs.isEmpty()) {
                    Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp)) {
                        com.cleanlauncher.ui.MediaPlayerCard(prefs)
                        if (prefs.lockNotifications && !com.cleanlauncher.system.NotificationRepo.hasAccess(ctx)) {
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
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
                        reverseLayout = false,
                    ) {
                        if (com.cleanlauncher.system.MediaRepo.active) item(key = "media") { com.cleanlauncher.ui.MediaPlayerCard(prefs) }
                        items(notifs.take(20), key = { it.key }) { n ->
                            NotificationCard(prefs, n, hideContent = prefs.lockHideContent && !unlocked) { onOpenNotification(n) }
                        }
                    }
                }
            }

            // Taschenlampe & Kamera
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 46.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (prefs.lockTorch) TorchButton() else Spacer(Modifier.size(50.dp))
                Spacer(Modifier.weight(1f))
                if (prefs.lockCamera) {
                    RoundButton({
                        try {
                            ctx.startActivity(
                                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } catch (_: Exception) {
                        }
                    }, active = false) { CameraGlyph(Color.White) }
                } else Spacer(Modifier.size(50.dp))
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

@Composable
private fun NotificationCard(prefs: Prefs, n: Notif, hideContent: Boolean, onClick: () -> Unit) {
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
                if (hideContent) "Mitteilung" else n.text,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RoundButton(onClick: () -> Unit, active: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(if (active) Color.White else Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun TorchButton() {
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
    androidx.compose.runtime.DisposableEffect(camId) {
        val cb = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == camId) on = enabled
            }
        }
        runCatching { cm.registerTorchCallback(cb, Handler(Looper.getMainLooper())) }
        onDispose { runCatching { cm.unregisterTorchCallback(cb) } }
    }
    RoundButton({ camId?.let { runCatching { cm.setTorchMode(it, !on) } } }, active = on) {
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

@Composable
private fun Padlock(open: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.15f
        val bodyTop = h * 0.45f
        val sl = w * 0.22f // linke Bügelseite
        val sr = w * 0.78f // rechte Bügelseite
        val r = (sr - sl) / 2
        val lift = if (open) h * 0.14f else 0f
        val arcTop = sw / 2 + (if (open) 0f else lift)
        // Bügel: Halbkreis + zwei Beine
        drawArc(
            Color.White, 180f, 180f, false,
            topLeft = Offset(sl, arcTop),
            size = Size(r * 2, r * 2),
            style = Stroke(sw),
        )
        val legTop = arcTop + r
        drawLine(Color.White, Offset(sr, legTop), Offset(sr, bodyTop), sw)
        drawLine(Color.White, Offset(sl, legTop), Offset(sl, if (open) legTop + h * 0.06f else bodyTop), sw)
        drawRoundRect(Color.White, Offset(0f, bodyTop), Size(w, h - bodyTop), CornerRadius(w * 0.16f))
    }
}
