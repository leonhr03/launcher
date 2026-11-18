package com.cleanlauncher.ui

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cleanlauncher.data.AppInfo
import com.cleanlauncher.data.AppRepository
import com.cleanlauncher.data.Container
import com.cleanlauncher.data.Item
import com.cleanlauncher.data.ItemType
import com.cleanlauncher.system.NotificationRepo
import com.cleanlauncher.widgets.LauncherWidgetView
import com.cleanlauncher.widgets.Widgets
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LauncherRoot(activity: Activity, c: LauncherController, homePress: Int) {
    val prefs = c.prefs
    c.view = LocalView.current

    val apps = AppRepository.apps
    val byKey = remember(apps) { apps.associateBy { it.key } }
    c.byKey = byKey
    LaunchedEffect(apps) { c.syncApps(apps) }
    LaunchedEffect(prefs.columns, prefs.rows, prefs.hiddenApps) {
        if (c.ws.hasLayout && apps.isNotEmpty()) c.ws.normalize(prefs.columns, prefs.rows, c::visible)
    }

    val items = c.ws.items
    val desktop = items.filter { it.container == Container.DESKTOP && c.visible(it) }
    val dock = items.filter { it.container == Container.DOCK && c.visible(it) }.sortedBy { it.x }
    val wsPages = (items.filter { it.container == Container.DESKTOP }.maxOfOrNull { it.page } ?: 0) + 1
    val pageCount = wsPages + if (c.editMode) 1 else 0
    val pager = rememberPagerState { pageCount }
    val scope = rememberCoroutineScope()
    c.pager = pager
    c.scope = scope

    // ----- Fenster -----
    val view = LocalView.current
    SideEffect {
        val ctrl = WindowCompat.getInsetsController(activity.window, view)
        if (prefs.hideStatusBar || c.editMode) {
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            ctrl.show(WindowInsetsCompat.Type.statusBars())
        }
        val light = prefs.darkStatusIcons && !c.drawerOpen && c.openFolderId == null && c.menu == null
        ctrl.isAppearanceLightStatusBars = light
        ctrl.isAppearanceLightNavigationBars = light
        activity.requestedOrientation =
            if (prefs.lockPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    LaunchedEffect(c.drawerOpen, prefs.drawerBlur) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val w = activity.window
            try {
                if (c.drawerOpen && prefs.drawerBlur && com.cleanlauncher.data.WallpaperRepo.blurred == null) {
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    w.attributes = w.attributes.apply { blurBehindRadius = 90 }
                } else {
                    w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            } catch (_: Exception) {
            }
        }
    }

    // ----- Home-Taste & Zurück -----
    LaunchedEffect(homePress) {
        if (homePress == 0) return@LaunchedEffect
        when {
            c.menu != null -> c.menu = null
            c.widgetPicker -> c.widgetPicker = false
            c.openCategory != null -> c.openCategory = null
            c.openFolderId != null -> c.openFolderId = null
            c.drawerOpen -> c.closeDrawer()
            c.editMode -> c.exitEditMode()
            pager.currentPage != 0 -> pager.animateScrollToPage(0)
        }
    }
    BackHandler(enabled = true) {
        when {
            c.menu != null -> c.menu = null
            c.widgetPicker -> c.widgetPicker = false
            c.openFolderId != null -> c.openFolderId = null
            c.editMode -> c.exitEditMode()
            pager.currentPage != 0 -> scope.launch { pager.animateScrollToPage(0) }
        }
    }

    LauncherTheme(prefs, dark = true) {
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
                            onTap = { if (c.editMode) c.exitEditMode() },
                            onDoubleTap = { c.perform(prefs.doubleTap) },
                            onLongPress = { c.perform(prefs.longPress) },
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
                        .then(
                            if (prefs.hideStatusBar) Modifier.statusBarsPadding()
                            else Modifier.windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
                        )
                        .navigationBarsPadding(),
                ) {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        beyondViewportPageCount = if (c.drag != null) pageCount else 1,
                        key = { it },
                    ) { page ->
                        Page(c, page, desktop.filter { it.page == page })
                    }
                    SearchPillOrDots(c, pageCount)
                    if (prefs.dockEnabled) Dock(c, dock)
                    Spacer(Modifier.height(prefs.dockBottom.dp))
                }
                if (c.editMode) EditBar(c, Modifier.align(Alignment.TopCenter).padding(top = 2.dp))
            }

            DragLayer(c)

            // ===================== Overlays =====================
            AnimatedVisibility(
                visible = c.drawerOpen,
                enter = fadeIn(tween(200)) + scaleIn(tween(240), initialScale = 0.94f),
                exit = fadeOut(tween(180)) + scaleOut(tween(200), targetScale = 0.94f),
            ) {
                AppLibrary(c, apps)
            }
            FolderOverlay(c)
            AnimatedVisibility(visible = c.widgetPicker, enter = fadeIn(), exit = fadeOut()) {
                WidgetPicker(c)
            }
            ContextMenuOverlay(c)

            c.removeApp?.let { RemoveAppAlert(c, it) }
            c.rename?.let { RenameAlert(c, it) }
            ResizeAlert(c)
            c.confirmRemove?.let { item ->
                IosAlert(
                    title = if (item.type == ItemType.FOLDER) "Ordner „${item.title}“ entfernen?" else "Widget entfernen?",
                    message = if (item.type == ItemType.FOLDER) "Die Apps bleiben in der App-Mediathek." else null,
                    buttons = listOf(
                        AlertButton("Entfernen", destructive = true) {
                            c.confirmRemove = null
                            if (item.type == ItemType.FOLDER) c.dissolveFolder(item) else c.removeWidget(item)
                        },
                        AlertButton("Abbrechen", bold = true) { c.confirmRemove = null },
                    ),
                    onDismiss = { c.confirmRemove = null },
                )
            }
        }
    }
}

