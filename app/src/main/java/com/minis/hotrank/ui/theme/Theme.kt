package com.minis.hotrank.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** 热榜主色：偏橙的红，比纯红更有"热度"感。 */
val HeatRed = Color(0xFFD7263D)
val HeatOrange = Color(0xFFF46036)
val HeatAmber = Color(0xFFFFB300)

val GoldMedal = Color(0xFFE8A33D)
val SilverMedal = Color(0xFF9AA5B1)
val BronzeMedal = Color(0xFFB08154)

/** 顶部头部区的渐变，深红 -> 橙，三档过渡避免出现色带。 */
val HeaderGradient = Brush.linearGradient(
    listOf(Color(0xFF9E1B32), Color(0xFFD7263D), Color(0xFFF46036))
)

/** 综合热度条的渐变。 */
val HeatBarGradient = Brush.horizontalGradient(
    listOf(Color(0xFFF46036), Color(0xFFD7263D))
)

/** 「N 站同榜」徽标的渐变 —— 这是全 App 最该被注意到的信息。 */
val CorroborationGradient = Brush.horizontalGradient(
    listOf(Color(0xFFF46036), Color(0xFFD7263D))
)

private val LightColors = lightColorScheme(
    primary = HeatRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE1E4),
    onPrimaryContainer = Color(0xFF40000A),
    secondary = HeatOrange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E7F3),
    onSecondaryContainer = Color(0xFF1E1A1F),
    background = Color(0xFFF7F5F7),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFEDEAEF),
    onSurfaceVariant = Color(0xFF6B6A70),
    outline = Color(0xFFD9D5DC),
    outlineVariant = Color(0xFFE8E4EB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8A80),
    onPrimary = Color(0xFF5C0011),
    primaryContainer = Color(0xFF8B0A21),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFFFB59B),
    onSecondary = Color(0xFF5A1B00),
    secondaryContainer = Color(0xFF2B2A31),
    onSecondaryContainer = Color(0xFFEAE1E9),
    background = Color(0xFF121013),
    onBackground = Color(0xFFE9E5EA),
    surface = Color(0xFF1C1A1E),
    onSurface = Color(0xFFE9E5EA),
    surfaceVariant = Color(0xFF2A272C),
    onSurfaceVariant = Color(0xFFA9A4AC),
    outline = Color(0xFF3A3640),
    outlineVariant = Color(0xFF2C2930),
)

/**
 * dynamicColor 默认关闭：开了之后系统会按壁纸取色，品牌红被覆盖，
 * 头部渐变的视觉一致性就没了。
 */
@Composable
fun HotRankTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
