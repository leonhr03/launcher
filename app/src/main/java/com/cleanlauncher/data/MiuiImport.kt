package com.cleanlauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Environment
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.InflaterInputStream
import java.util.zip.ZipInputStream

/**
 * Übernimmt das Startbildschirm-Layout aus einer MIUI/HyperOS-Sicherung
 * (Einstellungen → Sichern & wiederherstellen → Startbildschirm).
 * Liest die Launcher-Datenbank (favorites) und baut Seiten, Dock, Ordner und Widgets nach.
 */
object MiuiImport {

    class ImportException(msg: String) : Exception(msg)

    data class PendingWidget(val provider: ComponentName, val page: Int, val x: Int, val y: Int, val sx: Int, val sy: Int) {
        fun encode() = listOf(provider.flattenToString(), page, x, y, sx, sy).joinToString("|")

        companion object {
            fun decode(s: String): PendingWidget? = try {
                val p = s.split("|")
                PendingWidget(ComponentName.unflattenFromString(p[0])!!, p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toInt())
            } catch (e: Exception) {
                null
            }
        }
    }

    data class Result(
        val items: List<Item>,
        val widgets: List<PendingWidget>,
        val cols: Int,
        val rows: Int,
        val pages: Int,
        val apps: Int,
        val folders: Int,
        val missing: Int,
        val skipped: Int,
    )

    private const val DESKTOP = -100
    private const val HOTSEAT = -101

    // ---------------- Sicherung finden ----------------

    /** Sucht die neueste MIUI-Startbildschirm-Sicherung im Speicher (braucht Dateizugriff). */
    fun findBackup(): File? = try {
        val roots = listOf(
            File(Environment.getExternalStorageDirectory(), "MIUI/backup/AllBackup"),
            File(Environment.getExternalStorageDirectory(), "MIUI/backup"),
            File(Environment.getExternalStorageDirectory(), "Download"),
        )
        roots.filter { it.isDirectory }
            .flatMap { root -> root.walkTopDown().maxDepth(3).filter { it.isFile && looksLikeHomeBackup(it.name) }.toList() }
            .maxByOrNull { it.lastModified() }
    } catch (e: Exception) {
        null
    }

