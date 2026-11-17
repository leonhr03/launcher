package com.cleanlauncher.system

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Aktuell laufende Musik/Medien (für Dynamic Island, Kontrollzentrum und Sperrbildschirm). */
object MediaRepo {
    var title by mutableStateOf<String?>(null)
        private set
    var artist by mutableStateOf<String?>(null)
        private set
    var art by mutableStateOf<ImageBitmap?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var pkg by mutableStateOf<String?>(null)
        private set

    val active: Boolean get() = title != null

    private var msm: MediaSessionManager? = null
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = update()
        override fun onPlaybackStateChanged(state: PlaybackState?) = update()
        override fun onSessionDestroyed() {
            controller = null
            refresh()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list ?: emptyList()) }

    fun init(context: Context) {
        if (msm != null) return
        if (!NotificationRepo.hasAccess(context)) return
        ctxRef = context.applicationContext
        try {
            val m = context.applicationContext.getSystemService(MediaSessionManager::class.java)
            val cn = NotificationService.component(context)
            m.addOnActiveSessionsChangedListener(sessionsListener, cn, handler)
            msm = m
            pick(m.getActiveSessions(cn))
        } catch (e: Exception) {
            msm = null
        }
    }

    private var ctxRef: Context? = null

    private fun refresh() {
        val m = msm ?: return
        try {
            val cn = ctxRef?.let { NotificationService.component(it) } ?: return
            pick(m.getActiveSessions(cn))
        } catch (_: Exception) {
        }
    }

    private fun pick(list: List<MediaController>) {
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback, handler)
        }
        update()
    }

    private fun update() {
        val c = controller
        val md = c?.metadata
        if (c == null || md == null) {
            title = null; artist = null; art = null; playing = false; pkg = null
            return
        }
        title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val bmp: Bitmap? = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        art = bmp?.let { runCatching { it.asImageBitmap() }.getOrNull() }
        playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        pkg = c.packageName
    }

    fun playPause() {
        val c = controller ?: return
        if (playing) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()

    fun open() {
        try {
            controller?.sessionActivity?.send()
        } catch (_: Exception) {
        }
    }
}
