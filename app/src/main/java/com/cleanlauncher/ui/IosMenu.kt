package com.cleanlauncher.ui

import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.offset
import com.cleanlauncher.data.AppRepository
import com.cleanlauncher.data.Container
import com.cleanlauncher.data.ItemType
import com.cleanlauncher.widgets.Widgets

data class MenuEntry(
    val label: String,
    val icon: ImageVector? = null,
    val image: ImageBitmap? = null,
    val destructive: Boolean = false,
    val groupStart: Boolean = false,
    val onClick: () -> Unit,
)

private fun entriesFor(c: LauncherController, t: MenuTarget, ctx: android.content.Context): List<MenuEntry> {
    val list = mutableListOf<MenuEntry>()
    fun close() {
        c.menu = null
    }
    when (t) {
        is MenuTarget.App -> {
            val app = t.app
            AppRepository.shortcuts(app).forEach { s ->
                list += MenuEntry((s.shortLabel ?: s.longLabel ?: "").toString(), image = AppRepository.shortcutIcon(s)) {
                    close()
                    AppRepository.startShortcut(ctx, s)
                    c.closeDrawer()
                }
            }
            var first = true
            fun add(e: MenuEntry) {
                list += if (first) e.copy(groupStart = list.isNotEmpty()) else e
                first = false
            }
            if (!t.fromLibrary) {
                add(MenuEntry("Home-Bildschirm bearbeiten", Icons.Filled.Edit) { close(); c.enterEditMode() })
            }
            add(MenuEntry("Umbenennen", Icons.Filled.Create) {
                close()
                c.rename = RenameTarget.App(app.key, c.labelOf(app), app.originalLabel)
            })
            if (t.folderId != null) {
                add(MenuEntry("Aus Ordner nehmen", Icons.Filled.Close) { close(); c.takeOutOfFolder(t.folderId, app.key) })
            } else if (t.item?.container == Container.DOCK) {
                add(MenuEntry("Aus dem Dock nehmen", Icons.Filled.Close) { close(); c.removeFromDock(t.item!!) })
            } else if (t.item != null && c.prefs.dockEnabled) {
                add(MenuEntry("Ins Dock legen", Icons.Filled.Add) { close(); c.addToDock(app.key) })
            }
            if (t.fromLibrary && !c.isOnHome(app.key)) {
                add(MenuEntry("Zum Home-Bildschirm", Icons.Filled.Home) { close(); c.addToHome(app.key) })
            }
            add(MenuEntry("App-Info", Icons.Filled.Info) { close(); AppRepository.openAppInfo(app) })
            list += MenuEntry("App entfernen", Icons.Filled.Delete, destructive = true, groupStart = true) {
                close()
                c.removeApp = t
            }
        }
        is MenuTarget.Folder -> {
            list += MenuEntry("Umbenennen", Icons.Filled.Create) {
                close()
                c.rename = RenameTarget.Folder(t.item.id, t.item.title)
            }
            list += MenuEntry("Home-Bildschirm bearbeiten", Icons.Filled.Edit) { close(); c.enterEditMode() }
            list += MenuEntry("Ordner entfernen", Icons.Filled.Delete, destructive = true, groupStart = true) {
                close()
                c.dissolveFolder(t.item)
            }
        }
        is MenuTarget.Widget -> {
            val info: AppWidgetProviderInfo? = if (t.item.type == ItemType.WIDGET) Widgets.info(t.item.widgetId) else null
            val reconfigurable = info != null && (
                info.configure != null ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                        (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0)
                )
            if (reconfigurable) {
                list += MenuEntry("Widget bearbeiten", Icons.Filled.Settings) {
                    close()
                    c.requestReconfigure?.invoke(t.item.widgetId)
                }
            }
            list += MenuEntry("Größe ändern", Icons.Filled.Build) { close(); c.resizeWidget = t.item }
            list += MenuEntry("Home-Bildschirm bearbeiten", Icons.Filled.Edit) { close(); c.enterEditMode() }
            list += MenuEntry("Widget entfernen", Icons.Filled.Delete, destructive = true, groupStart = true) {
                close()
                c.removeWidget(t.item)
            }
        }
    }
    return list
}

