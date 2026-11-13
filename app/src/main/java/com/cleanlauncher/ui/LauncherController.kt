package com.cleanlauncher.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.cleanlauncher.SettingsActivity
import com.cleanlauncher.data.AppInfo
import com.cleanlauncher.data.AppRepository
import com.cleanlauncher.data.BuiltinWidget
import com.cleanlauncher.data.Container
import com.cleanlauncher.data.GestureAction
import com.cleanlauncher.data.Item
import com.cleanlauncher.data.ItemType
import com.cleanlauncher.data.Prefs
import com.cleanlauncher.data.Workspace
import com.cleanlauncher.system.SystemActions
import com.cleanlauncher.widgets.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import androidx.compose.ui.geometry.Rect as CRect

sealed interface MenuTarget {
    data class App(val app: AppInfo, val item: Item?, val folderId: Long?, val fromLibrary: Boolean) : MenuTarget
    data class Folder(val item: Item) : MenuTarget
    data class Widget(val item: Item) : MenuTarget
}

data class MenuState(val target: MenuTarget, val anchor: Rect)

enum class DragSource { DESKTOP, DOCK, FOLDER }

class DragState(
    val item: Item,
    val source: DragSource,
    val folderId: Long?,
    val grab: Offset,
    val size: IntSize,
    start: Offset,
) {
    var pointer by mutableStateOf(start)
    var active by mutableStateOf(false)
    var accum = Offset.Zero
}

sealed interface RenameTarget {
    data class App(val key: String, val current: String, val original: String) : RenameTarget
    data class Folder(val id: Long, val current: String) : RenameTarget
}

/** Hält den UI-Zustand des Startbildschirms und alle Aktionen. */
class LauncherController(val context: Context, val prefs: Prefs) {

    val ws = Workspace.get(context)

    var drawerOpen by mutableStateOf(false)
    var drawerFocus by mutableStateOf(false)
    var editMode by mutableStateOf(false)
    var menu by mutableStateOf<MenuState?>(null)
    var drag by mutableStateOf<DragState?>(null)
    var rename by mutableStateOf<RenameTarget?>(null)
    var removeApp by mutableStateOf<MenuTarget.App?>(null)
    var widgetPicker by mutableStateOf(false)
    var resizeWidget by mutableStateOf<Item?>(null)
    var confirmRemove by mutableStateOf<Item?>(null)
    var openFolderId by mutableStateOf<Long?>(null)
    var openCategory by mutableStateOf<Pair<String, List<String>>?>(null)

    var view: View? = null
    var pager: PagerState? = null
    var scope: CoroutineScope? = null
    var gridRect: CRect = CRect.Zero
    var dockRect: CRect = CRect.Zero
    var byKey: Map<String, AppInfo> = emptyMap()
    var suppressTap = false

    /** Wird von der MainActivity gesetzt: startet das Hinzufügen eines Android-Widgets. */
    var requestWidget: ((AppWidgetProviderInfo) -> Unit)? = null
    var requestReconfigure: ((Int) -> Unit)? = null

    private val density get() = context.resources.displayMetrics.density

    fun haptic(long: Boolean = false) {
        if (!prefs.haptics) return
        view?.performHapticFeedback(
            if (long) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY,
        )
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun visible(i: Item): Boolean {
        val hidden = prefs.hiddenApps
        return when (i.type) {
            ItemType.APP -> i.appKey != null && i.appKey in byKey && i.appKey !in hidden
            ItemType.FOLDER -> i.apps.any { it in byKey && it !in hidden }
            else -> true
        }
    }

    fun folderApps(item: Item): List<AppInfo> {
        val hidden = prefs.hiddenApps
        return item.apps.filter { it !in hidden }.mapNotNull { byKey[it] }
    }

    fun labelOf(app: AppInfo) = prefs.labelFor(app.key, app.originalLabel)

    // ---------------- Gesten ----------------

    fun perform(action: GestureAction) {
        if (editMode || drag != null) return
        if (action != GestureAction.NONE) haptic()
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.APP_LIBRARY -> openDrawer(prefs.drawerAutoKeyboard)
            GestureAction.SEARCH -> openDrawer(true)
            GestureAction.NOTIFICATIONS -> SystemActions.expandNotifications(context)
            GestureAction.QUICK_SETTINGS -> SystemActions.expandQuickSettings(context)
            GestureAction.EDIT_MODE -> enterEditMode()
            GestureAction.LAUNCHER_SETTINGS -> openSettings()
            GestureAction.LOCK_SCREEN -> SystemActions.lockScreen(context)
            GestureAction.RECENTS -> SystemActions.recents(context)
        }
    }

