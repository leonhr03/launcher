package com.cleanlauncher.ui

import android.app.SearchManager
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlauncher.data.AppInfo
import com.cleanlauncher.data.DrawerStyle
import kotlinx.coroutines.delay

private sealed interface DrawerRow {
    data class Header(val letter: String) : DrawerRow
    data class App(val app: AppInfo, val label: String) : DrawerRow
}

@Composable
fun AppDrawer(c: LauncherController, apps: List<AppInfo>) {
    val prefs = c.prefs
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun close() {
        keyboard?.hide()
        query = ""
        c.closeDrawer()
    }

    BackHandler { close() }

    LaunchedEffect(c.drawerFocus) {
        if (c.drawerFocus) {
            delay(120)
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }

    val hidden = prefs.hiddenApps.toSet()
    val labels = prefs.customLabels
    val visible = remember(apps, hidden, labels) {
        apps.filter { it.key !in hidden }
            .map { it to (labels[it.key] ?: it.originalLabel) }
            .sortedBy { it.second.lowercase() }
    }
    val filtered = remember(visible, query) {
        val q = query.trim()
        if (q.isEmpty()) visible
        else visible
            .filter { (app, label) -> label.contains(q, true) || app.packageName.contains(q, true) }
            .sortedBy { (_, label) -> if (label.startsWith(q, true)) 0 else 1 }
    }

    val launch: (AppInfo, Rect?) -> Unit = { app, bounds ->
        c.launch(app, bounds)
        close()
    }

    val density = LocalDensity.current
    val closeConnection = remember {
        object : NestedScrollConnection {
            var acc = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    acc += available.y
                    if (acc > with(density) { 110.dp.toPx() }) {
                        acc = 0f
                        close()
                    }
                } else if (consumed.y != 0f) {
                    acc = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                acc = 0f
                return Velocity.Zero
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = prefs.drawerOpacity))
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            // Griff zum Schließen (nach unten ziehen)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total > 40.dp.toPx()) close() },
                            onVerticalDrag = { ch, dy ->
                                total += dy
                                ch.consume()
                            },
                        )
                    }
                    .clickable { close() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(40.dp, 5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.45f)),
                )
            }

            SearchField(
                value = query,
                onValueChange = { query = it },
                focus = focus,
                onGo = { filtered.firstOrNull()?.let { launch(it.first, null) } },
            )

            val webSearch: (@Composable () -> Unit)? = if (prefs.drawerWebSearch && query.isNotBlank()) {
                {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val q = query
                                try {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_WEB_SEARCH)
                                            .putExtra(SearchManager.QUERY, q)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                } catch (e: Exception) {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                                close()
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Text("Im Web suchen: „$query“", color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else null

            if (prefs.drawerStyle == DrawerStyle.LIST) {
                val rows = remember(filtered, query) {
                    if (query.isNotBlank()) filtered.map { DrawerRow.App(it.first, it.second) }
                    else buildList {
                        var last: String? = null
                        for ((app, label) in filtered) {
                            val ch = label.firstOrNull()?.uppercaseChar()
                            val letter = if (ch != null && ch.isLetter()) ch.toString() else "#"
                            if (letter != last) {
                                add(DrawerRow.Header(letter))
                                last = letter
                            }
                            add(DrawerRow.App(app, label))
                        }
                    }
                }
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .nestedScroll(closeConnection),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(rows, key = {
                        when (it) {
                            is DrawerRow.Header -> "h:" + it.letter
                            is DrawerRow.App -> it.app.key
                        }
                    }) { row ->
                        when (row) {
                            is DrawerRow.Header -> Text(
                                row.letter,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 22.dp, top = 12.dp, bottom = 2.dp),
                            )
                            is DrawerRow.App -> DrawerListRow(c, row.app, row.label, launch)
                        }
                    }
                    if (webSearch != null) item(key = "web") { webSearch() }
                    item { Spacer(Modifier.navigationBarsPadding()) }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(prefs.columns),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .nestedScroll(closeConnection)
                        .padding(horizontal = prefs.gridPaddingH.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                ) {
                    items(filtered, key = { it.first.key }) { (app, _) ->
                        Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            val menuId = "DRAWER:${app.key}"
                            AppIcon(
                                app = app,
                                prefs = prefs,
                                showLabel = true,
                                editMode = false,
                                selected = false,
                                wobbleSeed = 0,
                                menuOpen = c.menuKey == menuId,
                                onDismissMenu = { c.menuKey = null },
                                onClick = { b -> launch(app, b) },
                                onLongClick = {
                                    c.haptic(true)
                                    c.menuKey = menuId
                                },
                                onRemoveBadge = null,
                            ) { AppMenuItems(c, app, IconLocation.DRAWER) }
                        }
                    }
                    if (webSearch != null) {
                        item(key = "web", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { webSearch() }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerListRow(c: LauncherController, app: AppInfo, label: String, launch: (AppInfo, Rect?) -> Unit) {
    val menuId = "DRAWER:${app.key}"
    val bounds = remember { arrayOfNulls<Rect>(1) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned {
                    val b = it.boundsInWindow()
                    bounds[0] = Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                }
                .combinedClickable(
                    onClick = { launch(app, bounds[0]) },
                    onLongClick = {
                        c.haptic(true)
                        c.menuKey = menuId
                    },
                )
                .padding(horizontal = 20.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconImage(app, c.prefs, sizeDp = 42)
            Spacer(Modifier.width(16.dp))
            Text(label, color = Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(
            expanded = c.menuKey == menuId,
            onDismissRequest = { c.menuKey = null },
            offset = androidx.compose.ui.unit.DpOffset(70.dp, 0.dp),
        ) {
            AppMenuItems(c, app, IconLocation.DRAWER)
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, focus: FocusRequester, onGo: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("App-Mediathek durchsuchen", color = Color.White.copy(alpha = 0.55f), fontSize = 17.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Filled.Clear,
                "Löschen",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onValueChange("") },
            )
        }
    }
}
