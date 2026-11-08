package com.notimorph.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.notimorph.app.core.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF5443CC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5DEFF),
    onPrimaryContainer = Color(0xFF170064),
    secondary = Color(0xFF5D5B72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2DFF9),
    onSecondaryContainer = Color(0xFF191A2C),
    tertiary = Color(0xFF795369),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainer = Color(0xFFF1EEF9),
    surfaceContainerHigh = Color(0xFFEBE8F4),
    outline = Color(0xFF777680),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC5B9FF),
    onPrimary = Color(0xFF2700A8),
    primaryContainer = Color(0xFF3E2DBB),
    onPrimaryContainer = Color(0xFFE5DEFF),
    secondary = Color(0xFFC6C3DD),
    onSecondary = Color(0xFF2E2E42),
    secondaryContainer = Color(0xFF454459),
    onSecondaryContainer = Color(0xFFE2DFF9),
    tertiary = Color(0xFFE9B9D4),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceContainer = Color(0xFF1E1F24),
    surfaceContainerHigh = Color(0xFF292A2F),
    outline = Color(0xFF91909A),
    error = Color(0xFFFFB4AB),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun NotiMorphTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        shapes = AppShapes,
        content = content,
    )
}
