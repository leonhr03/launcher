package com.cleanlauncher.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cleanlauncher.data.AppInfo
import com.cleanlauncher.data.AppRepository
import com.cleanlauncher.data.Prefs
import com.cleanlauncher.system.SystemActions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

@Composable
fun LauncherRoot(activity: Activity, homePress: Int) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    val c = remember { LauncherController(activity, prefs) }
    c.view = LocalView.current

    val apps = AppRepository.apps
    LaunchedEffect(apps) { c.syncApps(apps) }
    val byKey = remember(apps) { apps.associateBy { it.key } }

    // ----- Sichtbare Listen berechnen -----
    val hidden = prefs.hiddenApps.toSet()
    val allDock = prefs.dockApps.filter { it in byKey && it !in hidden }.distinct()
    val dockKeys = if (prefs.dockEnabled) allDock.take(prefs.dockCount) else emptyList()
    val homeKeys = ((allDock - dockKeys.toSet()) + prefs.homeOrder)
        .filter { it in byKey && it !in hidden && it !in dockKeys }
        .distinct()
    c.homeKeys = homeKeys
    c.dockKeys = dockKeys

    val cols = prefs.columns
    val rows = prefs.rows
    val clockRows = if (prefs.clockWidget) 2.coerceAtMost(rows - 1) else 0
    val firstCap = cols * (rows - clockRows)
    val cap = cols * rows
    val pages: List<List<String>> = remember(homeKeys, firstCap, cap) {
        buildList {
            add(homeKeys.take(firstCap))
            var rest = homeKeys.drop(firstCap)
            while (rest.isNotEmpty()) {
                add(rest.take(cap))
                rest = rest.drop(cap)
            }
        }
    }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()

    // ----- Fenster: Statusleiste, Ausrichtung, Unschärfe -----
    val view = LocalView.current
    SideEffect {
        val ctrl = WindowCompat.getInsetsController(activity.window, view)
        if (prefs.hideStatusBar) {
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            ctrl.show(WindowInsetsCompat.Type.statusBars())
        }
        val light = prefs.darkStatusIcons && !c.drawerOpen
        ctrl.isAppearanceLightStatusBars = light
        ctrl.isAppearanceLightNavigationBars = light
        activity.requestedOrientation =
            if (prefs.lockPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    LaunchedEffect(c.drawerOpen, prefs.drawerBlur) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val w = activity.window
            try {
                if (c.drawerOpen && prefs.drawerBlur) {
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    w.attributes = w.attributes.apply { blurBehindRadius = 90 }
                } else {
                    w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            } catch (_: Exception) {
            }
        }
    }

    // ----- Home-Taste -----
    LaunchedEffect(homePress) {
        if (homePress == 0) return@LaunchedEffect
        when {
            c.renameApp != null -> c.renameApp = null
            c.menuKey != null -> c.menuKey = null
            c.drawerOpen -> c.closeDrawer()
            c.editMode -> c.exitEditMode()
            pager.currentPage != 0 -> pager.animateScrollToPage(0)
        }
    }
    // Zurück-Taste auf dem Startbildschirm: nichts tun (niemals den Launcher schließen)
    BackHandler(enabled = true) {
        when {
            c.editMode -> c.exitEditMode()
            pager.currentPage != 0 -> scope.launch { pager.animateScrollToPage(0) }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize()) {
            // ===================== Startbildschirm =====================
            val homeAlpha by animateFloatAsState(if (c.drawerOpen) 0f else 1f, tween(220), label = "home")
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = homeAlpha }
                    .background(Color.Black.copy(alpha = prefs.wallpaperDim))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { if (c.editMode) c.selectedKey = null },
                            onDoubleTap = { c.perform(prefs.doubleTap) },
                            onLongPress = {
                                if (System.currentTimeMillis() - lastIconLongPress > 600) {
                                    c.perform(prefs.longPress)
                                }
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                val th = 56.dp.toPx()
                                if (total < -th) c.perform(prefs.swipeUp)
                                else if (total > th) c.perform(prefs.swipeDown)
                            },
                            onVerticalDrag = { change, dy ->
                                total += dy
                                change.consume()
                            },
                        )
                    },
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    Spacer(Modifier.height(if (c.editMode) 52.dp else 0.dp))
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        beyondViewportPageCount = 1,
                    ) { page ->
                        PageGrid(
                            c = c,
                            keys = pages.getOrElse(page) { emptyList() },
                            byKey = byKey,
                            firstPage = page == 0,
                            clockRows = clockRows,
                            isLastPage = page == pages.lastIndex,
                        )
                    }
                    if (prefs.showPageIndicator && pages.size > 1) {
                        PageDots(pages.size, pager.currentPage, Color(prefs.pageIndicatorColor)) { p ->
                            scope.launch { pager.animateScrollToPage(p) }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                    }
                    if (prefs.dockEnabled) {
                        Dock(c, dockKeys, byKey)
                    }
                    Spacer(Modifier.height(6.dp))
                }

                if (c.editMode) {
                    EditBar(c, Modifier.align(Alignment.TopCenter).statusBarsPadding())
                }
            }

            // ===================== App-Mediathek / Suche =====================
            AnimatedVisibility(
                visible = c.drawerOpen,
                enter = slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(220)) { it / 3 } + fadeOut(tween(180)),
            ) {
                AppDrawer(c, apps)
            }

            c.renameApp?.let { app -> RenameDialog(c, app) }
        }
    }
}

