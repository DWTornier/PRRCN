package com.tornier.prrcn.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * Acrylic / frosted-glass backdrop.
 *
 * The blurred first frame of the last-viewed media is drawn edge to edge, then washed with
 * a luminance-mapped gradient of the dominant [seedColor] and a theme-aware scrim so that
 * foreground content stays readable. When no media has been viewed yet it degrades to a
 * pure seed-colour gradient.
 */
@Composable
fun AcrylicBackground(
    bitmap: Bitmap?,
    seedColor: Color,
    darkTheme: Boolean,
    surfaceColor: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(surfaceColor)) {
        Crossfade(
            targetState = bitmap,
            animationSpec = tween(700),
            label = "acrylicFrame"
        ) { frame ->
            if (frame != null) {
                Image(
                    bitmap = frame.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(48.dp)
                )
            }
        }

        // Luminance-mapped dominant-colour wash: lighter tint near the top, deeper toward
        // the bottom, emulating light passing through frosted acrylic.
        val light = seedColor.copy(alpha = if (darkTheme) 0.42f else 0.34f)
        val deep = shiftLuminance(seedColor, if (darkTheme) -0.35f else 0.25f)
            .copy(alpha = if (darkTheme) 0.62f else 0.5f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(light, deep)))
        )

        // Readability scrim that also fades the very bottom into the app surface.
        val scrimTop = if (darkTheme) Color.Black.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.30f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to scrimTop,
                        0.5f to Color.Transparent,
                        1f to surfaceColor.copy(alpha = 0.72f)
                    )
                )
        )
    }
}

/** Push a colour toward black (negative amount) or white (positive amount) by [amount]. */
private fun shiftLuminance(color: Color, amount: Float): Color {
    val target = if (amount < 0f) Color.Black else Color.White
    val f = kotlin.math.abs(amount).coerceIn(0f, 1f)
    return Color(
        red = color.red + (target.red - color.red) * f,
        green = color.green + (target.green - color.green) * f,
        blue = color.blue + (target.blue - color.blue) * f,
        alpha = color.alpha
    )
}
