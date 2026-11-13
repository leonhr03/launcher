package com.cleanlauncher.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlauncher.data.AppInfo
import com.cleanlauncher.data.IconShape
import com.cleanlauncher.data.Prefs
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Echte iOS-ähnliche "Superellipse" (kontinuierliche Ecken). */
class SquircleShape(private val n: Double = 5.0) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val a = size.width / 2f
        val b = size.height / 2f
        val steps = 96
        for (i in 0..steps) {
            val t = 2 * Math.PI * i / steps
            val c = cos(t)
            val s = sin(t)
            val x = (abs(c).pow(2.0 / n) * sign(c)).toFloat() * a + a
            val y = (abs(s).pow(2.0 / n) * sign(s)).toFloat() * b + b
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

fun iconShapeOf(shape: IconShape, cornerPercent: Int): Shape = when (shape) {
    IconShape.ORIGINAL -> RectangleShape
    IconShape.SQUIRCLE -> SquircleShape()
    IconShape.ROUNDED -> RoundedCornerShape(percent = cornerPercent.coerceIn(0, 50))
    IconShape.CIRCLE -> CircleShape
    IconShape.SQUARE -> RoundedCornerShape(percent = 4)
    IconShape.TEARDROP -> RoundedCornerShape(50, 50, cornerPercent.coerceIn(0, 50), 50)
}

/** Nur das Bild des Symbols in der gewählten Form und Größe. */
@Composable
fun IconImage(app: AppInfo, prefs: Prefs, sizeDp: Int = prefs.iconSize, modifier: Modifier = Modifier) {
    val shapeType = prefs.iconShape
    val shape = remember(shapeType, prefs.iconCornerPercent) { iconShapeOf(shapeType, prefs.iconCornerPercent) }
    val bmp = if (shapeType != IconShape.ORIGINAL && app.fullBleed != null) app.fullBleed else app.icon
    var m = modifier.size(sizeDp.dp)
    if (prefs.iconShadow) m = m.shadow((sizeDp / 10).dp, if (shapeType == IconShape.ORIGINAL) CircleShape else shape, clip = false)
    if (shapeType != IconShape.ORIGINAL) m = m.clip(shape)
    Image(bitmap = bmp, contentDescription = null, modifier = m)
}

fun labelStyle(prefs: Prefs) = TextStyle(
    color = Color(prefs.labelColor),
    fontSize = prefs.labelSize.sp,
    fontFamily = appFont(prefs),
    fontWeight = if (prefs.labelBold) FontWeight.SemiBold else FontWeight.Normal,
    textAlign = TextAlign.Center,
    shadow = if (prefs.labelShadow) Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), 4f) else null,
)

/** iOS-Ordnersymbol: Glasfläche mit 3×3 Mini-Symbolen. */
@Composable
fun FolderIcon(apps: List<AppInfo>, prefs: Prefs, sizeDp: Int = prefs.iconSize) {
    val shape = iconShapeOf(
        if (prefs.iconShape == IconShape.ORIGINAL) IconShape.SQUIRCLE else prefs.iconShape,
        prefs.iconCornerPercent,
    )
    val mini = (sizeDp * 0.22f)
    val gap = (sizeDp * 0.06f)
    Box(
        Modifier
            .size(sizeDp.dp)
            .frosted(shape, Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.28f), prefs.glass),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(gap.dp)) {
            for (r in 0 until 3) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                    for (c in 0 until 3) {
                        val app = apps.getOrNull(r * 3 + c)
                        if (app != null) {
                            Image(
                                bitmap = if (prefs.iconShape != IconShape.ORIGINAL && app.fullBleed != null) app.fullBleed else app.icon,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(mini.dp)
                                    .clip(if (prefs.iconShape == IconShape.ORIGINAL) RectangleShape else RoundedCornerShape(24)),
                            )
                        } else {
                            Spacer(Modifier.size(mini.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Darstellung einer Zelle: Symbol (beliebiger Inhalt) + Name + Badge + Wackeln im Bearbeitungsmodus.
 * Gesten werden vom Aufrufer über [modifier] gesetzt.
 */
@Composable
fun IconCell(
    prefs: Prefs,
    label: String,
    showLabel: Boolean,
    editMode: Boolean,
    pressed: Boolean,
    hidden: Boolean,
    wobbleSeed: Int,
    badge: Int,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val scale by animateFloatAsState(
        if (pressed && prefs.pressAnimation && !editMode) 0.9f else 1f,
        animationSpec = tween(110),
        label = "press",
    )
    val rotation = if (editMode) {
        val t = rememberInfiniteTransition(label = "wobble")
        val r by t.animateFloat(
            initialValue = -1.5f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(tween(125 + (abs(wobbleSeed) % 4) * 10), RepeatMode.Reverse),
            label = "wobble",
        )
        if (wobbleSeed % 2 == 0) r else -r
    } else 0f

    Column(
        modifier.alpha(if (hidden) 0f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.rotate(rotation).scale(scale)) {
            icon()
            if (badge > 0 && prefs.badges && !editMode) {
                Badge(badge, Modifier.align(Alignment.TopEnd).offset(8.dp, (-6).dp))
            }
            if (editMode && onRemove != null) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset((-7).dp, (-7).dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .frosted(CircleShape, Color(0x99555555), Color(0xE6555555), prefs.glass)
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(10.dp, 2.dp).background(Color.White))
                }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = label,
                style = labelStyle(prefs),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = (prefs.iconSize + 26).dp)
                    .padding(horizontal = 2.dp),
            )
        }
    }
}
