package com.cleanlauncher.lock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.cleanlauncher.R
import com.cleanlauncher.SettingsActivity
import com.cleanlauncher.data.Prefs

/**
 * Läuft im Hintergrund und zeigt beim Ausschalten des Bildschirms den iOS-Sperrbildschirm,
 * damit er beim Einschalten sofort da ist. Die echte Sperre (PIN/Fingerabdruck) bleibt beim System.
 */
class LockService : Service() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF && System.currentTimeMillis() - lastShown > 1500) showLock(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Sperrbildschirm", NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                    description = "Hält den iOS-Sperrbildschirm aktiv"
                },
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_lock_small)
            .setContentTitle("iOS-Sperrbildschirm aktiv")
            .setContentText("Tippen für Einstellungen")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        runCatching {
            getSystemService(android.hardware.display.DisplayManager::class.java)
                .registerDisplayListener(displayListener, android.os.Handler(android.os.Looper.getMainLooper()))
        }
    }

    /** Reagiert sofort beim Ausschalten (SCREEN_OFF kommt oft verspätet). */
    private var lastShown = 0L
    private val displayListener = object : android.hardware.display.DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != android.view.Display.DEFAULT_DISPLAY) return
            val st = getSystemService(android.hardware.display.DisplayManager::class.java)?.getDisplay(displayId)?.state ?: return
            if (st == android.view.Display.STATE_OFF || st == android.view.Display.STATE_DOZE) {
                val t = System.currentTimeMillis()
                if (t - lastShown > 1500) {
                    lastShown = t
                    showLock(this@LockService)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.get(this).lockEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Direkt starten: Ist das Handy schon gesperrt (z. B. nach dem Neustart), gleich anzeigen
        val km = getSystemService(android.app.KeyguardManager::class.java)
        if (km.isKeyguardLocked) showLock(this)
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        runCatching { getSystemService(android.hardware.display.DisplayManager::class.java).unregisterDisplayListener(displayListener) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "lockscreen"

        fun showLock(context: Context) {
            if (!Prefs.get(context).lockEnabled) return
            // Mit aktiver Bedienungshilfe übernimmt die (schnellere) Overlay-Variante
            if (com.cleanlauncher.system.LauncherAccessibilityService.instance?.overlays != null) return
            // Nicht während eines Anrufs (Näherungssensor schaltet den Bildschirm aus)
            val am = context.getSystemService(AudioManager::class.java)
            if (am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION) return
            try {
                context.startActivity(
                    Intent(context, LockScreenActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                    ),
                )
            } catch (_: Exception) {
            }
        }

        fun sync(context: Context) {
            val i = Intent(context, LockService::class.java)
            if (Prefs.get(context).lockEnabled) {
                try {
                    ContextCompat.startForegroundService(context, i)
                } catch (_: Exception) {
                }
            } else {
                context.stopService(i)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            LockService.sync(context)
        }
    }
}
