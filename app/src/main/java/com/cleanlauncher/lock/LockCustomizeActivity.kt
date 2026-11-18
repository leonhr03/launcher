package com.cleanlauncher.lock

import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlauncher.data.Prefs
import com.cleanlauncher.data.WallpaperRepo
import com.cleanlauncher.ui.Ios
import com.cleanlauncher.ui.LauncherTheme
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale

/**
 * Sperrbildschirm anpassen wie beim iPhone: oben die Live-Vorschau, unten Schrift, Stärke, Größe und Farbe.
 * Öffnet sich nach langem Druck auf die Uhr (nach dem Entsperren).
 */
class LockCustomizeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        WallpaperRepo.init(this)
        setContent {
            val prefs = remember { Prefs.get(this) }
            LauncherTheme(prefs, dark = true) {
                LockCustomizer(prefs, onDone = { finish() })
            }
        }
    }
}

private val CLOCK_COLORS = listOf(
    0xFFFFFFFF, 0xFFF2F2F7, 0xFFFFD60A, 0xFFFF9F0A, 0xFFFF453A, 0xFFFF375F,
    0xFFBF5AF2, 0xFF5E5CE6, 0xFF0A84FF, 0xFF64D2FF, 0xFF30D158, 0xFFE8D5B5, 0xFF1C1C1E,
).map { it.toInt() }

private val WEIGHTS = listOf(300 to "Leicht", 400 to "Normal", 500 to "Mittel", 600 to "Halbfett", 700 to "Fett", 900 to "Extra")

@Composable
private fun LockCustomizer(prefs: Prefs, onDone: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000)
        }
    }
    val wallpaper = WallpaperRepo.lock
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (wallpaper != null) {
            Image(wallpaper, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF1B2A4A), Color(0xFF4A3B6B), Color(0xFF1A1A2E))),
                ),
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = prefs.lockDim)))

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Kopfzeile
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Anpassen", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "Fertig",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Ios.blue)
                        .clickable(onClick = onDone)
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                )
            }

            // Live-Vorschau mit gestricheltem Rahmen wie beim iPhone
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val locale = Locale.getDefault()
                val datePattern = remember(locale) { DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM") }
                Text(
                    DateFormat.format(datePattern, Date(now)).toString(),
                    color = Color(prefs.lockClockColor),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                LockClockText(prefs, now)
            }

            Spacer(Modifier.weight(1f))

            // Auswahl unten
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Color(0xF21C1C1E))
                    .padding(vertical = 18.dp),
            ) {
                Label("Schrift")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LOCK_CLOCK_FONTS.forEachIndexed { i, name ->
                        val sel = prefs.lockClockFont == i
                        val family = remember(i) { lockClockFamily(i) }
                        Column(
                            Modifier
                                .size(78.dp, 74.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (sel) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
                                .border(if (sel) 2.dp else 0.dp, if (sel) Color.White else Color.Transparent, RoundedCornerShape(16.dp))
                                .clickable { prefs.lockClockFont = i },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("12", color = Color.White, fontSize = 30.sp, fontFamily = family, fontWeight = FontWeight(prefs.lockClockWeight))
                            Text(name, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Label("Stärke")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WEIGHTS.forEach { (w, name) ->
                        val sel = prefs.lockClockWeight == w
                        Text(
                            name,
                            color = if (sel) Color.Black else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight(w),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (sel) Color.White else Color.White.copy(alpha = 0.12f))
                                .clickable { prefs.lockClockWeight = w }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Label("Größe")
                Slider(
                    value = prefs.lockClockSize.toFloat(),
                    onValueChange = { prefs.lockClockSize = it.toInt() },
                    valueRange = 60f..130f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Spacer(Modifier.height(8.dp))
                Label("Farbe")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CLOCK_COLORS.forEach { argb ->
                        val sel = prefs.lockClockColor == argb
                        Box(
                            Modifier
                                .size(36.dp)
                                .border(if (sel) 2.5.dp else 0.dp, if (sel) Color.White else Color.Transparent, CircleShape)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                .clickable { prefs.lockClockColor = argb },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Toggle("Widgets", prefs.lockWidgets, Modifier.weight(1f)) { prefs.lockWidgets = it }
                    Toggle("24 Std.", prefs.clock24h, Modifier.weight(1f)) { prefs.clock24h = it }
                    Text(
                        "Zurücksetzen",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .weight(1.3f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable {
                                prefs.lockClockFont = 0
                                prefs.lockClockWeight = 700
                                prefs.lockClockSize = 96
                                prefs.lockClockColor = Color.White.toArgb()
                            }
                            .padding(vertical = 12.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(Locale.getDefault()),
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Toggle(text: String, on: Boolean, modifier: Modifier, onChange: (Boolean) -> Unit) {
    Text(
        text,
        color = if (on) Color.Black else Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) Color.White else Color.White.copy(alpha = 0.12f))
            .clickable { onChange(!on) }
            .padding(vertical = 12.dp),
    )
}
