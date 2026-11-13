package com.cleanlauncher.data

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
    val key: String,
    val component: ComponentName,
    val user: UserHandle,
    val originalLabel: String,
    /** Das Symbol so, wie es das System liefert. */
    val icon: ImageBitmap,
    /** Randlose Version (nur bei adaptiven Symbolen) – wird für eigene Formen benutzt. */
    val fullBleed: ImageBitmap?,
    val category: Int = -1,
    val installTime: Long = 0L,
) {
    val packageName: String get() = component.packageName
}

fun categoryName(app: AppInfo): String {
    val c = app.category
    val sys = app.packageName.startsWith("com.android.") || app.packageName.startsWith("com.miui.") ||
        app.packageName.startsWith("com.xiaomi.") || app.packageName.startsWith("com.google.android.")
    return when (c) {
        android.content.pm.ApplicationInfo.CATEGORY_GAME -> "Spiele"
        android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> "Musik & Audio"
        android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> "Unterhaltung"
        android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> "Fotos & Kreativität"
        android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "Soziale Netze"
        android.content.pm.ApplicationInfo.CATEGORY_NEWS -> "Nachrichten"
        android.content.pm.ApplicationInfo.CATEGORY_MAPS -> "Reisen & Navigation"
        android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Produktivität"
        android.content.pm.ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Dienstprogramme"
        else -> if (sys) "Dienstprogramme" else "Weitere"
    }
}

object AppRepository {

    private const val ICON_PX = 192

    var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set

    private lateinit var ctx: Context
    fun appFor(pkg: String): AppInfo? = apps.firstOrNull { it.packageName == pkg }
    private lateinit var launcherApps: LauncherApps
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private val cache = java.util.concurrent.ConcurrentHashMap<String, AppInfo>()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            reload(*packageNames)

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            reload(*packageNames)
    }

    fun init(context: Context) {
        if (::ctx.isInitialized) return
        ctx = context.applicationContext
        launcherApps = ctx.getSystemService(LauncherApps::class.java)
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    fun reload(vararg invalidatePackages: String) {
        if (invalidatePackages.isNotEmpty()) {
            cache.keys.removeAll { key -> invalidatePackages.any { key.startsWith("$it/") } }
        }
        job?.cancel()
        job = scope.launch {
            val list = withContext(Dispatchers.Default) { load() }
            apps = list
            loaded = true
        }
    }

    private fun load(): List<AppInfo> {
        val um = ctx.getSystemService(UserManager::class.java)
        val result = ArrayList<AppInfo>()
        for (user in launcherApps.profiles) {
            val serial = if (user == Process.myUserHandle()) null else um.getSerialNumberForUser(user)
            val activities = try {
                launcherApps.getActivityList(null, user)
            } catch (e: Exception) {
                emptyList()
            }
            for (info in activities) {
                val key = info.componentName.flattenToString() + (serial?.let { "#$it" } ?: "")
                val cached = cache[key]
                if (cached != null) {
                    result += cached
                    continue
                }
                val app = build(key, info) ?: continue
                cache[key] = app
                result += app
            }
        }
        return result.sortedBy { it.originalLabel.lowercase() }
    }

    private fun build(key: String, info: LauncherActivityInfo): AppInfo? = try {
        val drawable = info.getIcon(0)
        AppInfo(
            key = key,
            component = info.componentName,
            user = info.user,
            originalLabel = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName },
            icon = drawable.toBitmap(ICON_PX).asImageBitmap(),
            fullBleed = (drawable as? AdaptiveIconDrawable)?.fullBleed(ICON_PX)?.asImageBitmap(),
            category = info.applicationInfo?.category ?: -1,
            installTime = info.firstInstallTime,
        )
    } catch (e: Exception) {
        null
    }

    private fun Drawable.toBitmap(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        setBounds(0, 0, size, size)
        draw(c)
        return bmp
    }

    /** Zeichnet Vorder- und Hintergrund eines adaptiven Symbols ohne Systemmaske. */
    private fun AdaptiveIconDrawable.fullBleed(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // Sichtbarer Bereich ist 72 von 108 Einheiten -> 25 % Überstand auf jeder Seite.
        val inset = (size * 0.25f).toInt()
        val bounds = Rect(-inset, -inset, size + inset, size + inset)
        background?.let { it.bounds = bounds; it.draw(c) }
        foreground?.let { it.bounds = bounds; it.draw(c) }
        return bmp
    }

    // ---------------- Aktionen ----------------

    fun launch(context: Context, app: AppInfo, view: View?, bounds: Rect?, animate: Boolean) {
        try {
            val prefs = Prefs.get(context)
            val counts = prefs.launchCounts
            prefs.launchCounts = counts + (app.key to ((counts[app.key]?.toIntOrNull() ?: 0) + 1).toString())
            val opts = if (animate && view != null && bounds != null) {
                val loc = IntArray(2)
                view.getLocationOnScreen(loc)
                ActivityOptions.makeScaleUpAnimation(
                    view, bounds.left - loc[0], bounds.top - loc[1], bounds.width(), bounds.height(),
                ).toBundle()
            } else null
            launcherApps.startMainActivity(app.component, app.user, bounds, opts)
        } catch (e: Exception) {
            Toast.makeText(context, "App konnte nicht gestartet werden", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAppInfo(app: AppInfo) {
        try {
            launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
        } catch (_: Exception) {
        }
    }

    fun uninstall(context: Context, app: AppInfo) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            openAppInfo(app)
        }
    }

    fun shortcutIcon(s: ShortcutInfo): ImageBitmap? = try {
        launcherApps.getShortcutIconDrawable(s, ctx.resources.displayMetrics.densityDpi)?.toBitmap(96)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }

    fun shortcuts(app: AppInfo): List<ShortcutInfo> = try {
        if (!launcherApps.hasShortcutHostPermission()) emptyList()
        else {
            val q = LauncherApps.ShortcutQuery()
                .setPackage(app.packageName)
                .setActivity(app.component)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                )
            (launcherApps.getShortcuts(q, app.user) ?: emptyList())
                .filter { it.isEnabled }
                .sortedBy { it.rank }
                .take(4)
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun startShortcut(context: Context, s: ShortcutInfo) {
        try {
            launcherApps.startShortcut(s, null, null)
        } catch (e: Exception) {
            Toast.makeText(context, "Verknüpfung konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show()
        }
    }

    /** Sucht beim ersten Start sinnvolle Apps für das Dock (Telefon, SMS, Browser, Kamera). */
    fun defaultDock(context: Context, apps: List<AppInfo>): List<String> {
        val pm = context.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
        val own = Process.myUserHandle()
        return intents.mapNotNull { intent ->
            val pkg = try {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            } catch (e: Exception) {
                null
            } ?: return@mapNotNull null
            apps.firstOrNull { it.packageName == pkg && it.user == own }?.key
        }.distinct()
    }
}
