package io.github.cataylor5.ibs.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = IbsBrand.Cobalt, onPrimary = Color.White,
    primaryContainer = IbsBrand.PaleAqua, onPrimaryContainer = IbsBrand.Navy,
    secondary = IbsBrand.Navy, onSecondary = Color.White,
    secondaryContainer = IbsBrand.PaleAqua, onSecondaryContainer = IbsBrand.Navy,
    tertiary = IbsBrand.Aqua, onTertiary = IbsBrand.Navy,
    background = IbsBrand.Background, onBackground = IbsBrand.Navy,
    surface = Color.White, onSurface = IbsBrand.Navy,
    surfaceVariant = IbsBrand.Background, onSurfaceVariant = IbsBrand.Navy,
    surfaceContainer = IbsBrand.Background,
    outline = IbsBrand.Border, outlineVariant = IbsBrand.Border
)
private val NightPalette = ibsSurfacePalette(true)
private val DarkColorScheme = darkColorScheme(
    primary = IbsBrand.Aqua, onPrimary = IbsBrand.Navy,
    primaryContainer = Color(NightPalette.selected), onPrimaryContainer = IbsBrand.Aqua,
    secondary = IbsBrand.Aqua, onSecondary = IbsBrand.Navy,
    secondaryContainer = Color(NightPalette.selected), onSecondaryContainer = IbsBrand.Aqua,
    background = Color(NightPalette.background), onBackground = Color(NightPalette.text),
    surface = Color(NightPalette.surface), onSurface = Color(NightPalette.text),
    surfaceVariant = Color(NightPalette.surface), onSurfaceVariant = Color(NightPalette.text),
    surfaceContainer = Color(NightPalette.surface),
    outline = Color(NightPalette.border), outlineVariant = Color(NightPalette.border)
)

@Suppress("UNUSED_PARAMETER")
@Composable
fun IBSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Brand colors intentionally take precedence over wallpaper-derived dynamic colors.
    val context = LocalContext.current
    SideEffect { IbsAppearance.systemChanged(context, darkTheme) }
    val night = IbsAppearance.state.night ?: darkTheme
    val view = LocalView.current
    SideEffect {
        if (!view.isInEditMode) (context as? Activity)?.let {
            WindowCompat.getInsetsController(it.window, view).apply {
                isAppearanceLightStatusBars = !night
                isAppearanceLightNavigationBars = !night
            }
        }
    }
    MaterialTheme(colorScheme = if (night) DarkColorScheme else LightColorScheme,
        typography = Typography, content = content)
}
