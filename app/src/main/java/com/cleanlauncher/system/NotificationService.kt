package com.cleanlauncher.system

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat

data class Notif(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val time: Long,
    val contentIntent: PendingIntent?,
    val clearable: Boolean,
)

object NotificationRepo {
    /** Zuletzt eingetroffene (laute) Mitteilung – für die Dynamic Island. */
    var lastPosted by mutableStateOf<Notif?>(null)
        internal set
    var postedTick by mutableStateOf(0)
        internal set

    var list by mutableStateOf<List<Notif>>(emptyList())
        internal set
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())
        internal set

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun dismissAll() {
        try {
            NotificationService.instance?.cancelAllNotifications()
        } catch (_: Exception) {
        }
    }

    fun open(n: Notif) {
        try {
            n.contentIntent?.send()
        } catch (_: Exception) {
        }
    }

    fun dismiss(n: Notif) {
        try {
            NotificationService.instance?.cancelNotification(n.key)
        } catch (_: Exception) {
        }
    }
}

/** Liest Benachrichtigungen für App-Badges (rote Zahlen) und den Sperrbildschirm. */
class NotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        refresh()
        MediaRepo.init(this)
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val known = NotificationRepo.list.any { it.key == sbn?.key }
        refresh()
        if (sbn == null || known || sbn.isOngoing || sbn.packageName == packageName) return
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val important = try {
            val r = Ranking()
            currentRanking.getRanking(sbn.key, r) && r.importance >= android.app.NotificationManager.IMPORTANCE_DEFAULT
        } catch (e: Exception) {
            true
        }
        if (!important) return
        NotificationRepo.lastPosted = toNotif(sbn)
        NotificationRepo.postedTick++
    }

    private fun toNotif(sbn: StatusBarNotification): Notif {
        val ex = sbn.notification.extras
        return Notif(
            key = sbn.key,
            pkg = sbn.packageName,
            title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty(),
            time = sbn.postTime,
            contentIntent = sbn.notification.contentIntent,
            clearable = sbn.isClearable,
        )
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val all = try {
            activeNotifications?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val relevant = all.filter { sbn ->
            val n = sbn.notification
            (n.flags and Notification.FLAG_GROUP_SUMMARY) == 0 && sbn.packageName != packageName
        }
        NotificationRepo.counts = relevant.filter { !it.isOngoing }.groupingBy { it.packageName }.eachCount()
        NotificationRepo.list = relevant.filter { !it.isOngoing }.sortedByDescending { it.postTime }.map { toNotif(it) }
    }

    companion object {
        @Volatile
        var instance: NotificationService? = null

        fun component(context: Context) = ComponentName(context, NotificationService::class.java)
    }
}
