package com.dc.murmur.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val MurmurDarkScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = Color(0xFF003731),
    primaryContainer = Teal40,
    onPrimaryContainer = Color(0xFFC8FFF7),
    secondary = DeepBlue80,
    onSecondary = Color(0xFF1A237E),
    secondaryContainer = DeepBlue40,
    onSecondaryContainer = Color(0xFFDBE1FF),
    tertiary = Amber80,
    onTertiary = Color(0xFF3E2E00),
    tertiaryContainer = Amber40,
    onTertiaryContainer = Color(0xFFFFECB3),
    background = DarkSurface,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVar,
    surfaceContainerLowest = Color(0xFF0A0E12),
    surfaceContainerLow = Color(0xFF0F1318),
    surfaceContainer = DarkSurfaceCard,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = Color(0xFF252C36),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF370B0B),
    errorContainer = Color(0xFF3D1414),
    onErrorContainer = Color(0xFFFFB4B4),
    outline = Color(0xFF3A4456),
    outlineVariant = Color(0xFF2A3140)
)

private val MurmurLightScheme = lightColorScheme(
    primary = Teal40,
    secondary = DeepBlue40,
    tertiary = Amber40
)

val MurmurShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun MurmurTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> MurmurDarkScheme
        else -> MurmurLightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MurmurTypography,
        shapes = MurmurShapes,
        content = content
    )
}
