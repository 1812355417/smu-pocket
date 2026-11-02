package com.smu.pocket.ui.theme

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

// 南医大主色（偏医用的深青绿）。正式配色等你说要不要用校徽色再调。
private val LightColors = lightColorScheme(
    primary = Color(0xFF0E7C66),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA8F2DA),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4A635C),
    background = Color(0xFFF7FBF8),
    surface = Color(0xFFF7FBF8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD6C0),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005144),
    onPrimaryContainer = Color(0xFFA8F2DA),
    secondary = Color(0xFFB1CCC3),
    background = Color(0xFF0E1513),
    surface = Color(0xFF0E1513),
)

@Composable
fun SmuPocketTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,   // Android 12+ 跟随壁纸取色；不喜欢就设 false
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
