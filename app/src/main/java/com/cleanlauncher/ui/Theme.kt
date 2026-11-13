package com.cleanlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlauncher.R
import com.cleanlauncher.data.Prefs
import com.cleanlauncher.data.WallpaperRepo

object Ios {
    val red = Color(0xFFFF3B30)
    val blue = Color(0xFF0A84FF)
    val orange = Color(0xFFFF9500)
    val green = Color(0xFF34C759)
    val gray = Color(0xFF8E8E93)
}

/** Inter – sehr ähnlich zur iOS-Systemschrift (SF Pro). */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

fun appFont(prefs: Prefs): FontFamily = if (prefs.iosFont) InterFamily else FontFamily.Default

private fun Typography.withFont(f: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

@Composable
fun LauncherTheme(prefs: Prefs, dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(primary = Ios.blue) else lightColorScheme(primary = Color(0xFF007AFF)),
        typography = Typography().withFont(appFont(prefs)),
        content = content,
    )
}

/**
 * iOS-Glaseffekt: zeichnet den passenden Ausschnitt des weichgezeichneten Hintergrundbilds
 * hinter das Element. Ohne Hintergrundbild wird [fallback] verwendet.
 */
fun Modifier.frosted(shape: Shape, tint: Color, fallback: Color, enabled: Boolean = true): Modifier = composed {
    val blur = if (enabled) WallpaperRepo.blurred else null
    val root = LocalView.current.rootView
    val pos = remember { floatArrayOf(0f, 0f) }
    var tick by remember { mutableIntStateOf(0) }
    this
        .onGloballyPositioned {
            val p = it.positionInWindow()
            if (p.x != pos[0] || p.y != pos[1]) {
                pos[0] = p.x
                pos[1] = p.y
                tick++
            }
        }
        .clip(shape)
        .drawBehind {
            if (tick < 0) return@drawBehind
            if (blur != null && root.width > 0 && root.height > 0) {
                val sx = blur.width.toFloat() / root.width
                val sy = blur.height.toFloat() / root.height
                val srcX = (pos[0] * sx).toInt().coerceIn(0, blur.width - 1)
                val srcY = (pos[1] * sy).toInt().coerceIn(0, blur.height - 1)
                val srcW = (size.width * sx).toInt().coerceIn(1, blur.width - srcX)
                val srcH = (size.height * sy).toInt().coerceIn(1, blur.height - srcY)
                drawImage(
                    blur,
                    srcOffset = IntOffset(srcX, srcY),
                    srcSize = IntSize(srcW, srcH),
                    dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.High,
                )
                drawRect(tint)
            } else {
                drawRect(fallback)
            }
        }
}

/** Vollflächig weichgezeichneter Hintergrund (App-Mediathek, Ordner, Menüs). */
@Composable
fun BlurredBackdrop(dim: Float, modifier: Modifier = Modifier) {
    val blur = WallpaperRepo.blurred
    Box(modifier.fillMaxSize()) {
        if (blur != null) {
            Image(
                blur, null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.High,
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (dim + 0.45f).coerceAtMost(0.85f))))
        }
    }
}

/** Rotes iOS-Badge mit Anzahl. */
@Composable
fun Badge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            .clip(RoundedCornerShape(50))
            .background(Ios.red)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
