@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.minis.hotrank.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.minis.hotrank.data.PlatformLauncher
import com.minis.hotrank.model.DetailData
import com.minis.hotrank.model.HotItem
import com.minis.hotrank.model.Platform
import com.minis.hotrank.ui.theme.HeatRed

/**
 * 详情页 —— 「内容阅读」形态。
 *
 * 顺序刻意是：配图 -> 标题 -> 摘要 -> 热度 -> 各平台入口。
 * 先让人看懂发生了什么，再提供"去某个 App 细看"的选择。
 *
 * 两种形态共用这一段内容：
 *   - 全屏（默认）：配图和摘要需要呼吸空间
 *   - 底部面板：不想丢失列表位置的用户可以切
 */

@Composable
fun DetailSheet(data: DetailData, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        DetailBody(data = data, onDismiss = onDismiss, heroHeight = 150.dp)
    }
}

@Composable
fun DetailScreen(data: DetailData, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DetailBody(data = data, onDismiss = onDismiss, heroHeight = 210.dp)
    }
}

@Composable
private fun DetailBody(data: DetailData, onDismiss: () -> Unit, heroHeight: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Hero(data, heroHeight, onDismiss)

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 15.dp, bottom = 28.dp)) {

            Text(
                text = data.title,
                fontSize = 21.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(9.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (data.corroboration > 1) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.onSurface)
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            "${data.corroboration} 站同榜",
                            color = MaterialTheme.colorScheme.surface,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
                Text(
                    text = data.members.joinToString(" · ") { it.platform.label },
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }

            data.summary?.let { summary ->
                Spacer(Modifier.height(13.dp))
                Text(
                    text = summary,
                    fontSize = 13.sp,
                    lineHeight = 23.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            StatsRow(data)

            data.heatIndex?.let { heat ->
                Spacer(Modifier.height(15.dp))
                HeatPanel(heat, data.corroboration)
            }

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "各平台上的这条热搜",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "每行是该平台自己的标题",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(9.dp))

            data.members.forEach { member ->
                PlatformButton(member) { PlatformLauncher.open(context, member) }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "已装对应 App 的直接跳进 App，没装的用浏览器打开；" +
                    "标题是各平台自己的写法，同一件事措辞可能不同。",
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Hero(data: DetailData, height: androidx.compose.ui.unit.Dp, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val image = data.image
        // 没有图就退化成渐变底 —— 微博/头条/知乎本来就不给图，不能顶着个加载失败的空框
        val accent = data.members.firstOrNull()?.platform?.let { Color(it.argb) } ?: HeatRed
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.85f),
                            accent.copy(alpha = 0.45f),
                            MaterialTheme.colorScheme.surface,
                        )
                    )
                )
        )

        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // 压一层暗角，保证左上角按钮在任何配图上都看得清
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.34f), Color.Transparent)
                        )
                    )
            )
        }

        Box(
            Modifier
                .statusBarsPadding()
                .padding(start = 14.dp, top = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (data.image != null) {
            Text(
                text = "配图来源：${data.members.firstOrNull { it.image != null }?.platform?.label ?: ""}",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 9.dp),
            )
        }
    }
}

/** 只有 B站和抖音会给真实统计数字，没有就整块不显示，不留空位。 */
@Composable
private fun StatsRow(data: DetailData) {
    val stats = data.members.firstNotNullOfOrNull { it.stats.takeIf { s -> s.isNotEmpty() } } ?: return

    Spacer(Modifier.height(13.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        stats.entries.take(4).forEach { (label, value) ->
            Column {
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = label,
                    fontSize = 9.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeatPanel(heat: Int, corroboration: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = heat.toString(),
            color = HeatRed,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.width(13.dp))
        Column {
            Text(
                text = "综合热度",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (corroboration > 1) {
                    "在 $corroboration 个平台同时上榜"
                } else {
                    "仅出现在一个平台"
                },
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 一个平台一张卡：
 *   第一行  平台色圆点 + 平台名 + 动作胶囊 + 右侧「第 N 名 · 热度」
 *   第二行  ↳ 该平台自己的热搜标题
 *
 * 关键点：**平台名必须常在**。之前的设计把「用浏览器打开」当成主文案，
 * 结果两个走浏览器的平台长得一模一样，用户根本分不清是哪个平台 ——
 * 那正是这个界面要解决的问题。动作降级成小胶囊，不再抢平台名的位置。
 *
 * 黑白填充 = 装了 App 直接跳；描边 = 走浏览器。跳转去向用底色表达，不靠文字。
 */
@Composable
private fun PlatformButton(item: HotItem, onClick: () -> Unit) {
    val installed = PlatformLauncher.isInstalled(
        context = LocalContext.current,
        platform = item.platform,
    )
    val precise = PlatformLauncher.canOpenPrecisely(item)
    val accent = Color(item.platform.argb)

    val action = when {
        !installed -> "用浏览器打开"
        precise -> "在${item.platform.label}打开"
        else -> "在${item.platform.label}搜索"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (installed) Modifier.background(MaterialTheme.colorScheme.onSurface)
                else Modifier.border(
                    1.5.dp,
                    MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(10.dp),
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(8.dp))

            Text(
                text = item.platform.label,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Black,
                color = if (installed) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.width(7.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (installed) MaterialTheme.colorScheme.surface.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 8.dp, vertical = 2.5.dp)
            ) {
                Text(
                    text = action,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (installed) MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = listOfNotNull("第 ${item.rank} 名", item.hotLabel).joinToString(" · "),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (installed) MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(7.dp))

        Row {
            Text(
                text = "↳",
                fontSize = 11.sp,
                color = if (installed) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = item.title,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = if (installed) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