/** iOS-Kontextmenü: Hintergrund verschwimmt, Symbol bleibt sichtbar, Menü darunter/darüber. */
@Composable
fun ContextMenuOverlay(c: LauncherController) {
    val m = c.menu ?: return
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val entries = remember(m) { entriesFor(c, m.target, ctx) }
    val anim = remember(m) { Animatable(0f) }
    LaunchedEffect(m) { anim.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 500f)) }
    val density = LocalDensity.current
    val prefs = c.prefs

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(m) { detectTapGestures { c.menu = null } },
    ) {
        BlurredBackdrop(0.25f, Modifier.graphicsLayer { alpha = anim.value.coerceIn(0f, 1f) })

        // angehobenes Symbol
        val iconPx = with(density) { prefs.iconSize.dp.roundToPx() }
        val a = m.anchor
        when (val t = m.target) {
            is MenuTarget.App -> Box(
                Modifier
                    .offset { IntOffset(a.centerX() - iconPx / 2, a.top) }
                    .graphicsLayer { scaleX = 1f + 0.06f * anim.value; scaleY = scaleX },
            ) { IconImage(t.app, prefs) }
            is MenuTarget.Folder -> Box(
                Modifier
                    .offset { IntOffset(a.centerX() - iconPx / 2, a.top) }
                    .graphicsLayer { scaleX = 1f + 0.06f * anim.value; scaleY = scaleX },
            ) { FolderIcon(c.folderApps(t.item), prefs) }
            is MenuTarget.Widget -> Unit
        }

        val margin = with(density) { 12.dp.roundToPx() }
        val gap = with(density) { 10.dp.roundToPx() }
        val iconBottom = if (m.target is MenuTarget.Widget) a.bottom else a.top + iconPx
        Layout(
            content = {
                MenuPanel(entries, dark, Modifier.graphicsLayer {
                    alpha = anim.value.coerceIn(0f, 1f)
                    scaleX = 0.85f + 0.15f * anim.value
                    scaleY = scaleX
                })
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val p = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val below = iconBottom + gap
                val y = if (below + p.height < constraints.maxHeight - margin) below
                else (a.top - gap - p.height).coerceAtLeast(margin)
                val x = (a.centerX() - p.width / 2).coerceIn(margin, (constraints.maxWidth - p.width - margin).coerceAtLeast(margin))
                p.place(x, y)
            }
        }
    }
}

