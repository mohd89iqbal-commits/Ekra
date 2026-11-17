package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = CrimsonPrimary,
    onPrimary = Color.White,
    primaryContainer = CrimsonContainer,
    onPrimaryContainer = OnCrimsonContainer,
    secondary = SaffronSecondary,
    onSecondary = Color.White,
    secondaryContainer = SaffronContainer,
    onSecondaryContainer = OnSaffronContainer,
    tertiary = BasilTertiary,
    onTertiary = Color.White,
    tertiaryContainer = BasilContainer,
    onTertiaryContainer = OnBasilContainer,
    background = ParchmentBg,
    onBackground = EspressoInk,
    surface = ParchmentSurface,
    onSurface = EspressoInk,
    surfaceVariant = WarmSurfaceContainer,
    onSurfaceVariant = RoastMuted,
    surfaceContainerLowest = ParchmentSurface,
    surfaceContainerLow = WarmSurfaceLow,
    surfaceContainer = WarmSurfaceContainer,
    surfaceContainerHigh = WarmSurfaceHigh,
    outline = WarmOutline,
    outlineVariant = WarmOutlineVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkCrimsonPrimary,
    onPrimary = Color(0xFF3E0403),
    primaryContainer = DarkCrimsonContainer,
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = DarkSaffron,
    onSecondary = Color(0xFF382000),
    secondaryContainer = Color(0xFF523100),
    onSecondaryContainer = Color(0xFFFFE1B3),
    tertiary = DarkBasil,
    onTertiary = Color(0xFF003819),
    tertiaryContainer = Color(0xFF0C522B),
    onTertiaryContainer = Color(0xFFBDF6D2),
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = DarkMuted,
    surfaceContainerLowest = DarkBg,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceHigh,
    outline = DarkOutline,
    outlineVariant = Color(0xFF372C28)
)

val BiteLocalShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BiteLocalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = BiteLocalTypography,
        shapes = BiteLocalShapes,
        content = content
    )
}