/** Zeitpunkt des letzten Langdrucks auf ein Symbol (damit der Hintergrund-Langdruck nicht zusätzlich auslöst). */
internal var lastIconLongPress = 0L

@Composable
private fun PageGrid(
    c: LauncherController,
    keys: List<String>,
    byKey: Map<String, AppInfo>,
    firstPage: Boolean,
    clockRows: Int,
    isLastPage: Boolean,
) {
    val prefs = c.prefs
    val cols = prefs.columns
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = prefs.gridPaddingH.dp)
            .padding(top = prefs.gridPaddingTop.dp),
    ) {
        val rowCount = if (firstPage) prefs.rows - clockRows else prefs.rows
        if (firstPage && clockRows > 0) {
            ClockWidget(prefs, Modifier.fillMaxWidth().weight(clockRows.toFloat()))
        }
        for (r in 0 until rowCount) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (col in 0 until cols) {
                    val idx = r * cols + col
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val app = keys.getOrNull(idx)?.let { byKey[it] }
                        if (app != null) {
                            HomeAppIcon(c, app, IconLocation.HOME, prefs.showLabels)
                        } else if (isLastPage && idx == keys.size && c.editMode && c.selectedKey != null) {
                            EmptySlot(prefs.iconSize) { c.moveSelectedToHomeEnd() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySlot(iconSize: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(iconSize.dp)
            .clip(RoundedCornerShape(24))
            .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(24))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("+", color = Color.White, fontSize = (iconSize / 2.4f).sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun Dock(c: LauncherController, dockKeys: List<String>, byKey: Map<String, AppInfo>) {
    val prefs = c.prefs
    var m = Modifier
        .fillMaxWidth()
        .padding(horizontal = prefs.dockMargin.dp)
    if (prefs.dockBackground) {
        val shape = RoundedCornerShape(prefs.dockCorner.dp)
        m = m
            .clip(shape)
            .background(Color(prefs.dockColor).copy(alpha = prefs.dockOpacity))
    }
    Box(m.padding(vertical = 12.dp, horizontal = 6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dockKeys.forEach { key ->
                byKey[key]?.let { HomeAppIcon(c, it, IconLocation.DOCK, prefs.showLabels && prefs.dockShowLabels) }
            }
            val sel = c.selectedKey
            if (c.editMode && sel != null && sel !in dockKeys && dockKeys.size < prefs.dockCount) {
                EmptySlot(prefs.iconSize) { c.moveSelectedToDock() }
            }
            if (dockKeys.isEmpty() && !(c.editMode && sel != null)) {
                Spacer(Modifier.height(prefs.iconSize.dp))
            }
        }
    }
}

@Composable
private fun HomeAppIcon(c: LauncherController, app: AppInfo, location: IconLocation, showLabel: Boolean) {
    val menuId = "${location.name}:${app.key}"
    AppIcon(
        app = app,
        prefs = c.prefs,
        showLabel = showLabel,
        editMode = c.editMode,
        selected = c.editMode && c.selectedKey == app.key,
        wobbleSeed = app.key.hashCode(),
        menuOpen = c.menuKey == menuId,
        onDismissMenu = { c.menuKey = null },
        onClick = { bounds ->
            if (c.editMode) c.onEditTap(app.key) else c.launch(app, bounds)
        },
        onLongClick = {
            lastIconLongPress = System.currentTimeMillis()
            if (c.editMode) {
                c.onEditTap(app.key)
            } else {
                c.haptic(true)
                c.menuKey = menuId
            }
        },
        onRemoveBadge = if (location == IconLocation.HOME) ({ c.removeFromHome(app.key) })
        else ({ c.removeFromDock(app.key) }),
    ) {
        AppMenuItems(c, app, location)
    }
}

/** Kontextmenü für ein App-Symbol (Home, Dock oder App-Mediathek). */
@Composable
fun ColumnScope.AppMenuItems(c: LauncherController, app: AppInfo, location: IconLocation) {
    val ctx = LocalContext.current
    val shortcuts = remember(app.key) { AppRepository.shortcuts(app) }
    fun close() {
        c.menuKey = null
    }
    shortcuts.forEach { s ->
        DropdownMenuItem(
            text = { Text((s.shortLabel ?: s.longLabel ?: "").toString()) },
            onClick = {
                close()
                AppRepository.startShortcut(ctx, s)
                c.closeDrawer()
            },
        )
    }
    if (shortcuts.isNotEmpty()) HorizontalDivider()
    if (location != IconLocation.DRAWER) {
        DropdownMenuItem(text = { Text("Home-Bildschirm bearbeiten") }, onClick = { c.enterEditMode() })
    }
    DropdownMenuItem(text = { Text("Umbenennen") }, onClick = {
        close()
        c.renameApp = app
    })
    if (c.isInDock(app.key)) {
        DropdownMenuItem(text = { Text("Aus dem Dock entfernen") }, onClick = {
            close()
            c.removeFromDock(app.key)
        })
    } else {
        DropdownMenuItem(text = { Text("Zum Dock hinzufügen") }, onClick = {
            close()
            c.addToDock(app.key)
        })
    }
    if (c.isOnHome(app.key)) {
        DropdownMenuItem(text = { Text("Vom Home-Bildschirm entfernen") }, onClick = {
            close()
            c.removeFromHome(app.key)
        })
    } else if (!c.isInDock(app.key)) {
        DropdownMenuItem(text = { Text("Zum Home-Bildschirm hinzufügen") }, onClick = {
            close()
            c.addToHome(app.key)
        })
    }
    DropdownMenuItem(text = { Text("Ausblenden") }, onClick = {
        close()
        c.hide(app.key)
    })
    HorizontalDivider()
    DropdownMenuItem(text = { Text("App-Info") }, onClick = {
        close()
        AppRepository.openAppInfo(app)
    })
    DropdownMenuItem(text = { Text("Deinstallieren", color = Color(0xFFFF6B6B)) }, onClick = {
        close()
        AppRepository.uninstall(ctx, app)
    })
}

@Composable
private fun PageDots(count: Int, current: Int, color: Color, onClick: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(26.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (i == current) 0.95f else 0.35f))
                    .clickable { onClick(i) },
            )
        }
    }
}

@Composable
private fun EditBar(c: LauncherController, modifier: Modifier) {
    val ctx = LocalContext.current
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill("Hintergrund") { SystemActions.pickWallpaper(ctx) }
            Spacer(Modifier.size(8.dp))
            Pill("Einstellungen") { c.openSettings() }
            Spacer(Modifier.weight(1f))
            Pill("Fertig", strong = true) { c.exitEditMode() }
        }
        Text(
            if (c.selectedKey == null) "Tippe eine App an, dann eine zweite zum Tauschen"
            else "Jetzt Ziel antippen (App, freier Platz oder Dock)",
            color = Color.White,
            fontSize = 12.sp,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 1f), 6f)),
            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        )
    }
}

@Composable
private fun Pill(text: String, strong: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (strong) Color.White.copy(alpha = 0.95f) else Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            color = if (strong) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ClockWidget(prefs: Prefs, modifier: Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000 + 50)
        }
    }
    val locale = Locale.getDefault()
    val timePattern = if (prefs.clock24h) "HH:mm" else "h:mm"
    val datePattern = remember(locale) { DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM") }
    val color = Color(prefs.clockColor)
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            DateFormat.format(datePattern, Date(now)).toString(),
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            DateFormat.format(timePattern, Date(now)).toString(),
            color = color,
            fontSize = 78.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RenameDialog(c: LauncherController, app: AppInfo) {
    var text by remember(app.key) { mutableStateOf(c.prefs.labelFor(app.key, app.originalLabel)) }
    AlertDialog(
        onDismissRequest = { c.renameApp = null },
        title = { Text("App umbenennen") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = {
                c.rename(app.key, text.takeIf { it != app.originalLabel })
                c.renameApp = null
            }) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    c.rename(app.key, null)
                    c.renameApp = null
                }) { Text("Original") }
                TextButton(onClick = { c.renameApp = null }) { Text("Abbrechen") }
            }
        },
    )
}