// ======================= Gesten für Symbole =======================

@Composable
fun Modifier.iconGestures(
    c: LauncherController,
    key: Any,
    bounds: Array<Rect?>,
    pressed: MutableState<Boolean>,
    dragItem: Item?,
    source: DragSource,
    folderId: Long?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
): Modifier {
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLongPress)
    val dItem by rememberUpdatedState(dragItem)
    val edit = c.editMode
    return this
        .onGloballyPositioned {
            val b = it.boundsInWindow()
            bounds[0] = Rect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
        }
        .pointerInput(key) {
            detectTapGestures(
                onPress = {
                    c.suppressTap = false
                    pressed.value = true
                    tryAwaitRelease()
                    pressed.value = false
                },
                onTap = { if (!c.suppressTap) tap() },
            )
        }
        .pointerInput(key, edit, dragItem != null) {
            if (edit && dItem != null) {
                detectDragGestures(
                    onDragStart = { o -> dItem?.let { c.beginDrag(it, source, folderId, bounds[0], o) } },
                    onDragEnd = { c.endDrag() },
                    onDragCancel = { c.cancelDrag() },
                    onDrag = { ch, d ->
                        ch.consume()
                        c.dragBy(d)
                    },
                )
            } else {
                detectDragGesturesAfterLongPress(
                    onDragStart = { o ->
                        c.suppressTap = true
                        pressed.value = false
                        long()
                        dItem?.let { c.pendingDrag(it, source, folderId, bounds[0], o) }
                    },
                    onDragEnd = { c.endDrag() },
                    onDragCancel = { c.cancelDrag() },
                    onDrag = { ch, d ->
                        ch.consume()
                        c.dragBy(d)
                    },
                )
            }
        }
}

// ======================= Seiten & Elemente =======================

