package com.cleanlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlauncher.data.AppRepository
import com.cleanlauncher.data.DrawerStyle
import com.cleanlauncher.data.GestureAction
import com.cleanlauncher.data.IconShape
import com.cleanlauncher.data.Prefs
import com.cleanlauncher.system.SystemActions
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private class Palette(val bg: Color, val card: Color, val text: Color, val sub: Color, val accent: Color, val divider: Color)

private val LocalPalette = androidx.compose.runtime.staticCompositionLocalOf {
    Palette(Color.Black, Color.DarkGray, Color.White, Color.Gray, Color(0xFF0A84FF), Color.Gray)
}

private enum class Screen { MAIN, HIDDEN, NAMES }

@Composable
fun SettingsScreen(onFinish: () -> Unit, autoImport: Boolean = false) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) {
        Palette(Color(0xFF000000), Color(0xFF1C1C1E), Color.White, Color(0xFF8E8E93), Color(0xFF0A84FF), Color(0xFF38383A))
    } else {
        Palette(Color(0xFFF2F2F7), Color.White, Color.Black, Color(0xFF6D6D72), Color(0xFF007AFF), Color(0xFFD1D1D6))
    }
    LauncherTheme(Prefs.get(LocalContext.current), dark) {
        androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides palette) {
            var screen by remember { mutableStateOf(Screen.MAIN) }
            BackHandler(screen != Screen.MAIN) { screen = Screen.MAIN }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(palette.bg),
            ) {
                when (screen) {
                    Screen.MAIN -> MainSettings(onBack = onFinish, open = { screen = it }, autoImport = autoImport)
                    Screen.HIDDEN -> HiddenAppsScreen { screen = Screen.MAIN }
                    Screen.NAMES -> NamesScreen { screen = Screen.MAIN }
                }
            }
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = p.accent)
        }
        Text(title, color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MainSettings(onBack: () -> Unit, open: (Screen) -> Unit, autoImport: Boolean) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    var resetDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Launcher", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
        ) {
            if (!SystemActions.isDefaultLauncher(ctx)) {
                Group {
                    ActionRow(
                        "Als Standard-Launcher festlegen",
                        "Wähle „Clean Launcher“ als Startbildschirm-App",
                        highlight = true,
                    ) { SystemActions.setAsDefaultLauncher(ctx) }
                }
            }

            SystemWideSection(prefs)
            ImportSection(prefs, autoImport)
            GestureNavSection()

            Preview(prefs)

            AppearanceSection(prefs)
            WallpaperSection(prefs)
            LockSection(prefs)

            Section("Raster")
            Group {
                IntSlider("Spalten", prefs.columns, 3..7) { prefs.columns = it }
                Divider()
                IntSlider("Zeilen", prefs.rows, 3..9) { prefs.rows = it }
                Divider()
                IntSlider("Seitenrand", prefs.gridPaddingH, 0..40, suffix = " dp") { prefs.gridPaddingH = it }
                Divider()
                IntSlider("Abstand oben", prefs.gridPaddingTop, 0..120, suffix = " dp") { prefs.gridPaddingTop = it }
            }

            Section("App-Symbole")
            Group {
                IntSlider("Symbolgröße", prefs.iconSize, 32..100, suffix = " dp") { prefs.iconSize = it }
                Divider()
                ChoiceRow("Form", IconShape.entries, prefs.iconShape, { it.title }) { prefs.iconShape = it }
                if (prefs.iconShape == IconShape.ROUNDED || prefs.iconShape == IconShape.TEARDROP) {
                    Divider()
                    IntSlider("Eckenradius", prefs.iconCornerPercent, 0..50, suffix = " %") { prefs.iconCornerPercent = it }
                }
                Divider()
                SwitchRow("Schatten", prefs.iconShadow) { prefs.iconShadow = it }
                Divider()
                SwitchRow("Drück-Animation", prefs.pressAnimation) { prefs.pressAnimation = it }
                Divider()
                SwitchRow("Öffnen-Animation (Zoom aus dem Symbol)", prefs.openAnimation) { prefs.openAnimation = it }
            }
            Footer("Tipp: Stell die Symbolgröße so ein, dass sie zu deinem bisherigen MIUI/HyperOS-Launcher passt. „Original“ zeigt die Symbole genau so, wie sie vom System kommen.")

            Section("Beschriftung")
            Group {
                SwitchRow("App-Namen anzeigen", prefs.showLabels) { prefs.showLabels = it }
                if (prefs.showLabels) {
                    Divider()
                    FloatSlider("Schriftgröße", prefs.labelSize, 8f..18f, step = 0.5f, suffix = " sp") { prefs.labelSize = it }
                    Divider()
                    ColorRow("Farbe", prefs.labelColor) { prefs.labelColor = it }
                    Divider()
                    SwitchRow("Schatten", prefs.labelShadow) { prefs.labelShadow = it }
                    Divider()
                    SwitchRow("Fett", prefs.labelBold) { prefs.labelBold = it }
                    Divider()
                    SwitchRow("Namen auch im Dock", prefs.dockShowLabels) { prefs.dockShowLabels = it }
                }
            }

            Section("Dock")
            Group {
                SwitchRow("Dock anzeigen", prefs.dockEnabled) { prefs.dockEnabled = it }
                if (prefs.dockEnabled) {
                    Divider()
                    IntSlider("Anzahl Apps", prefs.dockCount, 2..7) { prefs.dockCount = it }
                    Divider()
                    SwitchRow("Hintergrund", prefs.dockBackground) { prefs.dockBackground = it }
                    if (prefs.dockBackground) {
                        Divider()
                        FloatSlider("Deckkraft", prefs.dockOpacity, 0f..1f, step = 0.01f, percent = true) { prefs.dockOpacity = it }
                        Divider()
                        ColorRow("Farbe", prefs.dockColor) { prefs.dockColor = it }
                        Divider()
                        IntSlider("Eckenradius", prefs.dockCorner, 0..48, suffix = " dp") { prefs.dockCorner = it }
                    }
                    Divider()
                    IntSlider("Seitenabstand", prefs.dockMargin, 0..40, suffix = " dp") { prefs.dockMargin = it }
                }
            }

            Section("Startbildschirm")
            Group {
                SwitchRow("24-Stunden-Uhr (Widgets & Sperrbildschirm)", prefs.clock24h) { prefs.clock24h = it }
                Divider()
                SwitchRow("Seitenpunkte anzeigen", prefs.showPageIndicator) { prefs.showPageIndicator = it }
                if (prefs.showPageIndicator) {
                    Divider()
                    ColorRow("Farbe der Seitenpunkte", prefs.pageIndicatorColor) { prefs.pageIndicatorColor = it }
                }
                Divider()
                FloatSlider("Hintergrund abdunkeln", prefs.wallpaperDim, 0f..0.8f, step = 0.01f, percent = true) { prefs.wallpaperDim = it }
                Divider()
                SwitchRow("Statusleiste ausblenden", prefs.hideStatusBar) { prefs.hideStatusBar = it }
                Divider()
                SwitchRow("Dunkle Statusleisten-Symbole", prefs.darkStatusIcons, "Für helle Hintergrundbilder") { prefs.darkStatusIcons = it }
                Divider()
                SwitchRow("Hochformat sperren", prefs.lockPortrait) { prefs.lockPortrait = it }
                Divider()
                SwitchRow("Neue Apps automatisch hinzufügen", prefs.autoAddNewApps) { prefs.autoAddNewApps = it }
            }

            Section("Gesten")
            Group {
                ChoiceRow("Nach oben wischen", GestureAction.entries, prefs.swipeUp, { it.title }) { prefs.swipeUp = it }
                Divider()
                ChoiceRow("Nach unten wischen", GestureAction.entries, prefs.swipeDown, { it.title }) { prefs.swipeDown = it }
                Divider()
                ChoiceRow("Doppelt tippen", GestureAction.entries, prefs.doubleTap, { it.title }) { prefs.doubleTap = it }
                Divider()
                ChoiceRow("Lange drücken (freie Fläche)", GestureAction.entries, prefs.longPress, { it.title }) { prefs.longPress = it }
                Divider()
                SwitchRow("Haptisches Feedback", prefs.haptics) { prefs.haptics = it }
                Divider()
                ActionRow(
                    "Bedienungshilfe für Gesten",
                    if (SystemActions.accessibilityEnabled) "Aktiv" else "Nötig für „Bildschirm sperren“ und „Zuletzt verwendete Apps“",
                ) { SystemActions.openAccessibilitySettings(ctx) }
            }

            Section("App-Mediathek & Suche")
            Group {
                ChoiceRow("Darstellung", DrawerStyle.entries, prefs.drawerStyle, { it.title }) { prefs.drawerStyle = it }
                Divider()
                FloatSlider("Hintergrund-Deckkraft", prefs.drawerOpacity, 0f..1f, step = 0.01f, percent = true) { prefs.drawerOpacity = it }
                Divider()
                SwitchRow("Hintergrund unscharf (Android 12+)", prefs.drawerBlur) { prefs.drawerBlur = it }
                Divider()
                SwitchRow("Tastatur automatisch öffnen", prefs.drawerAutoKeyboard) { prefs.drawerAutoKeyboard = it }
                Divider()
                SwitchRow("Websuche anbieten", prefs.drawerWebSearch) { prefs.drawerWebSearch = it }
            }

            Section("Apps")
            Group {
                ActionRow("Ausgeblendete Apps", "${prefs.hiddenApps.size} ausgeblendet") { open(Screen.HIDDEN) }
                Divider()
                ActionRow("Umbenannte Apps", "${prefs.customLabels.size} umbenannt") { open(Screen.NAMES) }
                Divider()
                ActionRow(
                    "Mitteilungszugriff",
                    if (com.cleanlauncher.system.NotificationRepo.hasAccess(ctx)) "Aktiv – rote Badges & Mitteilungen auf dem Sperrbildschirm"
                    else "Nötig für rote Badges und Mitteilungen auf dem Sperrbildschirm",
                ) { openSafe(ctx, android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
            Footer("Lange auf eine App drücken: Menü (umbenennen, Dock, entfernen …). Lange drücken und ziehen: verschieben. App auf eine andere App ziehen: Ordner. Im Bearbeitungsmodus oben links „+“: Widgets hinzufügen.")

            Section("Zurücksetzen")
            Group {
                ActionRow("Design zurücksetzen", "Anordnung und ausgeblendete Apps bleiben erhalten") { prefs.resetDesign() }
                Divider()
                ActionRow("Alles zurücksetzen", "Auch Anordnung, Dock und Namen", destructive = true) { resetDialog = true }
            }
        }
    }

    if (resetDialog) {
        AlertDialog(
            onDismissRequest = { resetDialog = false },
            title = { Text("Alles zurücksetzen?") },
            text = { Text("Alle Einstellungen, die Anordnung der Apps, das Dock und eigene Namen werden gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    prefs.resetEverything()
                    com.cleanlauncher.data.Workspace.get(ctx).reset()
                    resetDialog = false
                }) { Text("Zurücksetzen") }
            },
            dismissButton = { TextButton(onClick = { resetDialog = false }) { Text("Abbrechen") } },
        )
    }
}

