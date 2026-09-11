@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.minis.hotrank.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.minis.hotrank.model.TimeBucket
import com.minis.hotrank.model.TimelineEntry
import com.minis.hotrank.ui.theme.HeatRed
import java.util.Locale

/**
 * 全网热点时间线。
 *
 * 数据来自新闻源（有真实发布时间），与热榜（有榜单位置但无发布时间）交叉关联。
 * 按发布时间分段，左侧竖线表达"时间在往下走"。
 *
 * 两种标记的含义：
 *   红色「微博热搜」          = 这条新闻此刻正好还在某个平台的热榜上
 *   灰色「曾上热搜 · 已掉榜」  = 上过榜，但现在已经掉了
 * 前者说明"真的全网在聊"，后者说明"火过一阵就没了"，
 * 这是两套数据交叉之后才有的判断。
 */
@Composable
fun TimelinePage(
    grouped: List<Pair<TimeBucket, List<TimelineEntry>>>,
    failed: Boolean,
    onOpen: (TimelineEntry) -> Unit,
) {
    if (grouped.isEmpty()) {
        EmptyTimeline(failed)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
    ) {
        grouped.forEachIndexed { groupIndex, (bucket, items) ->
            item(key = "head-${bucket.name}") {
                BucketHeader(bucket, items.size, isFirst = groupIndex == 0)
            }
            items(
                count = items.size,
                key = { index -> items[index].id },
            ) { index ->
                TimelineRow(items[index]) { onOpen(items[index]) }
            }
        }
    }
}

/** 分组头：圆形节点 + 区间标签 + 条数，下面接一小段竖线把视觉引到卡片上。 */
@Composable
private fun BucketHeader(bucket: TimeBucket, count: Int, isFirst: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = if (isFirst) 6.dp else 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(23.dp)
                    .clip(CircleShape)
                    .background(if (isFirst) HeatRed else MaterialTheme.colorScheme.onSurface),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isFirst) "新" else "${(bucket.maxMinutes / 60).coerceAtMost(24)}",
                    color = MaterialTheme.colorScheme.surface,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black,
                )
            }

            Spacer(Modifier.width(9.dp))

            Text(
                text = bucket.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = "$count 条",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 节点正下方的一小段竖线，把分组头和卡片连起来
        Box(
            Modifier
                .padding(start = 11.dp, top = 3.dp)
                .width(1.5.dp)
                .height(13.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

/** 卡片缩进到与竖线右侧对齐，竖线在卡片左侧留一条细装饰。 */
@Composable
private fun TimelineRow(entry: TimelineEntry, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Box(
            Modifier
                .padding(start = 11.dp)
                .width(1.5.dp)
                .height(28.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(Modifier.width(19.dp))
        TimelineCard(entry, onClick)
    }
}

@Composable
private fun TimelineCard(entry: TimelineEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(11.dp)
    ) {
        Row {
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(7.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = entry.source.ifEmpty { "全网热点" },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MetaDot()
                    Text(
                        text = ago(entry.publishedAt),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (entry.readCount > 0) {
                        MetaDot()
                        Text(
                            text = "阅读 " + compact(entry.readCount),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            val image = entry.image
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 84.dp, height = 63.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 84.dp, height = 63.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "无配图",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (entry.hotLinks.isNotEmpty()) {
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (entry.isHotNow) {
                    LinkBadge(
                        text = entry.hotLinks.filter { it.live }
                            .joinToString("·") { it.platform.short } + "热搜",
                        strong = true,
                    )
                }
                if (entry.isFaded) {
                    LinkBadge(text = "曾上热搜 · 已掉榜", strong = false)
                }
            }
        }
    }
}

@Composable
private fun LinkBadge(text: String, strong: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (strong) HeatRed else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 7.dp, vertical = 2.5.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Black,
            color = if (strong) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetaDot() {
    Box(
        Modifier
            .size(2.5.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outline)
    )
}

@Composable
private fun EmptyTimeline(failed: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            Text(
                text = if (failed) "没拿到热点数据" else "24 小时内暂无热点",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = "下拉刷新试试",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ago(timestamp: Long): String {
    val minutes = ((System.currentTimeMillis() - timestamp) / 60_000L).toInt().coerceAtLeast(0)
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        minutes < 60 * 24 -> "${minutes / 60} 小时前"
        else -> "${minutes / (60 * 24)} 天前"
    }
}

private fun compact(number: Int): String = when {
    number >= 100_000_000 -> String.format(Locale.CHINA, "%.1f亿", number / 100_000_000.0)
    number >= 10_000 -> String.format(Locale.CHINA, "%.1f万", number / 10_000.0)
    else -> number.toString()
}
