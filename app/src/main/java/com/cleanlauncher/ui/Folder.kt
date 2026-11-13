package com.cleanlauncher.ui

import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlauncher.data.AppInfo
import com.cleanlauncher.data.Item
import com.cleanlauncher.data.ItemType
import com.cleanlauncher.system.NotificationRepo

/** Geöffneter Ordner (oder Kategorie aus der App-Mediathek) im iOS-Stil. */
@Composable
fun FolderOverlay(c: LauncherController) {
    val fid = c.openFolderId
    val cat = c.openCategory
    if (fid == null && cat == null) return
    val folder = fid?.let { c.ws.byId(it) }
    if (fid != null && (folder == null || folder.type != ItemType.FOLDER)) {
        LaunchedEffect(fid) { c.openFolderId = null }
        return
    }
    val prefs = c.prefs
    val title = folder?.title ?: cat!!.first
    val hidden = prefs.hiddenApps
    val apps: List<AppInfo> = if (folder != null) c.folderApps(folder)
    else cat!!.second.filter { it !in hidden }.mapNotNull { c.byKey[it] }
    val d = c.drag
    val draggingOut = d != null && d.active && d.source == DragSource.FOLDER
    fun close() {
        if (fid != null) c.openFolderId = null else c.openCategory = null
    }
    BackHandler { close() }
    val anim = remember(fid, cat) { Animatable(0f) }
    LaunchedEffect(fid, cat) { anim.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 420f)) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (draggingOut) 0f else anim.value.coerceIn(0f, 1f) }
            .pointerInput(fid, cat) { detectTapGestures { close() } },
    ) {
        BlurredBackdrop(0.22f)
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.86f)
                .graphicsLayer {
                    scaleX = 0.8f + 0.2f * anim.value
                    scaleY = scaleX
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(bottom = 18.dp)
                    .clickable(enabled = folder != null) {
                        folder?.let { c.rename = RenameTarget.Folder(it.id, it.title) }
                    },
            )
            val pages = apps.chunked(9).ifEmpty { listOf(emptyList()) }
            val pager = rememberPagerState { pages.size }
            Box(
                Modifier
                    .fillMaxWidth()
                    .frosted(RoundedCornerShape(38.dp), Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.18f), prefs.glass)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(vertical = 22.dp, horizontal = 6.dp),
            ) {
                HorizontalPager(state = pager, beyondViewportPageCount = if (draggingOut) pages.size else 1) { p ->
                    val list = pages[p]
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        for (r in 0 until 3) {
                            Row(Modifier.fillMaxWidth()) {
                                for (col in 0 until 3) {
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        list.getOrNull(r * 3 + col)?.let { app -> FolderAppCell(c, app, folder) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (pages.size > 1) {
                Row(Modifier.padding(top = 12.dp)) {
                    repeat(pages.size) { i ->
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (i == pager.currentPage) 0.95f else 0.35f)),
                        )
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun FolderAppCell(c: LauncherController, app: AppInfo, folder: Item?) {
    val bounds = remember { arrayOfNulls<Rect>(1) }
    val pressed = remember { mutableStateOf(false) }
    val dragItem = folder?.let { Item(-1, ItemType.APP, appKey = app.key) }
    val d = c.drag
    val dragging = d != null && d.active && d.source == DragSource.FOLDER && d.item.appKey == app.key
    IconCell(
        prefs = c.prefs,
        label = c.labelOf(app),
        showLabel = true,
        editMode = c.editMode && folder != null,
        pressed = pressed.value,
        hidden = dragging,
        wobbleSeed = app.key.hashCode(),
        badge = NotificationRepo.counts[app.packageName] ?: 0,
        onRemove = null,
        modifier = Modifier.iconGestures(
            c, "f:" + app.key, bounds, pressed, dragItem, DragSource.FOLDER, folder?.id,
            onTap = {
                if (!c.editMode || folder == null) {
                    c.launch(app, bounds[0])
                    c.openFolderId = null
                    c.openCategory = null
                    c.closeDrawer()
                }
            },
            onLongPress = { c.showMenu(MenuTarget.App(app, null, folder?.id, folder == null), bounds[0]) },
        ),
    ) { IconImage(app, c.prefs) }
}