/** Kleine Live-Vorschau mit echten Symbolen in der eingestellten Größe/Form. */
@Composable
private fun Preview(prefs: Prefs) {
    val apps = AppRepository.apps
    val sample = remember(apps) { apps.take(4) }
    if (sample.isEmpty()) return
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF34407A), Color(0xFF8B5B9E), Color(0xFFDB8A73))))
            .background(Color.Black.copy(alpha = prefs.wallpaperDim))
            .padding(vertical = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            sample.take(prefs.columns.coerceAtMost(4)).forEach { app ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconImage(app, prefs)
                    if (prefs.showLabels) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            prefs.labelFor(app.key, app.originalLabel),
                            style = labelStyle(prefs),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width((prefs.iconSize + 20).dp),
                        )
                    }
                }
            }
        }
    }
}

// ======================= Unterseiten =======================

@Composable
private fun HiddenAppsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    val p = LocalPalette.current
    val apps = AppRepository.apps
    Column(Modifier.fillMaxSize()) {
        TopBar("Ausgeblendete Apps", onBack)
        Text(
            "Ausgeblendete Apps erscheinen weder auf dem Startbildschirm noch in der App-Mediathek.",
            color = p.sub,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            items(apps, key = { it.key }) { app ->
                val hidden = app.key in prefs.hiddenApps
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(p.card)
                        .clickable {
                            if (hidden) {
                                prefs.hiddenApps = prefs.hiddenApps - app.key
                                val ws = com.cleanlauncher.data.Workspace.get(ctx)
                                if (!ws.contains(app.key)) {
                                    val hiddenNow = prefs.hiddenApps
                                    ws.addApp(app.key, prefs.columns, prefs.rows, { i ->
                                        i.type != com.cleanlauncher.data.ItemType.APP || (i.appKey !in hiddenNow)
                                    })
                                }
                            } else {
                                prefs.hiddenApps = prefs.hiddenApps + app.key
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconImage(app, prefs, sizeDp = 36)
                    Spacer(Modifier.width(12.dp))
                    Text(prefs.labelFor(app.key, app.originalLabel), color = p.text, modifier = Modifier.weight(1f))
                    Checkbox(checked = hidden, onCheckedChange = null)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun NamesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    val p = LocalPalette.current
    val byKey = AppRepository.apps.associateBy { it.key }
    Column(Modifier.fillMaxSize()) {
        TopBar("Umbenannte Apps", onBack)
        if (prefs.customLabels.isEmpty()) {
            Text(
                "Noch keine App umbenannt. Lange auf ein App-Symbol drücken → „Umbenennen“.",
                color = p.sub,
                modifier = Modifier.padding(20.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            items(prefs.customLabels.entries.toList(), key = { it.key }) { (key, label) ->
                val app = byKey[key]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(p.card)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (app != null) IconImage(app, prefs, sizeDp = 36)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, color = p.text)
                        Text(app?.originalLabel ?: key, color = p.sub, fontSize = 12.sp)
                    }
                    TextButton(onClick = { prefs.customLabels = prefs.customLabels - key }) { Text("Original") }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

// ======================= Bausteine =======================

@Composable
private fun Section(title: String) {
    val p = LocalPalette.current
    Text(
        title.uppercase(),
        color = p.sub,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 32.dp, top = 22.dp, bottom = 6.dp),
    )
}

@Composable
private fun Footer(text: String) {
    val p = LocalPalette.current
    Text(text, color = p.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 6.dp))
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.card),
        content = content,
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = LocalPalette.current.divider)
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = p.sub, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String? = null,
    highlight: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = when {
                    destructive -> Color(0xFFFF453A)
                    highlight -> p.accent
                    else -> p.text
                },
                fontSize = 16.sp,
                fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (subtitle != null) Text(subtitle, color = p.sub, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.sub)
    }
}

@Composable
private fun IntSlider(title: String, value: Int, range: IntRange, suffix: String = "", onChange: (Int) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row {
            Text(title, color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("$value$suffix", color = p.sub, fontSize = 16.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = if (range.last - range.first <= 10) range.last - range.first - 1 else 0,
        )
    }
}

@Composable
private fun FloatSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    suffix: String = "",
    percent: Boolean = false,
    onChange: (Float) -> Unit,
) {
    val p = LocalPalette.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row {
            Text(title, color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(
                if (percent) "${(value * 100).roundToInt()} %" else "${"%.1f".format(value)}$suffix",
                color = p.sub,
                fontSize = 16.sp,
            )
        }
        Slider(
            value = value,
            onValueChange = { onChange(((it / step).roundToInt() * step).coerceIn(range.start, range.endInclusive)) },
            valueRange = range,
        )
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(label(selected), color = p.sub, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.sub)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { o ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSelect(o)
                                    open = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = o == selected, onClick = null)
                            Spacer(Modifier.width(10.dp))
                            Text(label(o))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Schließen") } },
        )
    }
}

private val swatches = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFF8E8E93, 0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00,
    0xFF34C759, 0xFF00C7BE, 0xFF0A84FF, 0xFF5E5CE6, 0xFFAF52DE, 0xFFFF2D55,
).map { it.toInt() }

@Composable
private fun ColorRow(title: String, value: Int, onChange: (Int) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, color = p.text, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            swatches.forEach { argb ->
                val sel = argb == value
                Box(
                    Modifier
                        .padding(end = 10.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(if (sel) 3.dp else 1.dp, if (sel) p.accent else p.divider, CircleShape)
                        .clickable { onChange(argb) },
                )
            }
        }
    }
}