@Composable
private fun MenuPanel(entries: List<MenuEntry>, dark: Boolean, modifier: Modifier) {
    val text = if (dark) Color.White else Color.Black
    val sep = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.1f)
    val groupSep = if (dark) Color.Black.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.08f)
    Column(
        modifier
            .width(254.dp)
            .frosted(
                RoundedCornerShape(14.dp),
                if (dark) Color(0xCC2C2C2E) else Color(0xD9F2F2F2),
                if (dark) Color(0xF22C2C2E) else Color(0xF5F2F2F2),
            ),
    ) {
        entries.forEachIndexed { i, e ->
            if (i > 0) {
                if (e.groupStart) Box(Modifier.fillMaxWidth().height(8.dp).background(groupSep))
                else HorizontalDivider(thickness = 0.5.dp, color = sep)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = e.onClick)
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    e.label,
                    color = if (e.destructive) Ios.red else text,
                    fontSize = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (e.image != null) {
                    Image(e.image, null, Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)))
                } else if (e.icon != null) {
                    Icon(e.icon, null, tint = if (e.destructive) Ios.red else text, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ======================= iOS-Dialoge =======================

class AlertButton(val label: String, val bold: Boolean = false, val destructive: Boolean = false, val onClick: () -> Unit)

@Composable
fun IosAlert(
    title: String,
    message: String?,
    buttons: List<AlertButton>,
    onDismiss: () -> Unit,
    content: (@Composable () -> Unit)? = null,
) {
    val dark = isSystemInDarkTheme()
    val bg = if (dark) Color(0xFF2C2C2E) else Color(0xFFF2F2F2)
    val text = if (dark) Color.White else Color.Black
    val sep = if (dark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f)
    val blue = if (dark) Ios.blue else Color(0xFF007AFF)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, color = text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                if (message != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(message, color = text, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                if (content != null) {
                    Spacer(Modifier.height(12.dp))
                    content()
                }
            }
            val sideBySide = buttons.size == 2 && buttons.all { it.label.length <= 12 }
            HorizontalDivider(thickness = 0.5.dp, color = sep)
            if (sideBySide) {
                Row(Modifier.fillMaxWidth().height(44.dp)) {
                    buttons.forEachIndexed { i, b ->
                        if (i > 0) Box(Modifier.width(0.5.dp).fillMaxHeight().background(sep))
                        AlertButtonView(b, blue, Modifier.weight(1f))
                    }
                }
            } else {
                buttons.forEachIndexed { i, b ->
                    if (i > 0) HorizontalDivider(thickness = 0.5.dp, color = sep)
                    AlertButtonView(b, blue, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun AlertButtonView(b: AlertButton, blue: Color, modifier: Modifier) {
    Box(
        modifier
            .height(44.dp)
            .clickable(onClick = b.onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            b.label,
            color = if (b.destructive) Ios.red else blue,
            fontSize = 17.sp,
            fontWeight = if (b.bold) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
fun RemoveAppAlert(c: LauncherController, t: MenuTarget.App) {
    val ctx = LocalContext.current
    val name = c.labelOf(t.app)
    val buttons = mutableListOf(
        AlertButton("App löschen", destructive = true) {
            c.removeApp = null
            AppRepository.uninstall(ctx, t.app)
        },
    )
    if (c.isOnHome(t.app.key)) {
        buttons += AlertButton("Vom Home-Bildschirm entfernen") {
            c.removeApp = null
            c.removeFromHome(t.app.key)
        }
    }
    buttons += AlertButton("Ausblenden") {
        c.removeApp = null
        c.hide(t.app.key)
    }
    buttons += AlertButton("Abbrechen", bold = true) { c.removeApp = null }
    IosAlert(
        title = "„$name“ entfernen?",
        message = "Vom Home-Bildschirm entfernte Apps bleiben in der App-Mediathek.",
        buttons = buttons,
        onDismiss = { c.removeApp = null },
    )
}

@Composable
fun RenameAlert(c: LauncherController, t: RenameTarget) {
    val dark = isSystemInDarkTheme()
    var value by remember(t) {
        mutableStateOf(
            when (t) {
                is RenameTarget.App -> t.current
                is RenameTarget.Folder -> t.current
            },
        )
    }
    val buttons = mutableListOf<AlertButton>()
    if (t is RenameTarget.App) {
        buttons += AlertButton("Originalname") {
            c.applyRename(t, null)
            c.rename = null
        }
    }
    buttons += AlertButton("Abbrechen") { c.rename = null }
    buttons += AlertButton("Sichern", bold = true) {
        c.applyRename(t, value)
        c.rename = null
    }
    IosAlert(
        title = if (t is RenameTarget.Folder) "Ordner umbenennen" else "App umbenennen",
        message = null,
        buttons = if (t is RenameTarget.App) buttons else buttons.toList(),
        onDismiss = { c.rename = null },
    ) {
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            textStyle = TextStyle(color = if (dark) Color.White else Color.Black, fontSize = 15.sp),
            cursorBrush = SolidColor(Ios.blue),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (dark) Color(0xFF1C1C1E) else Color.White)
                .padding(horizontal = 10.dp, vertical = 9.dp),
        )
    }
}

@Composable
fun ResizeAlert(c: LauncherController) {
    val item = c.resizeWidget ?: return
    var sx by remember(item.id) { mutableIntStateOf(item.spanX) }
    var sy by remember(item.id) { mutableIntStateOf(item.spanY) }
    val dark = isSystemInDarkTheme()
    val text = if (dark) Color.White else Color.Black
    IosAlert(
        title = "Widget-Größe",
        message = "In Zellen des Rasters",
        buttons = listOf(
            AlertButton("Abbrechen") { c.resizeWidget = null },
            AlertButton("Fertig", bold = true) {
                if (c.resize(item, sx, sy)) c.resizeWidget = null else c.toast("Dafür ist dort kein Platz frei")
            },
        ),
        onDismiss = { c.resizeWidget = null },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Stepper("Breite", sx, 1..c.prefs.columns, text) { sx = it }
            Stepper("Höhe", sy, 1..c.prefs.rows, text) { sy = it }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, text: Color, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        StepButton("−") { if (value > range.first) onChange(value - 1) }
        Text("$value", color = text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
        StepButton("+") { if (value < range.last) onChange(value + 1) }
    }
}

@Composable
private fun StepButton(t: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Ios.gray.copy(alpha = 0.25f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, fontSize = 20.sp, color = Ios.blue) }
}
