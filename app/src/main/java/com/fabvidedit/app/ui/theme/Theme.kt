package com.fabvidedit.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

val FabBackground = Color(0xFF0B0910)
val FabSurface = Color(0xFF15121C)
val FabSurfaceHigh = Color(0xFF211C2B)
val FabPink = Color(0xFFFF4D76)
val FabPurple = Color(0xFF9F7AEA)
val FabMint = Color(0xFF31D6C4)
val FabText = Color(0xFFF7F3FC)
val FabMuted = Color(0xFFAAA2B5)

private val Colors = darkColorScheme(
    primary = FabPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5C172B),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = FabPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF352651),
    onSecondaryContainer = Color(0xFFEADDFF),
    tertiary = FabMint,
    background = FabBackground,
    onBackground = FabText,
    surface = FabSurface,
    onSurface = FabText,
    surfaceVariant = FabSurfaceHigh,
    onSurfaceVariant = FabMuted,
    outline = Color(0xFF5B5365),
    error = Color(0xFFFF6B6B),
)

@Composable
fun FabVidEditTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = FabBackground.toArgb()
        window.navigationBarColor = FabBackground.toArgb()
    }
    MaterialTheme(colorScheme = Colors, content = content)
}

