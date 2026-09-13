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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minis.hotrank.HotUiState
import com.minis.hotrank.data.RankingEngine
import com.minis.hotrank.model.HotItem
import com.minis.hotrank.model.RankedEvent
import com.minis.hotrank.model.TimelineEntry
import com.minis.hotrank.ui.theme.HeatRed

/**
 * 搜索。
 *
 * 搜的范围是**这次已经拿到的全部数据**：综合榜事件、各平台原始条目、全网热点。
 * 故意不做服务端搜索 —— 本地数据量也就几百条，直接内存匹配更快，
 * 而且能把"综合榜上的排序结果"和"某平台自己榜上的原话"一起搜出来，
 * 这是调接口搜不到的。
 *
 * 匹配复用聚合排序那套归一化（去标点、转小写），
 * 所以搜「iphone18」能命中「iPhone 18 Pro」。
 */
@Composable
fun SearchScreen(
    state: HotUiState,
    onClose: () -> Unit,
    onOpenEvent: (RankedEvent) -> Unit,
    onOpenItem: (HotItem) -> Unit,
    onOpenNews: (TimelineEntry) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val needle = remember(query) { RankingEngine.normalize(query) }

    val matchedEvents = remember(needle, state.events) {
        if (needle.isEmpty()) emptyList()
        else state.events.filter { RankingEngine.normalize(it.title).contains(needle) }.take(MAX_PER_SECTION)
    }

    val matchedItems = remember(needle, state.byPlatform) {
        if (needle.isEmpty()) emptyList()
        else state.byPlatform.values.flatten()
            .filter { RankingEngine.normalize(it.title).contains(needle) }
            .take(MAX_PER_SECTION)
    }

    val matchedNews = remember(needle, state.timeline) {
        if (needle.isEmpty()) emptyList()
        else state.timeline.filter { RankingEngine.normalize(it.title).contains(needle) }
            .take(MAX_PER_SECTION)
    }

    val total = matchedEvents.size + matchedItems.size + matchedNews.size

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(30) },
                placeholder = {
                    Text("搜热搜、搜新闻", fontSize = 14.sp)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "清空",
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
            )
        }

        when {
            needle.isEmpty() -> SearchHint(state)

            total == 0 -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "没有匹配「$query」的条目",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "只搜当前已加载的数据，下拉刷新后再试试",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            ) {
                if (matchedEvents.isNotEmpty()) {
                    item(key = "h-events") { SectionHeader("综合榜", matchedEvents.size) }
                    items(matchedEvents.size, key = { "e$it" }) { index ->
                        EventResult(matchedEvents[index]) { onOpenEvent(matchedEvents[index]) }
                    }
                }

                if (matchedNews.isNotEmpty()) {
                    item(key = "h-news") { SectionHeader("全网热点", matchedNews.size) }
                    items(matchedNews.size, key = { "n$it" }) { index ->
                        NewsResult(matchedNews[index]) { onOpenNews(matchedNews[index]) }
                    }
                }

                if (matchedItems.isNotEmpty()) {
                    item(key = "h-items") { SectionHeader("各平台热榜", matchedItems.size) }
                    items(matchedItems.size, key = { "i$it" }) { index ->
                        ItemResult(matchedItems[index]) { onOpenItem(matchedItems[index]) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHint(state: HotUiState) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 26.dp)) {
        Text(
            "可搜索 ${state.events.size} 条综合榜 · ${state.byPlatform.values.sumOf { it.size }} 条平台榜" +
                " · ${state.timeline.size} 条热点",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "输入关键词试试。中文直接匹配，英文忽略大小写和空格 —— " +
                "搜「iphone18」也能命中「iPhone 18 Pro」。",
            fontSize = 11.5.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "$count 条",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.onSurface,
        thickness = 1.5.dp,
    )
}

@Composable
private fun ResultRow(
    leading: String?,
    title: String,
    meta: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (leading != null) {
            Text(
                leading,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = HeatRed,
                modifier = Modifier.width(26.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.5.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                meta,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
}

@Composable
private fun EventResult(event: RankedEvent, onClick: () -> Unit) {
    val meta = buildString {
        if (event.corroboration > 1) append("${event.corroboration} 站同榜 · ")
        append(event.platforms.joinToString(" · ") { it.label })
        append(" · 热度 ${event.heatIndex}")
    }
    ResultRow(leading = null, title = event.title, meta = meta, onClick = onClick)
}

@Composable
private fun NewsResult(entry: TimelineEntry, onClick: () -> Unit) {
    val meta = buildString {
        append(entry.source.ifEmpty { "全网热点" })
        append(" · ")
        append(agoText(entry.publishedAt))
        if (entry.isHotNow) append(" · 正在热搜")
        else if (entry.isFaded) append(" · 曾上热搜")
    }
    ResultRow(leading = null, title = entry.title, meta = meta, onClick = onClick)
}

@Composable
private fun ItemResult(item: HotItem, onClick: () -> Unit) {
    val meta = buildString {
        append(item.platform.label)
        append(" · 第 ${item.rank} 名")
        item.hotLabel?.let { append(" · $it") }
    }
    ResultRow(leading = null, title = item.title, meta = meta, onClick = onClick)
}

private fun agoText(timestamp: Long): String {
    val minutes = ((System.currentTimeMillis() - timestamp) / 60_000L).toInt().coerceAtLeast(0)
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        minutes < 60 * 24 -> "${minutes / 60} 小时前"
        else -> "${minutes / (60 * 24)} 天前"
    }
}

private const val MAX_PER_SECTION = 20
