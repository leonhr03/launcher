package com.cleanlauncher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/** Widget-Ansicht, die einen langen Druck erkennt (für das Widget-Menü), ohne das Widget zu stören. */
class LauncherWidgetView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var longPressed = false

    private val longPressRunnable = Runnable {
        longPressed = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        cancelChildren()
        onLongPress?.invoke()
    }

    private fun cancelChildren() {
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressed = false
                downX = ev.x
                downY = ev.y
                handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) {
                handler.removeCallbacks(longPressRunnable)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPressRunnable)
        }
        if (longPressed) return true
        super.dispatchTouchEvent(ev)
        return true
    }
}

class LauncherWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        LauncherWidgetView(context)

    companion object {
        const val HOST_ID = 1024
    }
}

object Widgets {
    lateinit var host: LauncherWidgetHost
        private set
    lateinit var manager: AppWidgetManager
        private set

    val ready: Boolean get() = ::host.isInitialized

    fun init(context: Context) {
        if (ready) return
        val app = context.applicationContext
        host = LauncherWidgetHost(app)
        manager = AppWidgetManager.getInstance(app)
    }

    fun info(id: Int): AppWidgetProviderInfo? = try {
        manager.getAppWidgetInfo(id)
    } catch (e: Exception) {
        null
    }

    fun providers(): List<AppWidgetProviderInfo> = try {
        manager.installedProviders
    } catch (e: Exception) {
        emptyList()
    }

    fun delete(id: Int) {
        try {
            host.deleteAppWidgetId(id)
        } catch (_: Exception) {
        }
    }
}
