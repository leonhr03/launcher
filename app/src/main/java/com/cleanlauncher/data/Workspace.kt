package com.cleanlauncher.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ItemType { APP, FOLDER, WIDGET, BUILTIN }

object Container {
    const val DESKTOP = 0
    const val DOCK = 1
}

enum class BuiltinWidget(val title: String, val spanX: Int, val spanY: Int) {
    CLOCK_ANALOG("Uhr (analog)", 2, 2),
    CLOCK_DIGITAL("Uhr & Datum", 4, 2),
    CALENDAR("Kalender", 2, 2),
    BATTERY("Batterie", 2, 2),
}

/** Ein Element auf dem Startbildschirm: App, Ordner, Android-Widget oder eingebautes Widget. */
data class Item(
    val id: Long,
    val type: ItemType,
    val container: Int = Container.DESKTOP,
    val page: Int = 0,
    val x: Int = 0,
    val y: Int = 0,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val appKey: String? = null,
    val title: String = "",
    val apps: List<String> = emptyList(),
    val widgetId: Int = -1,
    val provider: String? = null,
    val builtin: String? = null,
) {
    val builtinType: BuiltinWidget? get() = builtin?.let { runCatching { BuiltinWidget.valueOf(it) }.getOrNull() }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("container", container)
        put("page", page)
        put("x", x)
        put("y", y)
        put("spanX", spanX)
        put("spanY", spanY)
        appKey?.let { put("appKey", it) }
        put("title", title)
        put("apps", JSONArray(apps))
        put("widgetId", widgetId)
        provider?.let { put("provider", it) }
        builtin?.let { put("builtin", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): Item {
            val arr = o.optJSONArray("apps")
            return Item(
                id = o.getLong("id"),
                type = ItemType.valueOf(o.getString("type")),
                container = o.optInt("container", Container.DESKTOP),
                page = o.optInt("page"),
                x = o.optInt("x"),
                y = o.optInt("y"),
                spanX = o.optInt("spanX", 1),
                spanY = o.optInt("spanY", 1),
                appKey = if (o.has("appKey")) o.getString("appKey") else null,
                title = o.optString("title"),
                apps = if (arr == null) emptyList() else (0 until arr.length()).map { arr.getString(it) },
                widgetId = o.optInt("widgetId", -1),
                provider = if (o.has("provider")) o.getString("provider") else null,
                builtin = if (o.has("builtin")) o.getString("builtin") else null,
            )
        }
    }
}

/**
 * Freies Raster wie bei iOS/MIUI: jedes Element hat Seite, Spalte, Zeile und Größe.
 * Wird als JSON gespeichert.
 */
class Workspace private constructor(context: Context) {

    private val file = File(context.filesDir, "workspace.json")

    var items by mutableStateOf<List<Item>>(emptyList())
        private set

    val hasLayout: Boolean get() = file.exists()

