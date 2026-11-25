package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MuvantaDarkColorScheme = darkColorScheme(
    primary = WarmBrass,
    onPrimary = ObsidianBg,
    primaryContainer = ElevatedSurface,
    onPrimaryContainer = IvoryInk,
    secondary = WatchedMoss,
    onSecondary = ObsidianBg,
    secondaryContainer = ArchivalSurface,
    onSecondaryContainer = IvoryInk,
    tertiary = WarningAmber,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = IvoryInk,
    surface = ArchivalSurface,
    onSurface = IvoryInk,
    surfaceVariant = ElevatedSurface,
    onSurfaceVariant = SecondaryInk,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ArchivalSurface,
    surfaceContainer = ArchivalSurface,
    surfaceContainerHigh = ElevatedSurface,
    surfaceContainerHighest = ElevatedSurface,
    outline = HairlineBorder,
    outlineVariant = SprocketDark,
    error = ErrorTerracotta,
    onError = IvoryInk,
    errorContainer = ErrorTerracottaSoft,
    onErrorContainer = ErrorRoseInk,
)

private val MuvantaShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun MuvantaTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MuvantaDarkColorScheme,
        typography = MuvantaTypography,
        shapes = MuvantaShapes,
        content = content,
    )
}