@Composable
private fun Page(c: LauncherController, page: Int, items: List<Item>) {
    val prefs = c.prefs
    val cols = prefs.columns
    val rows = prefs.rows
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(horizontal = prefs.gridPaddingH.dp)
            .padding(top = prefs.gridPaddingTop.dp)
            .onGloballyPositioned { if (page == c.pager?.currentPage) c.gridRect = it.boundsInWindow() },
    ) {
        val cw = maxWidth / cols
        val ch = maxHeight / rows
        items.forEach { item ->
            key(item.id) {
                Box(
                    Modifier
                        .offset(cw * item.x, ch * item.y)
                        .size(cw * item.spanX.coerceAtMost(cols), ch * item.spanY.coerceAtMost(rows)),
                    contentAlignment = Alignment.Center,
                ) {
                    ItemView(c, item, cw, ch, DragSource.DESKTOP)
                }
            }
        }
    }
}

@Composable
private fun ItemView(c: LauncherController, item: Item, cw: Dp, ch: Dp, source: DragSource) {
    when (item.type) {
        ItemType.APP -> c.byKey[item.appKey]?.let { AppItemCell(c, item, it, source, c.prefs.showLabels) }
        ItemType.FOLDER -> FolderCell(c, item, source, c.prefs.showLabels)
        ItemType.BUILTIN, ItemType.WIDGET -> WidgetCell(c, item, cw, ch)
    }
}

@Composable
fun AppItemCell(c: LauncherController, item: Item, app: AppInfo, source: DragSource, showLabel: Boolean) {
    val bounds = remember { arrayOfNulls<Rect>(1) }
    val pressed = remember { mutableStateOf(false) }
    val d = c.drag
    val dragging = d != null && d.active && d.item.id == item.id && d.source != DragSource.FOLDER
    IconCell(
        prefs = c.prefs,
        label = c.labelOf(app),
        showLabel = showLabel,
        editMode = c.editMode,
        pressed = pressed.value,
        hidden = dragging,
        wobbleSeed = item.id.toInt(),
        badge = NotificationRepo.counts[app.packageName] ?: 0,
        onRemove = { c.removeApp = MenuTarget.App(app, item, null, false) },
        modifier = Modifier.iconGestures(
            c, item.id, bounds, pressed, item, source, null,
            onTap = { if (!c.editMode) c.launch(app, bounds[0]) },
            onLongPress = { c.showMenu(MenuTarget.App(app, item, null, false), bounds[0]) },
        ),
    ) { IconImage(app, c.prefs) }
}

@Composable
private fun FolderCell(c: LauncherController, item: Item, source: DragSource, showLabel: Boolean) {
    val bounds = remember { arrayOfNulls<Rect>(1) }
    val pressed = remember { mutableStateOf(false) }
    val apps = c.folderApps(item)
    val d = c.drag
    val dragging = d != null && d.active && d.item.id == item.id
    IconCell(
        prefs = c.prefs,
        label = item.title,
        showLabel = showLabel,
        editMode = c.editMode,
        pressed = pressed.value,
        hidden = dragging,
        wobbleSeed = item.id.toInt(),
        badge = apps.sumOf { NotificationRepo.counts[it.packageName] ?: 0 },
        onRemove = { c.confirmRemove = item },
        modifier = Modifier.iconGestures(
            c, item.id, bounds, pressed, item, source, null,
            onTap = { c.openFolderId = item.id },
            onLongPress = { c.showMenu(MenuTarget.Folder(item), bounds[0]) },
        ),
    ) { FolderIcon(apps, c.prefs) }
}

