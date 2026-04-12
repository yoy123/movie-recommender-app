package com.movierecommender.app.ui.theme.firestick

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = MoviePrimary,
    secondary = MovieSecondary,
    tertiary = MovieTertiary,
    background = MovieBackground,
    surface = MovieSurface,
    surfaceVariant = MovieSurfaceVariant,
    primaryContainer = MoviePanel,
    onPrimary = MovieOnPrimary,
    onPrimaryContainer = MovieOnBackground,
    onBackground = MovieOnBackground,
    onSurface = MovieOnSurface,
    onSurfaceVariant = MovieOnSurfaceVariant,
    outline = MovieOutline
)

private val LightColorScheme = lightColorScheme(
    primary = MoviePrimary,
    secondary = MovieSecondary,
    tertiary = MovieTertiary,
    background = MovieOnBackground,
    surface = Color.White,
    surfaceVariant = Color(0xFFE7EEF7),
    primaryContainer = Color(0xFFCCF3F8),
    onPrimary = MovieOnPrimary,
    onPrimaryContainer = MovieBackground,
    onBackground = MovieBackground,
    onSurface = MovieBackground,
    onSurfaceVariant = Color(0xFF40506D),
    outline = Color(0xFF70819F)
)

@Composable
fun MovieRecommenderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surfaceVariant.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