    init {
        if (file.exists()) {
            items = try {
                val arr = JSONArray(file.readText())
                (0 until arr.length()).map { Item.fromJson(arr.getJSONObject(it)) }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun commit(list: List<Item>) {
        items = list
        try {
            file.writeText(JSONArray(list.map { it.toJson() }).toString())
        } catch (_: Exception) {
        }
    }

    fun setAll(list: List<Item>) {
        var id = 1L
        commit(list.map { it.copy(id = id++) })
    }

    fun reset() {
        file.delete()
        items = emptyList()
    }

    private fun newId() = (items.maxOfOrNull { it.id } ?: 0L) + 1

    fun byId(id: Long) = items.firstOrNull { it.id == id }

    val desktop: List<Item> get() = items.filter { it.container == Container.DESKTOP }
    val dock: List<Item> get() = items.filter { it.container == Container.DOCK }.sortedBy { it.x }

    fun pageCount(): Int = (desktop.maxOfOrNull { it.page } ?: 0) + 1

    /** Liegt die App schon irgendwo auf dem Startbildschirm (auch im Ordner/Dock)? */
    fun contains(appKey: String) = items.any { it.appKey == appKey || appKey in it.apps }

    // ---------------- Belegung ----------------

    fun isFree(
        page: Int, x: Int, y: Int, sx: Int, sy: Int,
        cols: Int, rows: Int, excludeId: Long, visible: (Item) -> Boolean,
    ): Boolean {
        if (x < 0 || y < 0 || x + sx > cols || y + sy > rows) return false
        return desktop.none {
            it.id != excludeId && it.page == page && visible(it) &&
                it.x < x + sx && x < it.x + it.spanX && it.y < y + sy && y < it.y + it.spanY
        }
    }

    fun itemAt(page: Int, x: Int, y: Int, excludeId: Long, visible: (Item) -> Boolean): Item? =
        desktop.firstOrNull {
            it.id != excludeId && it.page == page && visible(it) &&
                x >= it.x && x < it.x + it.spanX && y >= it.y && y < it.y + it.spanY
        }

    fun findFree(
        sx: Int, sy: Int, cols: Int, rows: Int, visible: (Item) -> Boolean,
        startPage: Int = 0, excludeId: Long = -1,
    ): Triple<Int, Int, Int> {
        val w = sx.coerceIn(1, cols)
        val h = sy.coerceIn(1, rows)
        var p = startPage
        val limit = pageCount() + 1
        while (p <= limit) {
            for (y in 0..rows - h) for (x in 0..cols - w) {
                if (isFree(p, x, y, w, h, cols, rows, excludeId, visible)) return Triple(p, x, y)
            }
            p++
        }
        return Triple(limit, 0, 0)
    }

    // ---------------- Hinzufügen / Entfernen ----------------

    fun add(item: Item): Long {
        val id = newId()
        commit(items + item.copy(id = id))
        return id
    }

    fun addApp(key: String, cols: Int, rows: Int, visible: (Item) -> Boolean, startPage: Int = 0) {
        val (p, x, y) = findFree(1, 1, cols, rows, visible, startPage)
        add(Item(0, ItemType.APP, page = p, x = x, y = y, appKey = key))
    }

    fun remove(id: Long) {
        val removed = byId(id) ?: return
        val rest = items.filter { it.id != id }
        commit(if (removed.container == Container.DOCK) reindexDock(rest) else rest)
    }

    fun replace(item: Item) = commit(items.map { if (it.id == item.id) item else it })

    /** Entfernt eine App von überall (Startbildschirm, Dock, Ordner). */
    fun removeApp(key: String) {
        var list = items.filter { !(it.type == ItemType.APP && it.appKey == key) }
        list = list.mapNotNull { folderAfterRemoval(it, key) }
        commit(reindexDock(list))
    }

    private fun folderAfterRemoval(it: Item, key: String): Item? {
        if (it.type != ItemType.FOLDER || key !in it.apps) return it
        val left = it.apps - key
        return when (left.size) {
            0 -> null
            1 -> it.copy(type = ItemType.APP, appKey = left[0], apps = emptyList(), title = "")
            else -> it.copy(apps = left)
        }
    }

    private fun reindexDock(list: List<Item>): List<Item> {
        val dock = list.filter { it.container == Container.DOCK }.sortedBy { it.x }.mapIndexed { i, d -> d.copy(x = i) }
        return list.filter { it.container != Container.DOCK } + dock
    }

    // ---------------- Verschieben ----------------

    fun moveTo(id: Long, page: Int, x: Int, y: Int) {
        val it = byId(id) ?: return
        val moved = it.copy(container = Container.DESKTOP, page = page, x = x, y = y)
        commit(reindexDock(items.map { i -> if (i.id == id) moved else i }))
    }

    fun moveToDock(id: Long, index: Int) {
        val item = byId(id) ?: return
        val dock = dock.filter { it.id != id }.toMutableList()
        dock.add(index.coerceIn(0, dock.size), item.copy(container = Container.DOCK, page = 0, y = 0, spanX = 1, spanY = 1))
        val newDock = dock.mapIndexed { i, d -> d.copy(x = i) }
        commit(items.filter { it.container != Container.DOCK && it.id != id } + newDock)
    }

    fun createFolder(targetId: Long, draggedId: Long) {
        val target = byId(targetId) ?: return
        val dragged = byId(draggedId) ?: return
        val folder = target.copy(
            type = ItemType.FOLDER, appKey = null, title = "Ordner",
            apps = listOfNotNull(target.appKey, dragged.appKey),
        )
        commit(reindexDock(items.filter { it.id != draggedId }.map { if (it.id == targetId) folder else it }))
    }

    fun createFolderWithKey(targetId: Long, key: String) {
        val target = byId(targetId) ?: return
        replace(target.copy(type = ItemType.FOLDER, appKey = null, title = "Ordner", apps = listOfNotNull(target.appKey, key)))
    }

    fun addToFolder(folderId: Long, draggedId: Long) {
        val folder = byId(folderId) ?: return
        val dragged = byId(draggedId) ?: return
        val key = dragged.appKey ?: return
        commit(reindexDock(items.filter { it.id != draggedId }.map { if (it.id == folderId) folder.copy(apps = folder.apps + key) else it }))
    }

    fun addKeyToFolder(folderId: Long, key: String) {
        val folder = byId(folderId) ?: return
        if (key !in folder.apps) replace(folder.copy(apps = folder.apps + key))
    }

    /** Nimmt eine App aus dem Ordner (Ordner mit nur 1 App wird wieder zur App). */
    fun takeFromFolder(folderId: Long, key: String) {
        commit(reindexDock(items.mapNotNull { if (it.id == folderId) folderAfterRemoval(it, key) else it }))
    }

    fun reorderFolder(folderId: Long, apps: List<String>) {
        val f = byId(folderId) ?: return
        replace(f.copy(apps = apps))
    }

    /** Entfernt leere Seiten. */
    fun compactPages() {
        val used = desktop.map { it.page }.distinct().sorted()
        val map = used.withIndex().associate { it.value to it.index }
        if (used.withIndex().all { it.index == it.value }) return
        commit(items.map { if (it.container == Container.DESKTOP) it.copy(page = map[it.page] ?: 0) else it })
    }

    /** Sorgt dafür, dass nichts überlappt oder aus dem Raster ragt (z. B. nach Änderung von Spalten/Zeilen). */
    fun normalize(cols: Int, rows: Int, visible: (Item) -> Boolean) {
        val ordered = items.sortedWith(compareBy({ it.container }, { it.page }, { it.y }, { it.x }))
        val occ = HashMap<Int, Array<BooleanArray>>()
        fun grid(p: Int) = occ.getOrPut(p) { Array(rows) { BooleanArray(cols) } }
        fun fits(p: Int, x: Int, y: Int, sx: Int, sy: Int): Boolean {
            if (x < 0 || y < 0 || x + sx > cols || y + sy > rows) return false
            val g = grid(p)
            for (yy in y until y + sy) for (xx in x until x + sx) if (g[yy][xx]) return false
            return true
        }
        fun mark(p: Int, x: Int, y: Int, sx: Int, sy: Int) {
            val g = grid(p)
            for (yy in y until y + sy) for (xx in x until x + sx) g[yy][xx] = true
        }
        var changed = false
        val result = ordered.map { item ->
            if (item.container != Container.DESKTOP || !visible(item)) return@map item
            val sx = item.spanX.coerceIn(1, cols)
            val sy = item.spanY.coerceIn(1, rows)
            if (sx == item.spanX && sy == item.spanY && fits(item.page, item.x, item.y, sx, sy)) {
                mark(item.page, item.x, item.y, sx, sy)
                item
            } else {
                var p = item.page
                var found: Pair<Int, Int>? = null
                while (found == null) {
                    loop@ for (y in 0..rows - sy) for (x in 0..cols - sx) {
                        if (fits(p, x, y, sx, sy)) {
                            found = x to y
                            break@loop
                        }
                    }
                    if (found == null) p++
                }
                val (fx, fy) = found!!
                mark(p, fx, fy, sx, sy)
                changed = true
                item.copy(page = p, x = fx, y = fy, spanX = sx, spanY = sy)
            }
        }
        if (changed) commit(result)
    }

    companion object {
        @Volatile
        private var instance: Workspace? = null

        fun get(context: Context): Workspace =
            instance ?: synchronized(this) {
                instance ?: Workspace(context.applicationContext).also { instance = it }
            }
    }
}
