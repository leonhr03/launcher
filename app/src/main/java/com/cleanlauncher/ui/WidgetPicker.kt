package com.cleanlauncher.ui

import android.appwidget.AppWidgetProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlauncher.data.BuiltinWidget
import com.cleanlauncher.widgets.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface PickerRow {
    data class Header(val title: String) : PickerRow
    data class Builtin(val type: BuiltinWidget) : PickerRow
    data class Provider(val info: AppWidgetProviderInfo, val label: String) : PickerRow
}

/** Widget-Galerie wie bei iOS: eingebaute Widgets + alle Android-Widgets deiner Apps. */
@Composable
fun WidgetPicker(c: LauncherController) {
    val ctx = LocalContext.current
    val prefs = c.prefs
    BackHandler { c.widgetPicker = false }
    var query by remember { mutableStateOf("") }
    val all = remember {
        val pm = ctx.packageManager
        Widgets.providers().map { info ->
            val appLabel = try {
                pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
            } catch (e: Exception) {
                info.provider.packageName
            }
            appLabel to info
        }.groupBy({ it.first }, { it.second }).toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }
    val rows = remember(all, query) {
        buildList<PickerRow> {
            if (query.isBlank()) {
                add(PickerRow.Header("Clean Launcher"))
                BuiltinWidget.entries.forEach { add(PickerRow.Builtin(it)) }
            }
            all.forEach { (app, infos) ->
                val labels = infos.map { it to (it.loadLabel(ctx.packageManager) ?: app) }
                val match = query.isBlank() || app.contains(query, true) || labels.any { it.second.contains(query, true) }
                if (match) {
                    add(PickerRow.Header(app))
                    labels.forEach { (i, l) -> add(PickerRow.Provider(i, l)) }
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        BlurredBackdrop(0.45f)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Widgets", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { c.widgetPicker = false },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, "Schließen", tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
            SearchField(query, { query = it }, null, "Widgets suchen") {}
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rows) { row ->
                    when (row) {
                        is PickerRow.Header -> Text(
                            row.title,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 14.dp, start = 4.dp),
                        )
                        is PickerRow.Builtin -> PickerCard("${row.type.title}", "${row.type.spanX} × ${row.type.spanY}", { c.addBuiltin(row.type) }) {
                            Box(Modifier.size(if (row.type.spanX > row.type.spanY) 300.dp else 150.dp, 150.dp)) {
                                BuiltinWidgetView(row.type, prefs, Modifier.fillMaxSize())
                            }
                        }
                        is PickerRow.Provider -> {
                            val info = row.info
                            val preview by produceState<ImageBitmap?>(null, info) {
                                value = withContext(Dispatchers.IO) { loadPreview(ctx, info) }
                            }
                            val size = "${sizeGuess(info.minWidth, ctx)} × ${sizeGuess(info.minHeight, ctx)}"
                            PickerCard(row.label, size, { c.requestWidget?.invoke(info) }) {
                                val p = preview
                                if (p != null) {
                                    Image(
                                        p, null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.heightIn(max = 160.dp).fillMaxWidth(0.9f),
                                    )
                                } else {
                                    Spacer(Modifier.height(80.dp))
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

private fun sizeGuess(px: Int, ctx: android.content.Context): Int {
    val dp = px / ctx.resources.displayMetrics.density
    return ((dp + 30) / 80).toInt().coerceAtLeast(1)
}

private fun loadPreview(ctx: android.content.Context, info: AppWidgetProviderInfo): ImageBitmap? = try {
    val d = info.loadPreviewImage(ctx, 0) ?: info.loadIcon(ctx, 0)
    if (d == null) null else {
        val w = d.intrinsicWidth.takeIf { it > 0 } ?: 300
        val h = d.intrinsicHeight.takeIf { it > 0 } ?: 300
        val scale = minOf(1f, 700f / maxOf(w, h))
        val bw = (w * scale).toInt().coerceAtLeast(1)
        val bh = (h * scale).toInt().coerceAtLeast(1)
        val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, bw, bh)
        d.draw(Canvas(b))
        b.asImageBitmap()
    }
} catch (e: Exception) {
    null
}

@Composable
private fun PickerCard(title: String, size: String, onAdd: () -> Unit, preview: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onAdd)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        preview()
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(size, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) { Text("+ Hinzufügen", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.width(2.dp))
        }
    }
}
