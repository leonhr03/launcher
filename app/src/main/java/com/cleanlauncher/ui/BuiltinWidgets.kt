package com.cleanlauncher.ui

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlauncher.data.BuiltinWidget
import com.cleanlauncher.data.Prefs
import com.cleanlauncher.data.WidgetStyle
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private class WColors(val bg: Color, val text: Color, val sub: Color)

private fun widgetColors(style: WidgetStyle) = when (style) {
    WidgetStyle.LIGHT -> WColors(Color.White, Color.Black, Color(0xFF8E8E93))
    WidgetStyle.DARK -> WColors(Color(0xFF1C1C1E), Color.White, Color(0xFF8E8E93))
    WidgetStyle.GLASS -> WColors(Color.Transparent, Color.White, Color.White.copy(alpha = 0.75f))
}

/** Rahmen für Widgets: abgerundet wie bei iOS, hell/dunkel/Glas. */
@Composable
fun WidgetCard(prefs: Prefs, modifier: Modifier = Modifier, forceStyle: WidgetStyle? = null, content: @Composable () -> Unit) {
    val style = forceStyle ?: prefs.widgetStyle
    val shape = RoundedCornerShape(prefs.widgetCorner.dp)
    val m = if (style == WidgetStyle.GLASS) {
        modifier.frosted(shape, Color.White.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.35f), prefs.glass)
    } else {
        modifier.frosted(shape, widgetColors(style).bg, widgetColors(style).bg, false)
    }
    Box(m) { content() }
}

@Composable
private fun rememberNow(everySecond: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(everySecond) {
        while (true) {
            now = System.currentTimeMillis()
            delay(if (everySecond) 1000 - now % 1000 else 60_000 - now % 60_000 + 30)
        }
    }
    return now
}

@Composable
fun BuiltinWidgetView(type: BuiltinWidget, prefs: Prefs, modifier: Modifier = Modifier) {
    when (type) {
        BuiltinWidget.CLOCK_ANALOG -> AnalogClockWidget(prefs, modifier)
        BuiltinWidget.CLOCK_DIGITAL -> DigitalClockWidget(prefs, modifier)
        BuiltinWidget.CALENDAR -> CalendarWidget(prefs, modifier)
        BuiltinWidget.BATTERY -> BatteryWidget(prefs, modifier)
    }
}

@Composable
private fun AnalogClockWidget(prefs: Prefs, modifier: Modifier) {
    val now = rememberNow(true)
    val colors = widgetColors(prefs.widgetStyle)
    val faceColor = if (prefs.widgetStyle == WidgetStyle.LIGHT) Color.White else Color(0xFF1C1C1E)
    val handColor = if (prefs.widgetStyle == WidgetStyle.LIGHT) Color.Black else Color.White
    WidgetCard(prefs, modifier, forceStyle = if (prefs.widgetStyle == WidgetStyle.GLASS) WidgetStyle.GLASS else null) {
        Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().aspectRatio(1f)) {
                val r = min(size.width, size.height) / 2f
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(faceColor, r, c)
                // Ziffern
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = colors.text.toArgb()
                    textSize = r * 0.24f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                for (i in 1..12) {
                    val a = Math.toRadians((i * 30 - 90).toDouble())
                    val tx = c.x + cos(a).toFloat() * r * 0.78f
                    val ty = c.y + sin(a).toFloat() * r * 0.78f - (paint.descent() + paint.ascent()) / 2
                    drawContext.canvas.nativeCanvas.drawText(i.toString(), tx, ty, paint)
                }
                val cal = Calendar.getInstance().apply { timeInMillis = now }
                val h = cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60f
                val m = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
                val s = cal.get(Calendar.SECOND)
                rotate(h * 30f, c) {
                    drawLine(handColor, c, Offset(c.x, c.y - r * 0.5f), r * 0.07f, StrokeCap.Round)
                }
                rotate(m * 6f, c) {
                    drawLine(handColor, c, Offset(c.x, c.y - r * 0.75f), r * 0.05f, StrokeCap.Round)
                }
                rotate(s * 6f, c) {
                    drawLine(Ios.orange, Offset(c.x, c.y + r * 0.15f), Offset(c.x, c.y - r * 0.85f), r * 0.022f, StrokeCap.Round)
                }
                drawCircle(Ios.orange, r * 0.05f, c)
                drawCircle(faceColor, r * 0.02f, c)
            }
        }
    }
}

@Composable
private fun DigitalClockWidget(prefs: Prefs, modifier: Modifier) {
    val ctx = LocalContext.current
    val now = rememberNow(false)
    val colors = widgetColors(prefs.widgetStyle)
    val locale = Locale.getDefault()
    val datePattern = remember(locale) { DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM") }
    val alarm = remember(now) {
        try {
            ctx.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime
        } catch (e: Exception) {
            null
        }
    }
    WidgetCard(prefs, modifier) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                DateFormat.format(datePattern, Date(now)).toString().uppercase(locale),
                color = Ios.red,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                DateFormat.format(if (prefs.clock24h) "HH:mm" else "h:mm", Date(now)).toString(),
                color = colors.text,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (alarm != null) "⏰ Wecker " + DateFormat.format(if (prefs.clock24h) "EE HH:mm" else "EE h:mm a", Date(alarm))
                else "Kein Wecker gestellt",
                color = colors.sub,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun CalendarWidget(prefs: Prefs, modifier: Modifier) {
    val now = rememberNow(false)
    val colors = widgetColors(prefs.widgetStyle)
    val locale = Locale.getDefault()
    WidgetCard(prefs, modifier) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(14.dp)) {
            val big = (maxHeight.value * 0.42f).coerceIn(28f, 64f)
            Column {
                Text(
                    DateFormat.format("EEEE", Date(now)).toString().uppercase(locale),
                    color = Ios.red,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    DateFormat.format("d", Date(now)).toString(),
                    color = colors.text,
                    fontSize = big.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = big.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    DateFormat.format("MMMM yyyy", Date(now)).toString(),
                    color = colors.sub,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun BatteryWidget(prefs: Prefs, modifier: Modifier) {
    val ctx = LocalContext.current
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
    val colors = widgetColors(prefs.widgetStyle)
    val ringColor = when {
        charging -> Ios.green
        level in 0..20 -> Ios.red
        else -> Ios.green
    }
    WidgetCard(prefs, modifier) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = size.minDimension * 0.11f
                    drawCircle(colors.sub.copy(alpha = 0.25f), size.minDimension / 2 - sw / 2, style = Stroke(sw))
                    drawArc(
                        ringColor, -90f, 360f * (level.coerceAtLeast(0) / 100f), false,
                        topLeft = Offset(sw / 2, sw / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw),
                        style = Stroke(sw, cap = StrokeCap.Round),
                    )
                }
                Text(if (charging) "⚡" else "📱", fontSize = 22.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    if (level >= 0) "$level %" else "–",
                    color = colors.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Suppress("unused")
private fun Modifier.bg(c: Color) = background(c)
