package com.cleanlauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Eigene Sicherung: alle Launcher-Einstellungen + Layout als eine Datei. */
object Backup {

    fun export(context: Context, uri: Uri) {
        val sp = context.getSharedPreferences("clean_launcher", Context.MODE_PRIVATE)
        val prefs = JSONObject()
        for ((k, v) in sp.all) {
            val o = JSONObject()
            when (v) {
                is Int -> o.put("t", "i").put("v", v)
                is Long -> o.put("t", "l").put("v", v)
                is Float -> o.put("t", "f").put("v", v.toDouble())
                is Boolean -> o.put("t", "b").put("v", v)
                is String -> o.put("t", "s").put("v", v)
                else -> continue
            }
            prefs.put(k, o)
        }
        val ws = File(context.filesDir, "workspace.json").takeIf { it.exists() }?.readText() ?: "[]"
        val root = JSONObject()
            .put("app", "CleanLauncher")
            .put("version", 1)
            .put("prefs", prefs)
            .put("workspace", JSONArray(ws))
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(root.toString(2).toByteArray()) }
            ?: throw IllegalStateException("Datei konnte nicht geschrieben werden")
    }

    /** Stellt eine Sicherung wieder her. Danach muss der Launcher neu starten. */
    fun restore(context: Context, uri: Uri) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("Datei konnte nicht gelesen werden")
        val root = JSONObject(text)
        require(root.optString("app") == "CleanLauncher") { "Das ist keine Clean-Launcher-Sicherung" }
        val prefs = root.getJSONObject("prefs")
        val ed = context.getSharedPreferences("clean_launcher", Context.MODE_PRIVATE).edit().clear()
        for (k in prefs.keys()) {
            val o = prefs.getJSONObject(k)
            when (o.getString("t")) {
                "i" -> ed.putInt(k, o.getInt("v"))
                "l" -> ed.putLong(k, o.getLong("v"))
                "f" -> ed.putFloat(k, o.getDouble("v").toFloat())
                "b" -> ed.putBoolean(k, o.getBoolean("v"))
                "s" -> ed.putString(k, o.getString("v"))
            }
        }
        // Widgets müssen auf dem Gerät neu angelegt werden (alte Widget-IDs gelten nicht mehr)
        val arr = root.optJSONArray("workspace") ?: JSONArray()
        val items = (0 until arr.length()).map { Item.fromJson(arr.getJSONObject(it)) }
        val widgets = items.filter { it.type == ItemType.WIDGET && it.provider != null }.mapNotNull { w ->
            ComponentName.unflattenFromString(w.provider!!)?.let {
                MiuiImport.PendingWidget(it, w.page, w.x, w.y, w.spanX, w.spanY).encode()
            }
        }
        ed.putString("pending_widgets", widgets.joinToString("\n"))
        ed.commit()
        val rest = JSONArray(items.filter { it.type != ItemType.WIDGET }.map { it.toJson() })
        File(context.filesDir, "workspace.json").writeText(rest.toString())
    }

    /** Neustart, damit alle Einstellungen neu geladen werden. */
    fun restart(context: Context) {
        context.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            android.os.Process.killProcess(android.os.Process.myPid())
        }, 400)
    }

    /** Öffnet die Xiaomi-Sicherung (Sichern & wiederherstellen). */
    fun openXiaomiBackup(context: Context): Boolean {
        val pm = context.packageManager
        val candidates = mutableListOf<Intent>()
        listOf(
            "com.miui.backup.ui.LocalBackupActivity",
            "com.miui.backup.ui.BackupActivity",
            "com.miui.backup.BackupActivity",
        ).forEach { candidates += Intent().setClassName("com.miui.backup", it) }
        pm.getLaunchIntentForPackage("com.miui.backup")?.let { candidates += it }
        candidates += Intent("miui.intent.action.BACKUP_SETTINGS")
        candidates += Intent().setClassName("com.android.settings", "com.android.settings.Settings\$PrivacySettingsActivity")
        candidates += Intent(android.provider.Settings.ACTION_PRIVACY_SETTINGS)
        for (i in candidates) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }
}
