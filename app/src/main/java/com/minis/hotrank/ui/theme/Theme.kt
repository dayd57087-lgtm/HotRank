package com.minis.hotrank.ui.theme

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

/**
 * 编辑杂志风配色（方案 B）。
 *
 * 设计约束：**全屏只有一处彩色**。
 * 主色是一支偏暗的红，只用在第 1 名的名次、综合热度数字上；
 * 其余一切（标题、名次、分割线、平台名）都是黑 / 灰 / 暖纸底的灰阶关系。
 * 平台品牌色只在详情页的小圆点上出现一次，且面积极小 —— 列表里的"彩虹打架"就是这么消掉的。
 */

val HeatRed = Color(0xFFC4342A)

/** 暖纸底，比纯白柔和，长时间看不刺眼。 */
val Paper = Color(0xFFFBF9F5)
val PaperDark = Color(0xFF141210)

private val LightColors = lightColorScheme(
    primary = HeatRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF2E5E3),
    onPrimaryContainer = Color(0xFF4A0F0A),
    secondary = Color(0xFF6B6259),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFEAE2),
    onSecondaryContainer = Color(0xFF2A241D),
    background = Paper,
    onBackground = Color(0xFF141210),
    surface = Paper,
    onSurface = Color(0xFF141210),
    surfaceVariant = Color(0xFFF2EDE4),
    onSurfaceVariant = Color(0xFF8C8378),
    outline = Color(0xFFC9C0B2),
    outlineVariant = Color(0xFFE5DED2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8604F),
    onPrimary = Color(0xFF2A0703),
    primaryContainer = Color(0xFF6B1A12),
    onPrimaryContainer = Color(0xFFFFDAD3),
    secondary = Color(0xFFB3A899),
    onSecondary = Color(0xFF241F19),
    secondaryContainer = Color(0xFF26221C),
    onSecondaryContainer = Color(0xFFEDE5D9),
    background = PaperDark,
    onBackground = Color(0xFFF2EDE4),
    surface = PaperDark,
    onSurface = Color(0xFFF2EDE4),
    surfaceVariant = Color(0xFF211D18),
    onSurfaceVariant = Color(0xFF9C9385),
    outline = Color(0xFF4A4239),
    outlineVariant = Color(0xFF2E2A24),
)

/** dynamicColor 恒为关：B 方案的全部气质都建立在"只有一处彩色"上，被系统取色就毁了。 */
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