@Composable
private fun WidgetCell(c: LauncherController, item: Item, cw: Dp, ch: Dp) {
    val prefs = c.prefs
    val bounds = remember { arrayOfNulls<Rect>(1) }
    val pressed = remember { mutableStateOf(false) }
    val d = c.drag
    val dragging = d != null && d.active && d.item.id == item.id
    // Ränder so, dass das Widget mit den App-Symbolen fluchtet (wie bei iOS)
    val labelH = if (prefs.showLabels) (prefs.labelSize + 9).dp else 0.dp
    val padH = ((cw - prefs.iconSize.dp) / 2).coerceAtLeast(4.dp)
    val padV = ((ch - prefs.iconSize.dp - labelH) / 2).coerceAtLeast(4.dp)
    val rotation = if (c.editMode) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "wobble")
        val r by t.animateFloat(
            -0.8f, 0.8f,
            androidx.compose.animation.core.infiniteRepeatable(tween(140), androidx.compose.animation.core.RepeatMode.Reverse),
            label = "w",
        )
        r
    } else 0f
    val pressScale by animateFloatAsState(if (pressed.value && !c.editMode) 0.96f else 1f, tween(110), label = "p")

    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = padH, vertical = padV)
            .graphicsLayer {
                alpha = if (dragging) 0f else 1f
                scaleX = pressScale
                scaleY = pressScale
            }
            .rotate(rotation),
    ) {
        val gestureMod = Modifier
            .fillMaxSize()
            .iconGestures(
                c, item.id, bounds, pressed, item, DragSource.DESKTOP, null,
                onTap = {},
                onLongPress = { c.showMenu(MenuTarget.Widget(item), bounds[0]) },
            )
        if (item.type == ItemType.BUILTIN) {
            item.builtinType?.let { BuiltinWidgetView(it, prefs, gestureMod) }
        } else {
            AndroidWidget(c, item, Modifier.fillMaxSize(), onLongPress = { c.showMenu(MenuTarget.Widget(item), bounds[0]) })
            if (c.editMode) Box(gestureMod)
            else Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        val b = it.boundsInWindow()
                        bounds[0] = Rect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
                    },
            )
        }
        if (c.editMode) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset((-8).dp, (-8).dp)
                    .size(24.dp)
                    .frosted(CircleShape, Color(0x99555555), Color(0xE6555555), prefs.glass)
                    .clickable { c.confirmRemove = item },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(10.dp, 2.dp).background(Color.White)) }
        }
    }
}

@Composable
private fun AndroidWidget(c: LauncherController, item: Item, modifier: Modifier, onLongPress: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = c.prefs
    val info = remember(item.widgetId) { if (Widgets.ready) Widgets.info(item.widgetId) else null }
    if (info == null) {
        WidgetCard(prefs, modifier) {
            Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
                Text("Widget nicht verfügbar", color = Ios.gray, fontSize = 12.sp)
            }
        }
        return
    }
    val holder = remember { arrayOfNulls<AppWidgetHostView>(1) }
    val density = LocalDensity.current
    val longPress by rememberUpdatedState(onLongPress)
    AndroidView(
        factory = {
            Widgets.host.createView(ctx, item.widgetId, info).also {
                it.setPadding(0, 0, 0, 0)
                holder[0] = it
            }
        },
        update = { v -> (v as? LauncherWidgetView)?.onLongPress = { longPress() } },
        modifier = modifier
            .clip(RoundedCornerShape(prefs.widgetCorner.dp))
            .onSizeChanged { s ->
                val v = holder[0] ?: return@onSizeChanged
                val w = with(density) { s.width.toDp().value }
                val h = with(density) { s.height.toDp().value }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        v.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h)))
                    } else {
                        @Suppress("DEPRECATION")
                        v.updateAppWidgetSize(null, w.toInt(), h.toInt(), w.toInt(), h.toInt())
                    }
                } catch (_: Exception) {
                }
            },
    )
}

// ======================= Dock, Suche, Bearbeiten =======================

@Composable
private fun Dock(c: LauncherController, dockItems: List<Item>) {
    val prefs = c.prefs
    var m = Modifier
        .fillMaxWidth()
        .padding(horizontal = prefs.dockMargin.dp)
        .onGloballyPositioned { c.dockRect = it.boundsInWindow() }
    if (prefs.dockBackground) {
        val tint = Color(prefs.dockColor).copy(alpha = prefs.dockOpacity)
        m = m.frosted(RoundedCornerShape(prefs.dockCorner.dp), tint, tint, prefs.glass)
    }
    Box(m.padding(vertical = 13.dp, horizontal = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dockItems.forEach { item ->
                key(item.id) {
                    when (item.type) {
                        ItemType.APP -> c.byKey[item.appKey]?.let {
                            AppItemCell(c, item, it, DragSource.DOCK, prefs.showLabels && prefs.dockShowLabels)
                        }
                        ItemType.FOLDER -> FolderCell(c, item, DragSource.DOCK, prefs.showLabels && prefs.dockShowLabels)
                        else -> Unit
                    }
                }
            }
            if (dockItems.isEmpty()) Spacer(Modifier.height(prefs.iconSize.dp))
        }
    }
}

