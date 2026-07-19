package com.tornier.prrcn.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

/**
 * Material You theme whose whole colour scheme is generated at runtime from [seedColor]
 * (the dominant colour of the media the user last viewed). The seed animates so switching
 * between items feels like a smooth colour wash rather than a hard cut.
 */
@Composable
fun PrrcnTheme(
    seedColor: Color,
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val animatedSeed by animateColorAsState(
        targetValue = seedColor,
        animationSpec = tween(durationMillis = 600),
        label = "seed"
    )
    val colorScheme = rememberDynamicColorScheme(
        seedColor = animatedSeed,
        isDark = darkTheme,
        style = PaletteStyle.Expressive
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PrrcnTypography,
        content = content
    )
}