    private fun looksLikeHomeBackup(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".bak") && (n.contains("com.miui.home") || n.contains("startbildschirm") || n.contains("home") || n.contains("launcher"))
    }

    // ---------------- Entpacken ----------------

    fun read(context: Context, uri: Uri): Result =
        context.contentResolver.openInputStream(uri)?.use { parse(context, it) }
            ?: throw ImportException("Datei konnte nicht geöffnet werden")

    fun read(context: Context, file: File): Result = file.inputStream().use { parse(context, it) }

    private fun parse(context: Context, raw: InputStream): Result {
        val dir = File(context.cacheDir, "miui_import").apply {
            deleteRecursively()
            mkdirs()
        }
        val input = BufferedInputStream(raw, 1 shl 16)
        input.mark(1 shl 15)
        val head = ByteArray(16384)
        val n = readFully(input, head)
        val text = String(head, 0, n, Charsets.ISO_8859_1)
        input.reset()
        val abIndex = text.indexOf("ANDROID BACKUP\n")
        when {
            abIndex >= 0 -> {
                skipFully(input, abIndex.toLong())
                readLine(input) // ANDROID BACKUP
                readLine(input) // Version
                val compressed = readLine(input).trim() == "1"
                val enc = readLine(input).trim()
                if (!enc.equals("none", true)) {
                    throw ImportException("Die Sicherung ist mit einem Passwort verschlüsselt. Bitte die Sicherung ohne Passwort erstellen.")
                }
                untar(if (compressed) InflaterInputStream(input) else input, dir)
            }
            text.startsWith("SQLite format 3") -> File(dir, "launcher.db").outputStream().use { input.copyTo(it) }
            text.startsWith("PK") -> unzip(input, dir)
            text.indexOf("ustar") in 0..600 -> untar(input, dir)
            else -> throw ImportException("Unbekanntes Dateiformat – bitte die Startbildschirm-Sicherung (.bak) von Xiaomi wählen.")
        }
        val db = dir.listFiles()?.filter { it.name.endsWith(".db") && it.length() > 0 }
            ?.sortedWith(compareByDescending<File> { it.name.contains("launcher") }.thenByDescending { it.length() })
            ?.firstOrNull()
            ?: throw ImportException("In der Sicherung wurde keine Startbildschirm-Datenbank gefunden.")
        return readDb(db)
    }

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val r = input.read(buf, off, buf.size - off)
            if (r < 0) break
            off += r
        }
        return off
    }

    private fun skipFully(input: InputStream, n: Long) {
        var left = n
        val tmp = ByteArray(8192)
        while (left > 0) {
            val r = input.read(tmp, 0, minOf(tmp.size.toLong(), left).toInt())
            if (r < 0) break
            left -= r
        }
    }

    private fun readLine(input: InputStream): String {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0 || c == '\n'.code) break
            sb.append(c.toChar())
        }
        return sb.toString()
    }

    private fun wanted(name: String): Boolean {
        val base = name.substringAfterLast('/')
        return (base.endsWith(".db") || base.endsWith(".db-wal")) &&
            (name.contains("/db/") || name.contains("launcher") || name.contains("miui.home"))
    }

    private fun untar(input: InputStream, dir: File) {
        val header = ByteArray(512)
        var longName: String? = null
        while (true) {
            if (readFully(input, header) < 512) break
            if (header.all { it == 0.toByte() }) break
            val name = cstr(header, 0, 100)
            val prefix = cstr(header, 345, 155)
            val size = octal(header, 124, 12)
            val type = header[156].toInt().toChar()
            val pad = (512 - size % 512) % 512
            if (type == 'L') {
                val data = ByteArray(size.toInt())
                readFully(input, data)
                longName = String(data, Charsets.UTF_8).trimEnd('\u0000')
                skipFully(input, pad)
                continue
            }
            val full = longName ?: if (prefix.isNotEmpty()) "$prefix/$name" else name
            longName = null
            if ((type == '0' || type == '\u0000') && wanted(full)) {
                val out = File(dir, full.substringAfterLast('/'))
                out.outputStream().use { o ->
                    var left = size
                    val buf = ByteArray(16384)
                    while (left > 0) {
                        val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (r < 0) break
                        o.write(buf, 0, r)
                        left -= r
                    }
                }
            } else {
                skipFully(input, size)
            }
            skipFully(input, pad)
        }
    }

    private fun unzip(input: InputStream, dir: File) {
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (!e.isDirectory && wanted(e.name)) {
                    File(dir, e.name.substringAfterLast('/')).outputStream().use { z.copyTo(it) }
                } else if (!e.isDirectory && (e.name.endsWith(".bak") || e.name.endsWith(".tar"))) {
                    // verschachtelte Sicherung
                    untar(z, dir)
                }
            }
        }
    }

    private fun cstr(b: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && b[end] != 0.toByte()) end++
        return String(b, off, end - off, Charsets.UTF_8).trim()
    }

    private fun octal(b: ByteArray, off: Int, len: Int): Long {
        val s = cstr(b, off, len).trim()
        return if (s.isEmpty()) 0 else s.toLong(8)
    }

    // ---------------- Datenbank lesen ----------------

    private class Row(
        val id: Long,
        val title: String,
        val intent: String?,
        val container: Long,
        val screen: Long,
        val x: Int,
        val y: Int,
        val sx: Int,
        val sy: Int,
        val type: Int,
        val provider: String?,
    )

    private fun Cursor.str(col: String): String? {
        val i = getColumnIndex(col)
        return if (i < 0 || isNull(i)) null else getString(i)
    }

    private fun Cursor.long(col: String, def: Long = 0): Long {
        val i = getColumnIndex(col)
        return if (i < 0 || isNull(i)) def else getLong(i)
    }

    private fun readDb(file: File): Result {
        val db = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            throw ImportException("Die Startbildschirm-Datenbank konnte nicht gelesen werden.")
        }
        db.use {
            val tables = mutableSetOf<String>()
            it.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                while (c.moveToNext()) tables += c.getString(0)
            }
            if ("favorites" !in tables) throw ImportException("Keine Startbildschirm-Daten (favorites) in der Sicherung gefunden.")

            // Bildschirm-Reihenfolge
            val screenOrder = HashMap<Long, Int>()
            if ("screens" in tables) {
                it.rawQuery("SELECT * FROM screens", null).use { c ->
                    val list = mutableListOf<Pair<Long, Long>>()
                    while (c.moveToNext()) {
                        val id = c.long("_id", -1)
                        val order = c.long("screenOrder", c.long("screen_order", id))
                        list += id to order
                    }
                    list.sortedBy { p -> p.second }.forEachIndexed { i, p -> screenOrder[p.first] = i }
                }
            }

            val rows = mutableListOf<Row>()
            it.rawQuery("SELECT * FROM favorites", null).use { c ->
                while (c.moveToNext()) {
                    rows += Row(
                        id = c.long("_id"),
                        title = c.str("title").orEmpty(),
                        intent = c.str("intent"),
                        container = c.long("container", DESKTOP.toLong()),
                        screen = c.long("screen"),
                        x = c.long("cellX").toInt(),
                        y = c.long("cellY").toInt(),
                        sx = c.long("spanX", 1).toInt().coerceAtLeast(1),
                        sy = c.long("spanY", 1).toInt().coerceAtLeast(1),
                        type = c.long("itemType").toInt(),
                        provider = c.str("appWidgetProvider"),
                    )
                }
            }
            return build(file.name, rows, screenOrder)
        }
    }

    private fun build(dbName: String, rows: List<Row>, screenOrder: Map<Long, Int>): Result {
        val apps = AppRepository.apps
        val byComponent = apps.associateBy { it.component }
        fun keyFor(intent: String?): String? {
            intent ?: return null
            val cn = try {
                Intent.parseUri(intent, 0).component
            } catch (e: Exception) {
                null
            } ?: return null
            return (byComponent[cn] ?: apps.firstOrNull { it.packageName == cn.packageName })?.key
        }

        // Rastergröße: aus dem Datenbanknamen (z. B. launcher4x6.db) oder aus den Positionen
        val m = Regex("(\\d)x(\\d)").find(dbName)
        val desktopRows = rows.filter { it.container == DESKTOP.toLong() }
        var cols = m?.groupValues?.get(1)?.toInt() ?: (desktopRows.maxOfOrNull { it.x + it.sx } ?: 4)
        var gridRows = m?.groupValues?.get(2)?.toInt() ?: (desktopRows.maxOfOrNull { it.y + it.sy } ?: 6)
        cols = cols.coerceIn(3, 7).coerceAtLeast(desktopRows.maxOfOrNull { it.x + it.sx } ?: 0)
        gridRows = gridRows.coerceIn(3, 9).coerceAtLeast(desktopRows.maxOfOrNull { it.y + it.sy } ?: 0)

        // Seiten in Reihenfolge durchnummerieren
        val usedScreens = desktopRows.map { screenOrder[it.screen] ?: it.screen.toInt() }.distinct().sorted()
        val pageOf = usedScreens.withIndex().associate { it.value to it.index }
        fun page(r: Row) = pageOf[screenOrder[r.screen] ?: r.screen.toInt()] ?: 0

        val folderIds = rows.filter { it.type == 2 }.map { it.id }.toSet()
        val items = mutableListOf<Item>()
        val widgets = mutableListOf<PendingWidget>()
        var missing = 0
        var skipped = 0
        var appCount = 0
        var folders = 0

        // Ordnerinhalte
        val folderContent = rows.filter { it.container in folderIds }
            .groupBy { it.container }
            .mapValues { (_, list) ->
                list.sortedWith(compareBy({ it.screen }, { it.y }, { it.x }))
                    .mapNotNull { r -> keyFor(r.intent).also { k -> if (k == null) missing++ else appCount++ } }
                    .distinct()
            }

        // Dock (Reihenfolge nach Spalte, sonst nach "screen")
        val hotseat = rows.filter { it.container == HOTSEAT.toLong() }
        val byX = hotseat.map { it.x }.distinct().size == hotseat.size
        hotseat.sortedBy { if (byX) it.x.toLong() else it.screen }.forEachIndexed { i, r ->
            when (r.type) {
                0, 1, 6 -> keyFor(r.intent)?.let {
                    appCount++
                    items += Item(0, ItemType.APP, Container.DOCK, x = i, appKey = it)
                } ?: run { missing++ }
                2 -> folderContent[r.id]?.takeIf { it.isNotEmpty() }?.let {
                    folders++
                    items += Item(0, ItemType.FOLDER, Container.DOCK, x = i, title = folderTitle(r.title), apps = it)
                }
                else -> skipped++
            }
        }

        // Startbildschirm-Seiten
        desktopRows.forEach { r ->
            val p = page(r)
            when (r.type) {
                0, 1, 6 -> keyFor(r.intent)?.let {
                    appCount++
                    items += Item(0, ItemType.APP, page = p, x = r.x, y = r.y, appKey = it)
                } ?: run { missing++ }
                2 -> {
                    val content = folderContent[r.id].orEmpty()
                    when (content.size) {
                        0 -> Unit
                        1 -> items += Item(0, ItemType.APP, page = p, x = r.x, y = r.y, appKey = content[0])
                        else -> {
                            folders++
                            items += Item(0, ItemType.FOLDER, page = p, x = r.x, y = r.y, title = folderTitle(r.title), apps = content)
                        }
                    }
                }
                4 -> {
                    val cn = r.provider?.let { ComponentName.unflattenFromString(it) }
                    if (cn != null) widgets += PendingWidget(cn, p, r.x, r.y, r.sx, r.sy) else skipped++
                }
                else -> skipped++ // MIUI-eigene Widgets (Gadgets) gibt es nur im Xiaomi-Launcher
            }
        }
        if (items.isEmpty() && widgets.isEmpty()) throw ImportException("Die Sicherung enthält keine Apps, die auf diesem Handy installiert sind.")
        return Result(
            items = items,
            widgets = widgets,
            cols = cols,
            rows = gridRows,
            pages = usedScreens.size.coerceAtLeast(1),
            apps = appCount,
            folders = folders,
            missing = missing,
            skipped = skipped,
        )
    }

    private fun folderTitle(t: String): String =
        if (t.isBlank() || t.startsWith("@") || t.startsWith("com.")) "Ordner" else t

    /** Übernimmt das Ergebnis: ersetzt das aktuelle Layout. Widgets werden danach nacheinander hinzugefügt. */
    fun apply(context: Context, r: Result) {
        val prefs = Prefs.get(context)
        prefs.columns = r.cols
        prefs.rows = r.rows
        val dockSize = r.items.count { it.container == Container.DOCK }
        if (dockSize > 0) prefs.dockCount = dockSize.coerceIn(2, 7)
        prefs.dockEnabled = true
        prefs.hiddenApps = emptyList()
        // Apps, die bei Xiaomi nicht auf dem Startbildschirm lagen, bleiben in der App-Mediathek
        prefs.knownApps = AppRepository.apps.map { it.key }
        Workspace.get(context).setAll(r.items)
        prefs.pendingWidgets = r.widgets.map { it.encode() }
    }
}
