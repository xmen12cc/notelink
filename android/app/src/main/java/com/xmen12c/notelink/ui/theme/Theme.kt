package com.xmen12c.notelink.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF4B5AC9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE1FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5B5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF77536D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F1),
    onTertiaryContainer = Color(0xFF2D1228),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF16256E),
    primaryContainer = Color(0xFF303F96),
    onPrimaryContainer = Color(0xFFDEE1FF),
    secondary = Color(0xFFC4C5DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434559),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFE6BAD7),
    onTertiary = Color(0xFF44263E),
    tertiaryContainer = Color(0xFF5D3C55),
    onTertiaryContainer = Color(0xFFFFD7F1),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
)

/** Card background + content color per note color, tuned for both themes. */
data class NotePalette(val container: Color, val onContainer: Color)

@Composable
fun notePalette(color: String): NotePalette {
    val dark = isSystemInDarkTheme()
    return when (color) {
        "yellow" -> NotePalette(Color(0xFFF7E8A6), Color(0xFF3E3413)).takeIf { !dark }
            ?: NotePalette(Color(0xFF453B18), Color(0xFFEAE2C0))
        "green" -> NotePalette(Color(0xFFCDE8C6), Color(0xFF24391F)).takeIf { !dark }
            ?: NotePalette(Color(0xFF2B4026), Color(0xFFD3E8CC))
        "blue" -> NotePalette(Color(0xFFC9E0F8), Color(0xFF1C3144)).takeIf { !dark }
            ?: NotePalette(Color(0xFF21344A), Color(0xFFCCE2F8))
        "pink" -> NotePalette(Color(0xFFF6CFDC), Color(0xFF40222C)).takeIf { !dark }
            ?: NotePalette(Color(0xFF4A2833), Color(0xFFF7D4E0))
        "gray" -> NotePalette(Color(0xFFE3E1DA), Color(0xFF33322C)).takeIf { !dark }
            ?: NotePalette(Color(0xFF35342F), Color(0xFFE5E3DC))
        else -> NotePalette(Color(0xFFEAE7F2), Color(0xFF1D1C22)).takeIf { !dark }
            ?: NotePalette(Color(0xFF2A2930), Color(0xFFE6E3EC))
    }
}

@Composable
fun NoteLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
