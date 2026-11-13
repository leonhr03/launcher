package com.cleanlauncher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Hält das Hintergrundbild (scharf + stark weichgezeichnet) für Glas-Effekte und den Sperrbildschirm.
 * Quelle: vom Launcher gesetztes Bild oder – mit Dateizugriff – das aktuelle System-Hintergrundbild.
 */
object WallpaperRepo {

    var home by mutableStateOf<ImageBitmap?>(null)
        private set
    var blurred by mutableStateOf<ImageBitmap?>(null)
        private set
    var lock by mutableStateOf<ImageBitmap?>(null)
        private set

    private lateinit var ctx: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val homeFile get() = File(ctx.filesDir, "wallpaper_home.jpg")
    private val lockFile get() = File(ctx.filesDir, "wallpaper_lock.jpg")

    fun init(context: Context) {
        if (::ctx.isInitialized) return
        ctx = context.applicationContext
        reload()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                WallpaperManager.getInstance(ctx).addOnColorsChangedListener(
                    { _, _ ->
                        // Hintergrund wurde außerhalb geändert -> eigene Kopie verwerfen, falls wir das System lesen dürfen
                        if (hasFileAccess()) {
                            homeFile.delete()
                            lockFile.delete()
                        }
                        reload()
                    },
                    Handler(Looper.getMainLooper()),
                )
            } catch (_: Exception) {
            }
        }
    }

    fun hasFileAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    val available: Boolean get() = home != null

    fun reload() {
        scope.launch {
            val (h, b, l) = withContext(Dispatchers.IO) { loadAll() }
            home = h
            blurred = b
            lock = l
        }
    }

    private fun screenSize(): Pair<Int, Int> {
        val wm = ctx.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }

    private fun loadAll(): Triple<ImageBitmap?, ImageBitmap?, ImageBitmap?> {
        val homeBmp = (if (homeFile.exists()) BitmapFactory.decodeFile(homeFile.path) else null) ?: systemWallpaper(false)
        val lockBmp = (if (lockFile.exists()) BitmapFactory.decodeFile(lockFile.path) else null)
            ?: systemWallpaper(true) ?: homeBmp
        val (w, h) = screenSize()
        val homeCropped = homeBmp?.let { centerCrop(it, w, h) }
        return Triple(
            homeCropped?.asImageBitmap(),
            homeCropped?.let { blur(it).asImageBitmap() },
            lockBmp?.let { centerCrop(it, w, h) }?.asImageBitmap(),
        )
    }

    @Suppress("MissingPermission")
    private fun systemWallpaper(lockScreen: Boolean): Bitmap? = try {
        val wm = WallpaperManager.getInstance(ctx)
        if (lockScreen) {
            wm.getWallpaperFile(WallpaperManager.FLAG_LOCK)?.use { pfd ->
                BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
            }
        } else {
            wm.drawable?.toBitmapSafe()
        }
    } catch (e: Throwable) {
        null
    }

    private fun Drawable.toBitmapSafe(): Bitmap? {
        if (this is BitmapDrawable && bitmap != null) return bitmap
        val w = intrinsicWidth.takeIf { it > 0 } ?: return null
        val h = intrinsicHeight.takeIf { it > 0 } ?: return null
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, w, h)
        draw(Canvas(b))
        return b
    }

    private fun centerCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        // Für Speicher: auf max. halbe Bildschirmauflösung verkleinern (reicht für Glas & Sperrbildschirm)
        val tw = (w / 2).coerceAtLeast(1)
        val th = (h / 2).coerceAtLeast(1)
        val scale = maxOf(tw.toFloat() / src.width, th.toFloat() / src.height)
        val sw = (tw / scale).toInt().coerceAtMost(src.width)
        val sh = (th / scale).toInt().coerceAtMost(src.height)
        val sx = (src.width - sw) / 2
        val sy = (src.height - sh) / 2
        val out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, Rect(sx, sy, sx + sw, sy + sh), Rect(0, 0, tw, th), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** Sehr starke, schnelle Unschärfe: verkleinern + mehrfacher Box-Blur. */
    fun blur(src: Bitmap): Bitmap {
        val w = (src.width / 10).coerceAtLeast(8)
        val h = (src.height / 10).coerceAtLeast(8)
        val small = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) {
            boxBlurH(px, w, h, 3)
            boxBlurV(px, w, h, 3)
        }
        small.setPixels(px, 0, w, 0, 0, w, h)
        return small
    }

    private fun boxBlurH(px: IntArray, w: Int, h: Int, r: Int) {
        val out = IntArray(w)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                for (k in -r..r) {
                    val xx = (x + k).coerceIn(0, w - 1)
                    val c = px[row + xx]
                    a += c ushr 24; rr += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                }
                out[x] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
            System.arraycopy(out, 0, px, row, w)
        }
    }

    private fun boxBlurV(px: IntArray, w: Int, h: Int, r: Int) {
        val out = IntArray(h)
        for (x in 0 until w) {
            for (y in 0 until h) {
                var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                for (k in -r..r) {
                    val yy = (y + k).coerceIn(0, h - 1)
                    val c = px[yy * w + x]
                    a += c ushr 24; rr += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                }
                out[y] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
            for (y in 0 until h) px[y * w + x] = out[y]
        }
    }

    /** Bild aus der Galerie als Hintergrund setzen (Home, Sperrbildschirm oder beides). */
    suspend fun setFromUri(context: Context, uri: Uri, home: Boolean, lock: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@withContext false
            val (w, h) = screenSize()
            val scale = maxOf(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
            val sw = (w / scale).toInt().coerceAtMost(bmp.width)
            val sh = (h / scale).toInt().coerceAtMost(bmp.height)
            val sx = (bmp.width - sw) / 2
            val sy = (bmp.height - sh) / 2
            val full = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(full).drawBitmap(bmp, Rect(sx, sy, sx + sw, sy + sh), Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
            val wm = WallpaperManager.getInstance(context)
            var which = 0
            if (home) which = which or WallpaperManager.FLAG_SYSTEM
            if (lock) which = which or WallpaperManager.FLAG_LOCK
            // Erst eigene Kopie speichern (der Farb-Listener darf sie danach nicht löschen)
            if (home) homeFile.outputStream().use { full.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            if (lock) lockFile.outputStream().use { full.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            wm.setBitmap(full, null, true, which)
            withContext(Dispatchers.Main) { reload() }
            true
        } catch (e: Exception) {
            false
        }
    }
}