@Suppress("unused")
private fun Color.argb() = toArgb()


// ======================= Neue Bereiche =======================

private fun openSafe(ctx: android.content.Context, intent: android.content.Intent, fallback: android.content.Intent? = null) {
    try {
        ctx.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        if (fallback != null) runCatching { ctx.startActivity(fallback.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

private fun appDetails(ctx: android.content.Context) = android.content.Intent(
    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    android.net.Uri.parse("package:" + ctx.packageName),
)

/** Erhöht sich bei jedem Zurückkehren in die Einstellungen (damit Berechtigungs-Status aktuell ist). */
@Composable
private fun resumeTick(): Int {
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var tick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

@Composable
private fun AppearanceSection(prefs: Prefs) {
    Section("Aussehen (iOS)")
    Group {
        SwitchRow("iOS-ähnliche Schrift", prefs.iosFont, "Inter – fast wie SF Pro") { prefs.iosFont = it }
        Divider()
        SwitchRow("Glas-Effekt", prefs.glass, "Dock, Ordner, Menüs & Widgets milchglasartig") { prefs.glass = it }
        Divider()
        SwitchRow("„Suchen“-Taste über dem Dock", prefs.searchPill, "Wie bei iOS 17 – beim Wischen erscheinen die Seitenpunkte") { prefs.searchPill = it }
        Divider()
        SwitchRow("Rote Badges (Mitteilungen)", prefs.badges) { prefs.badges = it }
        Divider()
        ChoiceRow("Widget-Stil", com.cleanlauncher.data.WidgetStyle.entries, prefs.widgetStyle, { it.title }) { prefs.widgetStyle = it }
        Divider()
        IntSlider("Widget-Ecken", prefs.widgetCorner, 0..40, suffix = " dp") { prefs.widgetCorner = it }
    }
}

@Composable
private fun WallpaperSection(prefs: Prefs) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    @Suppress("UNUSED_VARIABLE") val tick = resumeTick()
    var pending by remember { mutableStateOf<android.net.Uri?>(null) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> pending = uri }
    val hasAccess = WallpaperRepoAccess.has(tick)

    Section("Hintergrundbild")
    Group {
        ActionRow("Bild aus der Galerie wählen", "Für Startbildschirm und/oder Sperrbildschirm – aktiviert den Glas-Effekt") {
            picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        Divider()
        ActionRow(
            "Aktuelles Hintergrundbild übernehmen",
            if (hasAccess) "Aktiv – dein jetziges Hintergrundbild wird verwendet" else "Braucht „Zugriff auf alle Dateien“ (nur zum Lesen des Hintergrunds)",
        ) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && !hasAccess) {
                openSafe(
                    ctx,
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:" + ctx.packageName),
                    ),
                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                )
            } else {
                com.cleanlauncher.data.WallpaperRepo.reload()
            }
        }
        Divider()
        ActionRow("System-Hintergrundauswahl") { SystemActions.pickWallpaper(ctx) }
    }
    if (!com.cleanlauncher.data.WallpaperRepo.available) {
        Footer("Ohne Hintergrundbild-Zugriff wirken Dock & Ordner halbtransparent statt milchglasartig.")
    }

    pending?.let { uri ->
        fun apply(home: Boolean, lock: Boolean) {
            pending = null
            scope.launch {
                val ok = com.cleanlauncher.data.WallpaperRepo.setFromUri(ctx, uri, home, lock)
                android.widget.Toast.makeText(ctx, if (ok) "Hintergrundbild gesetzt" else "Konnte Bild nicht setzen", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        IosAlert(
            title = "Hintergrundbild verwenden für",
            message = null,
            buttons = listOf(
                AlertButton("Startbildschirm") { apply(true, false) },
                AlertButton("Sperrbildschirm") { apply(false, true) },
                AlertButton("Beide", bold = true) { apply(true, true) },
                AlertButton("Abbrechen") { pending = null },
            ),
            onDismiss = { pending = null },
        )
    }
}

private object WallpaperRepoAccess {
    @Suppress("UNUSED_PARAMETER")
    fun has(tick: Int) = com.cleanlauncher.data.WallpaperRepo.hasFileAccess()
}

@Composable
private fun LockSection(prefs: Prefs) {
    val ctx = LocalContext.current
    val tick = resumeTick()
    val overlay = remember(tick) { android.provider.Settings.canDrawOverlays(ctx) }
    val notif = remember(tick) { com.cleanlauncher.system.NotificationRepo.hasAccess(ctx) }
    val postPerm = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    val isXiaomi = remember { android.os.Build.MANUFACTURER.equals("Xiaomi", true) || android.os.Build.BRAND.equals("Redmi", true) || android.os.Build.BRAND.equals("POCO", true) }

    Section("Sperrbildschirm (iOS-Stil)")
    Group {
        SwitchRow("iOS-Sperrbildschirm", prefs.lockEnabled, "Große Uhr, Mitteilungen, Taschenlampe & Kamera") {
            prefs.lockEnabled = it
            if (it && android.os.Build.VERSION.SDK_INT >= 33) postPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            com.cleanlauncher.lock.LockService.sync(ctx)
        }
        if (prefs.lockEnabled) {
            Divider()
            ActionRow(
                if (overlay) "✓ Über anderen Apps anzeigen" else "① Über anderen Apps anzeigen erlauben",
                "Damit der Sperrbildschirm beim Ausschalten erscheinen darf",
                highlight = !overlay,
            ) {
                openSafe(
                    ctx,
                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + ctx.packageName)),
                    appDetails(ctx),
                )
            }
            if (isXiaomi) {
                Divider()
                ActionRow(
                    "② Xiaomi: „Auf Sperrbildschirm anzeigen“",
                    "Und „Pop-up-Fenster im Hintergrund anzeigen“ erlauben",
                    highlight = true,
                ) {
                    openSafe(
                        ctx,
                        android.content.Intent("miui.intent.action.APP_PERM_EDITOR")
                            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                            .putExtra("extra_pkgname", ctx.packageName),
                        appDetails(ctx),
                    )
                }
                Divider()
                ActionRow("③ Xiaomi: Autostart erlauben", "Damit der Sperrbildschirm nach dem Neustart wieder aktiv ist", highlight = true) {
                    openSafe(
                        ctx,
                        android.content.Intent().setComponent(
                            android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                        ),
                        appDetails(ctx),
                    )
                }
                Divider()
                ActionRow("④ Akku: „Keine Einschränkungen“", "Sonst beendet MIUI den Sperrbildschirm im Hintergrund") {
                    openSafe(ctx, appDetails(ctx))
                }
            }
            Divider()
            ActionRow(
                if (notif) "✓ Mitteilungszugriff" else "Mitteilungszugriff erlauben",
                "Für Mitteilungen auf dem Sperrbildschirm",
            ) { openSafe(ctx, android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            Divider()
            SwitchRow("Mitteilungen anzeigen", prefs.lockNotifications) { prefs.lockNotifications = it }
            if (prefs.lockNotifications) {
                Divider()
                SwitchRow("Inhalte erst nach Entsperren zeigen", prefs.lockHideContent, "Vor Fingerabdruck/PIN nur App-Name sichtbar") { prefs.lockHideContent = it }
            }
            Divider()
            SwitchRow("Taschenlampe-Taste", prefs.lockTorch) { prefs.lockTorch = it }
            Divider()
            SwitchRow("Kamera-Taste", prefs.lockCamera) { prefs.lockCamera = it }
            Divider()
            ColorRow("Farbe der Uhr", prefs.lockClockColor) { prefs.lockClockColor = it }
            Divider()
            FloatSlider("Hintergrund abdunkeln", prefs.lockDim, 0f..0.7f, step = 0.01f, percent = true) { prefs.lockDim = it }
            Divider()
            ActionRow("Vorschau anzeigen") {
                openSafe(ctx, android.content.Intent(ctx, com.cleanlauncher.lock.LockScreenActivity::class.java))
            }
        }
    }
    if (prefs.lockEnabled) {
        Footer("Deine echte Sperre (PIN, Muster, Fingerabdruck) bleibt aktiv: Nach dem Hochwischen fragt das System wie gewohnt danach. Mit Fingerabdruck entsperrst du direkt und wischst dann nur noch hoch – wie Face ID beim iPhone.")
    }
}


// ======================= Xiaomi-Layout übernehmen =======================

@Composable
private fun ImportSection(prefs: Prefs, autoImport: Boolean) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val tick = resumeTick()
    var result by remember { mutableStateOf<com.cleanlauncher.data.MiuiImport.Result?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val hasAccess = remember(tick) { com.cleanlauncher.data.WallpaperRepo.hasFileAccess() }

    fun run(block: () -> com.cleanlauncher.data.MiuiImport.Result) {
        busy = true
        scope.launch {
            try {
                result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
            } catch (e: com.cleanlauncher.data.MiuiImport.ImportException) {
                error = e.message
            } catch (e: Exception) {
                error = "Die Sicherung konnte nicht gelesen werden (${e.javaClass.simpleName})."
            }
            busy = false
        }
    }

    fun autoSearch() {
        val f = com.cleanlauncher.data.MiuiImport.findBackup()
        if (f == null) error = "Keine Startbildschirm-Sicherung gefunden. Erstelle zuerst eine Sicherung (siehe Anleitung) oder wähle die Datei selbst."
        else run { com.cleanlauncher.data.MiuiImport.read(ctx, f) }
    }

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) run { com.cleanlauncher.data.MiuiImport.read(ctx, uri) } }

    androidx.compose.runtime.LaunchedEffect(autoImport) {
        if (autoImport) {
            kotlinx.coroutines.delay(1500)
            autoSearch()
        }
    }

    Section("Xiaomi-Layout übernehmen")
    Group {
        ActionRow(
            if (busy) "Wird gelesen …" else "Sicherung automatisch suchen",
            if (hasAccess) "Sucht in MIUI/backup nach der neuesten Startbildschirm-Sicherung"
            else "Braucht „Zugriff auf alle Dateien“ – sonst unten die Datei selbst wählen",
            highlight = true,
        ) {
            if (!hasAccess && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                openSafe(
                    ctx,
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:" + ctx.packageName),
                    ),
                )
            } else autoSearch()
        }
        Divider()
        ActionRow("Sicherungsdatei selbst wählen", "Die .bak-Datei der Startbildschirm-Sicherung") {
            picker.launch(arrayOf("*/*"))
        }
    }
    Footer(
        "So geht’s: Auf dem Xiaomi Einstellungen → Über das Telefon → Sichern & wiederherstellen → " +
            "Mobilgerät (Lokale Sicherung) → Sichern → nur „Startbildschirm“ bzw. „Systemanwendungen → Startbildschirm“ auswählen " +
            "und OHNE Passwort sichern. Danach hier „automatisch suchen“. Seiten, Dock, Ordner und Widgets werden an dieselbe Stelle übernommen.",
    )

    error?.let { msg ->
        IosAlert(
            title = "Import nicht möglich",
            message = msg,
            buttons = listOf(AlertButton("OK", bold = true) { error = null }),
            onDismiss = { error = null },
        )
    }
    result?.let { r ->
        val text = buildString {
            append("${r.pages} Seite(n), ${r.cols} × ${r.rows} Raster\n")
            append("${r.apps} Apps, ${r.folders} Ordner, ${r.widgets.size} Widgets")
            if (r.missing > 0) append("\n${r.missing} Apps sind auf diesem Handy nicht installiert")
            if (r.skipped > 0) append("\n${r.skipped} Xiaomi-eigene Elemente können nicht übernommen werden")
            append("\n\nDein aktuelles Layout wird ersetzt.")
            if (r.widgets.isNotEmpty()) append(" Für die Widgets fragt Android eventuell kurz nach Erlaubnis.")
        }
        IosAlert(
            title = "Layout gefunden",
            message = text,
            buttons = listOf(
                AlertButton("Übernehmen", bold = true) {
                    com.cleanlauncher.data.MiuiImport.apply(ctx, r)
                    result = null
                    android.widget.Toast.makeText(ctx, "Layout übernommen", android.widget.Toast.LENGTH_SHORT).show()
                    ctx.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_MAIN)
                            .addCategory(android.content.Intent.CATEGORY_HOME)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                AlertButton("Abbrechen") { result = null },
            ),
            onDismiss = { result = null },
        )
    }
}

// ======================= Wischgesten (Xiaomi) =======================

private const val FSG_KEY = "force_fsg_nav_bar"
private const val ADB_COMMANDS =
    "adb shell pm grant com.cleanlauncher android.permission.WRITE_SECURE_SETTINGS\n" +
        "adb shell settings put global force_fsg_nav_bar 1"

@Composable
private fun GestureNavSection() {
    val ctx = LocalContext.current
    val tick = resumeTick()
    val canWrite = remember(tick) {
        ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    var forced by remember(tick) {
        mutableStateOf(runCatching { android.provider.Settings.Global.getInt(ctx.contentResolver, FSG_KEY, 0) == 1 }.getOrDefault(false))
    }
    val isXiaomi = remember {
        android.os.Build.MANUFACTURER.equals("Xiaomi", true) || android.os.Build.BRAND.equals("Redmi", true) || android.os.Build.BRAND.equals("POCO", true)
    }
    if (!isXiaomi && !forced && !canWrite) return

    Section("Original Xiaomi-Gesten (optional, per PC)")
    Group {
        if (canWrite) {
            SwitchRow("Xiaomi-Wischgesten mit diesem Launcher", forced, "Danach in den Systemeinstellungen „Vollbild-Gesten“ wählen") {
                runCatching { android.provider.Settings.Global.putInt(ctx.contentResolver, FSG_KEY, if (it) 1 else 0) }
                forced = it
            }
        } else {
            ActionRow(
                if (forced) "✓ Wischgesten sind freigeschaltet" else "Einmalig per PC freischalten",
                if (forced) "Wähle jetzt in den Systemeinstellungen „Vollbild-Gesten“" else "Tippen kopiert die zwei Befehle",
                highlight = !forced,
            ) {
                val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("ADB", ADB_COMMANDS))
                android.widget.Toast.makeText(ctx, "Befehle kopiert", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        Divider()
        ActionRow("Systemnavigation öffnen", "Einstellungen → Startbildschirm → Systemnavigation → Gesten") {
            openSafe(
                ctx,
                android.content.Intent().setClassName("com.android.settings", "com.android.settings.SubSettings")
                    .putExtra(":settings:show_fragment", "com.android.settings.FullScreenDisplaySettings"),
                android.content.Intent(android.provider.Settings.ACTION_SETTINGS),
            )
        }
    }
    if (!canWrite && !forced) {
        Footer(
            "Xiaomi schaltet die Wischgesten ab, sobald ein anderer Launcher aktiv ist. Mit einem kleinen Trick geht es trotzdem: " +
                "Entwickleroptionen → USB-Debugging an, Handy per Kabel an den PC, und dort die zwei kopierten Befehle ausführen " +
                "(Programm „adb“ / Android Platform-Tools). Das musst du nur einmal machen. Danach unter Systemnavigation „Gesten“ wählen.",
        )
    }
}


// ======================= iOS überall (Bedienungshilfe) =======================

@Composable
private fun SystemWideSection(prefs: Prefs) {
    val ctx = LocalContext.current
    val tick = resumeTick()
    val active = remember(tick) { SystemActions.accessibilityEnabled }
    val canWrite = remember(tick) { android.provider.Settings.System.canWrite(ctx) }
    val dndAccess = remember(tick) {
        ctx.getSystemService(android.app.NotificationManager::class.java).isNotificationPolicyAccessGranted
    }
    val notifAccess = remember(tick) { com.cleanlauncher.system.NotificationRepo.hasAccess(ctx) }

    Section("iOS überall (in allen Apps)")
    Group {
        ActionRow(
            if (active) "✓ Bedienungshilfe aktiv" else "① Bedienungshilfe einschalten",
            if (active) "Gestenleiste, Kontrollzentrum & Dynamic Island können laufen"
            else "Installierte Apps → „Clean Launcher – Gesten“ → An",
            highlight = !active,
        ) { SystemActions.openAccessibilitySettings(ctx) }
        if (!active) {
            Divider()
            ActionRow(
                "Schalter grau / „Eingeschränkte Einstellung“?",
                "App-Info → oben rechts ⋮ → „Eingeschränkte Einstellungen zulassen“",
            ) { openSafe(ctx, appDetails(ctx)) }
        }
        Divider()
        SwitchRow("Gestenleiste statt 3 Tasten", prefs.gestureBar, "Hochwischen = Home, hochwischen & halten = Übersicht") { prefs.gestureBar = it }
        if (prefs.gestureBar) {
            Divider()
            SwitchRow("Vom Rand wischen = Zurück", prefs.gestureBack, "Links oder rechts vom Bildschirmrand nach innen wischen") { prefs.gestureBack = it }
            Divider()
            SwitchRow("Seitlich über die Leiste = letzte App", prefs.gestureSwitchApps) { prefs.gestureSwitchApps = it }
            Divider()
            SwitchRow("Dunkle Leiste", prefs.gestureBarDark, "Aus = helle Leiste mit dunklem Strich") { prefs.gestureBarDark = it }
        }
        Divider()
        SwitchRow(
            "Kontrollzentrum & Mitteilungen",
            prefs.iosShade,
            "Von oben runterwischen: links = Mitteilungen, rechts = Kontrollzentrum",
        ) { prefs.iosShade = it }
        Divider()
        SwitchRow("Dynamic Island", prefs.dynamicIsland, "Immer sichtbar – zeigt Musik, Laden und neue Mitteilungen") { prefs.dynamicIsland = it }
        if (prefs.dynamicIsland) {
            Divider()
            IntSlider("Island-Breite", prefs.islandWidth, 60..200, suffix = " dp") { prefs.islandWidth = it }
            Divider()
            IntSlider("Island-Position (runter)", prefs.islandOffsetY, -10..40, suffix = " dp") { prefs.islandOffsetY = it }
        }
    }
    if (prefs.iosShade || prefs.dynamicIsland || prefs.gestureBar) {
        Section("Berechtigungen fürs Kontrollzentrum")
        Group {
            ActionRow(
                if (notifAccess) "✓ Mitteilungszugriff" else "Mitteilungszugriff erlauben",
                "Mitteilungen, Musik-Steuerung und Dynamic Island",
                highlight = !notifAccess,
            ) { openSafe(ctx, android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            Divider()
            ActionRow(
                if (canWrite) "✓ Helligkeit & Drehsperre" else "Helligkeit & Drehsperre erlauben",
                "„Systemeinstellungen ändern“",
            ) {
                openSafe(
                    ctx,
                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName)),
                    appDetails(ctx),
                )
            }
            Divider()
            ActionRow(if (dndAccess) "✓ Nicht stören" else "„Nicht stören“ erlauben", "Zugriff auf „Bitte nicht stören“") {
                openSafe(ctx, android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        }
        Footer("Die Gestenleiste liegt über den 3 Tasten und versteckt sie. Wenn du sie wieder loswerden willst: hier ausschalten oder die Bedienungshilfe deaktivieren.")
    }
}
