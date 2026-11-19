package cn.pylin.xiaoyutyper.ui.keyboard

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class KeyboardColors(
    val surface: Color,
    val surfaceMuted: Color,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val primary: Color,
    val onPrimary: Color,
    val border: Color,
    val borderStrong: Color,
    val shadow: Color,
)

private val LightKeyboardColors = KeyboardColors(
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF5F7FA),
    text = Color(0xFF182838),
    textSecondary = Color(0xFF586777),
    textMuted = Color(0xFF6C7B89),
    primary = Color(0xFF1F446B),
    onPrimary = Color(0xFFFFFFFF),
    border = Color(0xFFE0E6ED),
    borderStrong = Color(0xFFCCD5DF),
    shadow = Color(0x24112031),
)

private val DarkKeyboardColors = KeyboardColors(
    surface = Color(0xFF141A21),
    surfaceMuted = Color(0xFF202831),
    text = Color(0xFFE7EDF3),
    textSecondary = Color(0xFFB3BFCA),
    textMuted = Color(0xFF8C99A7),
    primary = Color(0xFF8FB7E9),
    onPrimary = Color(0xFF0D1620),
    border = Color(0xFF2C3742),
    borderStrong = Color(0xFF46525F),
    shadow = Color(0x66000000),
)

private val LightColorScheme = lightColorScheme(
    primary = LightKeyboardColors.primary,
    onPrimary = LightKeyboardColors.onPrimary,
    background = LightKeyboardColors.surface,
    onBackground = LightKeyboardColors.text,
    surface = LightKeyboardColors.surface,
    onSurface = LightKeyboardColors.text,
    outline = LightKeyboardColors.textMuted,
    outlineVariant = LightKeyboardColors.border,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkKeyboardColors.primary,
    onPrimary = DarkKeyboardColors.onPrimary,
    background = DarkKeyboardColors.surface,
    onBackground = DarkKeyboardColors.text,
    surface = DarkKeyboardColors.surface,
    onSurface = DarkKeyboardColors.text,
    outline = DarkKeyboardColors.textMuted,
    outlineVariant = DarkKeyboardColors.border,
)

private val LocalKeyboardColors = staticCompositionLocalOf { LightKeyboardColors }

object XiaoyuTheme {
    val colors: KeyboardColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKeyboardColors.current
}

@Composable
fun XiaoyuTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColorScheme else LightColorScheme,
    ) {
        CompositionLocalProvider(
            LocalKeyboardColors provides if (dark) DarkKeyboardColors else LightKeyboardColors,
            content = content,
        )
    }
}

val KeyboardKeyShape = RoundedCornerShape(6.dp)

enum class KeyboardKeyKind { Letter, Function }
