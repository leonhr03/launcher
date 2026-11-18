package com.cleanlauncher.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import com.cleanlauncher.SettingsActivity
import com.cleanlauncher.data.AppRepository
import com.cleanlauncher.system.NotificationRepo
import com.cleanlauncher.system.SystemActions

/**
 * Unsichtbare Brücke für den Overlay-Sperrbildschirm: Nach dem Hochwischen fragt das System
 * sofort nach PIN/Fingerabdruck (statt erst den System-Sperrbildschirm zu zeigen) – wie Face ID.
 * Danach wird optional eine Aktion ausgeführt (Mitteilung öffnen, Einstellungen …).
 */
class UnlockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppRepository.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        val km = getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = done(success = true)
                override fun onDismissCancelled() = done(success = false)
                override fun onDismissError() = done(success = false)
            })
        } else {
            done(success = true)
        }
    }

    private fun done(success: Boolean) {
        if (success) {
            runAction(this, intent)
        } else if (intent.getBooleanExtra(EXTRA_RESHOW, true)) {
            // Abgebrochen -> wieder unser Sperrbildschirm statt dem des Systems
            SystemActions.showLockOverlay()
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val EXTRA_ACTION = "action"
        private const val EXTRA_KEY = "key"
        private const val EXTRA_RESHOW = "reshow"
        const val ACTION_NONE = "none"
        const val ACTION_NOTIF = "notif"
        const val ACTION_SETTINGS = "settings"
        const val ACTION_APP = "app"

        fun start(context: Context, action: String = ACTION_NONE, key: String? = null) {
            try {
                context.startActivity(
                    Intent(context, UnlockActivity::class.java)
                        .putExtra(EXTRA_ACTION, action)
                        .putExtra(EXTRA_KEY, key)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                        ),
                )
            } catch (_: Exception) {
            }
        }

        /** Führt die gewünschte Aktion nach dem Entsperren aus (auch von der Activity-Variante genutzt). */
        fun runAction(context: Context, intent: Intent?) {
            val key = intent?.getStringExtra(EXTRA_KEY)
            when (intent?.getStringExtra(EXTRA_ACTION)) {
                ACTION_NOTIF -> {
                    val n = NotificationRepo.list.firstOrNull { it.key == key } ?: return
                    try {
                        n.contentIntent?.send() ?: throw IllegalStateException()
                    } catch (_: Exception) {
                        AppRepository.appFor(n.pkg)?.let { AppRepository.launch(context, it, null, null, false) }
                    }
                }
                ACTION_SETTINGS -> try {
                    context.startActivity(Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                }
                ACTION_APP -> {
                    val i = key?.let { context.packageManager.getLaunchIntentForPackage(it) } ?: return
                    try {
                        context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                    }
                }
            }
        }

        fun actionIntent(action: String, key: String? = null): Intent =
            Intent().putExtra(EXTRA_ACTION, action).putExtra(EXTRA_KEY, key)
    }
}