@Composable
private fun SearchPillOrDots(c: LauncherController, pageCount: Int) {
    val prefs = c.prefs
    val pager = c.pager ?: return
    val showDots = !prefs.searchPill || pager.isScrollInProgress || c.editMode
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!showDots) {
            Row(
                Modifier
                    .frosted(RoundedCornerShape(50), Color.White.copy(alpha = 0.22f), Color.Black.copy(alpha = 0.28f), prefs.glass)
                    .clickable { c.openDrawer(true) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("Suchen", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        } else if (prefs.showPageIndicator && pageCount > 1) {
            val color = Color(prefs.pageIndicatorColor)
            Row(
                Modifier
                    .frosted(RoundedCornerShape(50), Color.White.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.2f), prefs.glass)
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pageCount) { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = if (i == pager.currentPage) 0.95f else 0.35f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditBar(c: LauncherController, modifier: Modifier) {
    val prefs = c.prefs
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .frosted(CircleShape, Color.Black.copy(alpha = 0.32f), Color.Black.copy(alpha = 0.45f), prefs.glass)
                .clickable { c.widgetPicker = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, "Widget hinzufügen", tint = Color.White, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(36.dp)
                .frosted(CircleShape, Color.Black.copy(alpha = 0.32f), Color.Black.copy(alpha = 0.45f), prefs.glass)
                .clickable { c.openSettings() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Settings, "Einstellungen", tint = Color.White, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.92f))
                .clickable { c.exitEditMode() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) { Text("Fertig", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    }
}

// ======================= Ziehen =======================

@Composable
private fun DragLayer(c: LauncherController) {
    val d = c.drag ?: return
    if (!d.active) return
    val density = LocalDensity.current
    val prefs = c.prefs
    val pv = c.preview(d)
    if (pv != null && pv.page == c.pager?.currentPage && c.gridRect.width > 0) {
        val cw = c.gridRect.width / prefs.columns
        val ch = c.gridRect.height / prefs.rows
        val inset = with(density) { 6.dp.toPx() }
        Box(
            Modifier
                .offset { IntOffset((c.gridRect.left + pv.x * cw + inset).toInt(), (c.gridRect.top + pv.y * ch + inset).toInt()) }
                .size(with(density) { (pv.sx * cw - 2 * inset).toDp() }, with(density) { (pv.sy * ch - 2 * inset).toDp() })
                .clip(RoundedCornerShape(22.dp))
                .background(if (pv.ok) Color.White.copy(alpha = 0.16f) else Ios.red.copy(alpha = 0.18f))
                .border(1.5.dp, if (pv.ok) Color.White.copy(alpha = 0.6f) else Ios.red.copy(alpha = 0.6f), RoundedCornerShape(22.dp)),
        )
    }
    Box(
        Modifier
            .offset { IntOffset((d.pointer.x - d.grab.x).roundToInt(), (d.pointer.y - d.grab.y).roundToInt()) }
            .size(with(density) { d.size.width.toDp() }, with(density) { d.size.height.toDp() })
            .graphicsLayer {
                scaleX = 1.08f
                scaleY = 1.08f
                alpha = 0.95f
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        val item = d.item
        when (item.type) {
            ItemType.APP -> c.byKey[item.appKey]?.let { IconImage(it, prefs) }
            ItemType.FOLDER -> FolderIcon(c.folderApps(item), prefs)
            ItemType.BUILTIN -> item.builtinType?.let { BuiltinWidgetView(it, prefs, Modifier.fillMaxSize()) }
            ItemType.WIDGET -> WidgetCard(prefs, Modifier.fillMaxSize()) {}
        }
    }
}