    fun openDrawer(focus: Boolean) {
        drawerFocus = focus
        drawerOpen = true
    }

    fun closeDrawer() {
        drawerOpen = false
        drawerFocus = false
        openCategory = null
    }

    fun closeAll() {
        menu = null
        rename = null
        removeApp = null
        resizeWidget = null
        widgetPicker = false
        openFolderId = null
        closeDrawer()
    }

    fun enterEditMode() {
        menu = null
        editMode = true
        haptic(true)
    }

    fun exitEditMode() {
        editMode = false
        ws.compactPages()
        ws.normalize(prefs.columns, prefs.rows, ::visible)
    }

    fun openSettings() {
        context.startActivity(Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun launch(app: AppInfo, bounds: Rect?) {
        menu = null
        AppRepository.launch(context, app, view, bounds, prefs.openAnimation)
    }

    fun showMenu(target: MenuTarget, anchor: Rect?) {
        if (anchor == null) return
        haptic(true)
        menu = MenuState(target, anchor)
    }

    // ---------------- Erster Start / neue Apps ----------------

    fun syncApps(apps: List<AppInfo>) {
        if (apps.isEmpty()) return
        byKey = apps.associateBy { it.key }
        val keys = apps.map { it.key }
        val cols = prefs.columns
        val rows = prefs.rows
        if (!ws.hasLayout) {
            buildInitialLayout(apps)
            prefs.knownApps = keys
            return
        }
        val known = prefs.knownApps.toSet()
        val newKeys = keys.filter { it !in known }
        if (newKeys.isNotEmpty() && prefs.autoAddNewApps) {
            newKeys.filter { !ws.contains(it) && it !in prefs.hiddenApps }.forEach { ws.addApp(it, cols, rows, ::visible) }
        }
        if (known != keys.toSet()) prefs.knownApps = keys
        ws.normalize(cols, rows, ::visible)
    }

    private fun buildInitialLayout(apps: List<AppInfo>) {
        val cols = prefs.columns
        val rows = prefs.rows
        val hidden = prefs.hiddenApps
        val migrate = prefs.homeOrder.isNotEmpty()
        val dockKeys = (if (prefs.dockApps.isNotEmpty()) prefs.dockApps else AppRepository.defaultDock(context, apps))
            .filter { it in byKey && it !in hidden }.distinct().take(prefs.dockCount)
        val list = mutableListOf<Item>()
        dockKeys.forEachIndexed { i, k -> list += Item(0, ItemType.APP, Container.DOCK, x = i, appKey = k) }
        if (!migrate && rows >= 4) {
            list += Item(0, ItemType.BUILTIN, page = 0, x = 0, y = 0, spanX = 2.coerceAtMost(cols), spanY = 2, builtin = BuiltinWidget.CLOCK_ANALOG.name)
            if (cols >= 4) {
                list += Item(0, ItemType.BUILTIN, page = 0, x = 2, y = 0, spanX = 2, spanY = 2, builtin = BuiltinWidget.CALENDAR.name)
            }
        }
        ws.setAll(list)
        val order = if (migrate) prefs.homeOrder else apps.map { it.key }
        order.filter { it in byKey && it !in hidden && it !in dockKeys }.distinct()
            .forEach { ws.addApp(it, cols, rows, ::visible) }
    }

    // ---------------- Drag & Drop ----------------

    fun pendingDrag(item: Item, source: DragSource, folderId: Long?, bounds: Rect?, local: Offset) {
        val b = bounds ?: return
        drag = DragState(
            item, source, folderId,
            grab = local,
            size = IntSize(b.width(), b.height()),
            start = Offset(b.left + local.x, b.top + local.y),
        )
    }

    fun beginDrag(item: Item, source: DragSource, folderId: Long?, bounds: Rect?, local: Offset) {
        pendingDrag(item, source, folderId, bounds, local)
        drag?.active = true
        menu = null
        haptic()
    }

    fun dragBy(delta: Offset) {
        val d = drag ?: return
        d.pointer += delta
        if (!d.active) {
            d.accum += delta
            if (d.accum.getDistance() > 10 * density) {
                d.active = true
                menu = null
                if (!editMode) editMode = true
                haptic()
            }
        }
        if (d.active) checkEdge(d.pointer)
    }

    fun endDrag() {
        val d = drag
        edgeJob?.cancel()
        edgeDir = 0
        if (d != null && d.active) drop(d)
        drag = null
        if (d?.source == DragSource.FOLDER && d.active) openFolderId = null
    }

    fun cancelDrag() {
        edgeJob?.cancel()
        edgeDir = 0
        drag = null
    }

    private var edgeJob: Job? = null
    private var edgeDir = 0

    private fun checkEdge(p: Offset) {
        val w = view?.rootView?.width ?: return
        val margin = 30 * density
        val dir = when {
            p.x > w - margin -> 1
            p.x < margin -> -1
            else -> 0
        }
        if (dir == edgeDir) return
        edgeDir = dir
        edgeJob?.cancel()
        if (dir != 0) {
            edgeJob = scope?.launch {
                delay(550)
                val pg = pager ?: return@launch
                val target = pg.currentPage + dir
                if (target in 0 until pg.pageCount) {
                    haptic()
                    pg.animateScrollToPage(target)
                }
                edgeDir = 0
            }
        }
    }

    data class DropPreview(val page: Int, val x: Int, val y: Int, val sx: Int, val sy: Int, val ok: Boolean, val folder: Boolean)

    /** Wo würde das Element landen? (für die Hervorhebung) */
    fun preview(d: DragState): DropPreview? {
        if (!gridRect.contains(d.pointer)) return null
        val cols = prefs.columns
        val rows = prefs.rows
        val (x, y, sx, sy) = cellFor(d, cols, rows)
        val page = pager?.currentPage ?: 0
        val target = ws.itemAt(page, x, y, d.item.id, ::visible)
        val folder = d.item.type == ItemType.APP && sx == 1 && sy == 1 &&
            (target?.type == ItemType.APP || target?.type == ItemType.FOLDER)
        val ok = folder || ws.isFree(page, x, y, sx, sy, cols, rows, d.item.id, ::visible)
        return DropPreview(page, x, y, sx, sy, ok, folder)
    }

    private fun cellFor(d: DragState, cols: Int, rows: Int): IntArray {
        val sx = d.item.spanX.coerceIn(1, cols)
        val sy = d.item.spanY.coerceIn(1, rows)
        val cw = gridRect.width / cols
        val ch = gridRect.height / rows
        val p = d.pointer
        val cx: Int
        val cy: Int
        if (sx == 1 && sy == 1) {
            cx = ((p.x - gridRect.left) / cw).toInt()
            cy = ((p.y - gridRect.top) / ch).toInt()
        } else {
            val tl = p - d.grab
            cx = ((tl.x - gridRect.left) / cw + 0.5f).toInt()
            cy = ((tl.y - gridRect.top) / ch + 0.5f).toInt()
        }
        return intArrayOf(cx.coerceIn(0, cols - sx), cy.coerceIn(0, rows - sy), sx, sy)
    }


    private fun drop(d: DragState) {
        val item = d.item
        val p = d.pointer
        val cols = prefs.columns
        val rows = prefs.rows
        val fid = d.folderId
        val key = item.appKey
        val isIcon = item.type == ItemType.APP || item.type == ItemType.FOLDER

        // ---- ins Dock ----
        if (prefs.dockEnabled && isIcon && dockRect.contains(p)) {
            val dockItems = ws.dock.filter(::visible)
            val already = d.source == DragSource.DOCK
            if (!already && dockItems.size >= prefs.dockCount) {
                toast("Das Dock ist voll")
                return
            }
            val n = (dockItems.size + if (already) 0 else 1).coerceAtLeast(1)
            val idx = ((p.x - dockRect.left) / (dockRect.width / n)).toInt().coerceIn(0, n - 1)
            if (d.source == DragSource.FOLDER && fid != null && key != null) {
                ws.takeFromFolder(fid, key)
                val id = ws.add(Item(0, ItemType.APP, Container.DOCK, x = 999, appKey = key))
                ws.moveToDock(id, idx)
            } else {
                ws.moveToDock(item.id, idx)
            }
            haptic()
            return
        }

        val page = pager?.currentPage ?: 0
        if (!gridRect.contains(p)) {
            if (d.source == DragSource.FOLDER && fid != null && key != null) {
                ws.takeFromFolder(fid, key)
                ws.addApp(key, cols, rows, ::visible, page)
            }
            return
        }
        val (x, y, sx, sy) = cellFor(d, cols, rows)
        val target = ws.itemAt(page, x, y, item.id, ::visible)

        // ---- Ordner erstellen / in Ordner legen ----
        if (item.type == ItemType.APP && key != null && sx == 1 && sy == 1 && target != null) {
            if (target.type == ItemType.APP) {
                if (d.source == DragSource.FOLDER && fid != null) {
                    ws.takeFromFolder(fid, key)
                    ws.createFolderWithKey(target.id, key)
                } else {
                    ws.createFolder(target.id, item.id)
                }
                haptic()
                return
            }
            if (target.type == ItemType.FOLDER) {
                if (d.source == DragSource.FOLDER && fid != null) {
                    if (fid == target.id) return
                    ws.takeFromFolder(fid, key)
                    ws.addKeyToFolder(target.id, key)
                } else {
                    ws.addToFolder(target.id, item.id)
                }
                haptic()
                return
            }
        }

        // ---- auf freien Platz ----
        if (ws.isFree(page, x, y, sx, sy, cols, rows, item.id, ::visible)) {
            if (d.source == DragSource.FOLDER && fid != null && key != null) {
                ws.takeFromFolder(fid, key)
                ws.add(Item(0, ItemType.APP, page = page, x = x, y = y, appKey = key))
            } else {
                ws.moveTo(item.id, page, x, y)
            }
            haptic()
        } else if (d.source == DragSource.FOLDER && fid != null && key != null) {
            ws.takeFromFolder(fid, key)
            ws.addApp(key, cols, rows, ::visible, page)
        }
    }

    // ---------------- App-Aktionen ----------------

    fun itemForApp(key: String) = ws.items.firstOrNull { it.type == ItemType.APP && it.appKey == key }

    fun isOnHome(key: String) = ws.contains(key)

    fun addToHome(key: String) {
        if (ws.contains(key)) {
            toast("Ist schon auf dem Home-Bildschirm")
            return
        }
        ws.addApp(key, prefs.columns, prefs.rows, ::visible)
        toast("Zum Home-Bildschirm hinzugefügt")
    }

    fun addToDock(key: String) {
        if (!prefs.dockEnabled) {
            toast("Das Dock ist in den Einstellungen ausgeschaltet")
            return
        }
        if (ws.dock.filter(::visible).size >= prefs.dockCount) {
            toast("Das Dock ist voll (max. ${prefs.dockCount})")
            return
        }
        val existing = itemForApp(key)
        if (existing != null) ws.moveToDock(existing.id, 99)
        else {
            val id = ws.add(Item(0, ItemType.APP, Container.DOCK, x = 999, appKey = key))
            ws.moveToDock(id, 99)
        }
    }

    fun removeFromDock(item: Item) {
        val (p, x, y) = ws.findFree(1, 1, prefs.columns, prefs.rows, ::visible)
        ws.moveTo(item.id, p, x, y)
    }

    fun removeFromHome(key: String) = ws.removeApp(key)

    fun hide(key: String) {
        prefs.hiddenApps = (prefs.hiddenApps - key) + key
        ws.removeApp(key)
        toast("App ausgeblendet – in den Einstellungen wieder einblendbar")
    }

    fun takeOutOfFolder(folderId: Long, key: String) {
        ws.takeFromFolder(folderId, key)
        ws.addApp(key, prefs.columns, prefs.rows, ::visible, pager?.currentPage ?: 0)
    }

    fun dissolveFolder(item: Item) {
        ws.remove(item.id)
        if (openFolderId == item.id) openFolderId = null
    }

    fun applyRename(t: RenameTarget, value: String?) {
        when (t) {
            is RenameTarget.App -> prefs.customLabels =
                if (value.isNullOrBlank() || value == t.original) prefs.customLabels - t.key
                else prefs.customLabels + (t.key to value.trim())
            is RenameTarget.Folder -> ws.byId(t.id)?.let { ws.replace(it.copy(title = value?.trim().orEmpty().ifEmpty { "Ordner" })) }
        }
    }

    // ---------------- Widgets ----------------

    private fun cellPx(): Pair<Float, Float> {
        val cw = if (gridRect.width > 0) gridRect.width / prefs.columns else 90 * density
        val ch = if (gridRect.height > 0) gridRect.height / prefs.rows else 100 * density
        return cw to ch
    }

    fun placeWidget(id: Int, info: AppWidgetProviderInfo) {
        val cols = prefs.columns
        val rows = prefs.rows
        val (cw, ch) = cellPx()
        var sx = ceil(info.minWidth / cw).toInt()
        var sy = ceil(info.minHeight / ch).toInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            sx = info.targetCellWidth
            sy = info.targetCellHeight
        }
        sx = sx.coerceIn(1, cols)
        sy = sy.coerceIn(1, rows)
        place(Item(0, ItemType.WIDGET, spanX = sx, spanY = sy, widgetId = id, provider = info.provider.flattenToString()))
    }

    fun addBuiltin(type: BuiltinWidget) {
        place(
            Item(
                0, ItemType.BUILTIN,
                spanX = type.spanX.coerceAtMost(prefs.columns), spanY = type.spanY.coerceAtMost(prefs.rows),
                builtin = type.name,
            ),
        )
    }

    private fun place(item: Item) {
        val start = pager?.currentPage ?: 0
        val (p, x, y) = ws.findFree(item.spanX, item.spanY, prefs.columns, prefs.rows, ::visible, start)
        ws.add(item.copy(page = p, x = x, y = y))
        widgetPicker = false
        scope?.launch { pager?.animateScrollToPage(p) }
    }

    fun removeWidget(item: Item) {
        if (item.type == ItemType.WIDGET) Widgets.delete(item.widgetId)
        ws.remove(item.id)
    }

    fun resize(item: Item, sx: Int, sy: Int): Boolean {
        if (!ws.isFree(item.page, item.x, item.y, sx, sy, prefs.columns, prefs.rows, item.id, ::visible)) return false
        ws.replace(item.copy(spanX = sx, spanY = sy))
        return true
    }
}
