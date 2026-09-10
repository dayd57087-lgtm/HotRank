@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.minis.hotrank.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minis.hotrank.HotUiState
import com.minis.hotrank.HotViewModel
import com.minis.hotrank.data.DetailForm
import com.minis.hotrank.model.HotItem
import com.minis.hotrank.model.Platform
import com.minis.hotrank.model.RankedEvent
import com.minis.hotrank.model.toDetail
import com.minis.hotrank.ui.theme.HeatRed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 第 0 页是综合榜，之后每个平台一页。 */
private val TABS: List<String> = listOf("综合") + Platform.entries.map { it.label }

@Composable
fun HotScreen(vm: HotViewModel = viewModel()) {

    val state by vm.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { TABS.size })
    val tabListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var detail by remember { mutableStateOf<com.minis.hotrank.model.DetailData?>(null) }
    var showSubscribe by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // 翻页时把选中的 tab 滚进可视区，否则滑到 B站 时 tab 还停在左边
    LaunchedEffect(pagerState.currentPage) {
        tabListState.animateScrollToItem(pagerState.currentPage)
    }

    // 详情是全屏页时，系统返回键要能关掉它
    BackHandler(enabled = detail != null) { detail = null }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        Column(Modifier.fillMaxSize()) {

            Header(state, onSettings = { showSettings = true })

            TabsRow(
                selected = pagerState.currentPage,
                listState = tabListState,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            )

            Box(Modifier.weight(1f)) {
                if (state.loading) {
                    SkeletonList()
                } else if (state.error != null && state.events.isEmpty()) {
                    ErrorState(state.error!!) { vm.refresh(force = true) }
                } else {
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = { vm.refresh(force = true) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            if (page == 0) {
                                AggregatePage(state, onOpen = { detail = it.toDetail() })
                            } else {
                                val platform = Platform.entries[page - 1]
                                PlatformPage(
                                    state = state,
                                    platform = platform,
                                    aggregateRankOf = vm::aggregateRankOf,
                                    onOpen = { detail = it.toDetail() },
                                )
                            }
                        }
                    }
                }
            }

            PageDots(pagerState.currentPage, TABS.size)
        }

        ExtendedFloatingActionButton(
            onClick = { showSubscribe = true },
            containerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface,
            icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
            text = {
                Text(
                    if (state.subscriptions.isEmpty()) "订阅关键词"
                    else "订阅 · ${state.subscriptions.size}"
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(18.dp),
        )
    }

    // 详情页按设置决定形态
    detail?.let { data ->
        when (state.detailForm) {
            DetailForm.FULLSCREEN -> DetailScreen(data) { detail = null }
            DetailForm.SHEET -> DetailSheet(data) { detail = null }
        }
    }

    if (showSubscribe) {
        SubscribeSheet(
            subscriptions = state.subscriptions,
            notifyEnabled = state.notifyEnabled,
            onAdd = vm::addKeyword,
            onRemove = vm::removeKeyword,
            onToggleNotify = vm::setNotifyEnabled,
            onCheckNow = vm::checkNow,
            onPermissionChanged = {},
            onDismiss = { showSubscribe = false },
        )
    }

    if (showSettings) {
        SettingsSheet(
            detailForm = state.detailForm,
            showCrossLink = state.showCrossLink,
            onSetDetailForm = vm::setDetailForm,
            onSetShowCrossLink = vm::setShowCrossLink,
            onDismiss = { showSettings = false },
        )
    }
}

// ---------------------------------------------------------------- 头部

@Composable
private fun Header(state: HotUiState, onSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "HOT SEARCH AGGREGATE",
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSettings, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
        }

        Text(
            text = "热榜聚合",
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(11.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .background(MaterialTheme.colorScheme.onSurface)
        )

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth()) {
            Text(
                text = buildString {
                    append(state.onlineCount)
                    append(" 站在线")
                    if (state.updatedAt > 0L) append(" · " + clock(state.updatedAt) + " 更新")
                },
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (state.corroboratedCount > 0) {
                Text(
                    text = "${state.corroboratedCount} 个多站同榜",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = HeatRed,
                )
            }
        }

        Spacer(Modifier.height(11.dp))
    }
}

// ---------------------------------------------------------------- 分页

@Composable
private fun TabsRow(selected: Int, listState: androidx.compose.foundation.lazy.LazyListState, onSelect: (Int) -> Unit) {
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(TABS) { index, label ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 13.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Spacer(Modifier.height(11.dp))
}

@Composable
private fun PageDots(current: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val active = index == current
            Box(
                Modifier
                    .padding(horizontal = 2.5.dp)
                    .height(5.dp)
                    .width(if (active) 14.dp else 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

// ---------------------------------------------------------------- 综合榜

@Composable
private fun AggregatePage(state: HotUiState, onOpen: (RankedEvent) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp),
    ) {
        if (state.failed.isNotEmpty()) {
            item { NoticeBar(state.failed) }
        }
        itemsIndexed(state.events) { index, event ->
            AggregateRow(index + 1, event) { onOpen(event) }
        }
    }
}

@Composable
private fun AggregateRow(rank: Int, event: RankedEvent, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
    ) {
        RankNumber(rank)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = event.title,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (event.corroboration > 1) CorroborationTag(event.corroboration)
                Text(
                    text = event.platforms.joinToString(" · ") { it.label },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        HeatScore(event.heatIndex)
    }
    Divider()
}

// ---------------------------------------------------------------- 平台原始榜

@Composable
private fun PlatformPage(
    state: HotUiState,
    platform: Platform,
    aggregateRankOf: (HotItem) -> Int?,
    onOpen: (HotItem) -> Unit,
) {
    val items = state.byPlatform[platform].orEmpty()

    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "没拿到${platform.label}的榜单",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp),
    ) {
        itemsIndexed(items) { index, item ->
            PlatformRow(
                rank = index + 1,
                item = item,
                aggregateRank = if (state.showCrossLink) aggregateRankOf(item) else null,
                onClick = { onOpen(item) },
            )
        }
    }
}

@Composable
private fun PlatformRow(rank: Int, item: HotItem, aggregateRank: Int?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        RankNumber(rank)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 15.5.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                item.hotLabel?.let {
                    Text(
                        text = it,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 跨榜衔接：让用户明白平台原始榜和综合榜不是两张不相干的表
                aggregateRank?.let {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(HeatRed)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "上综合榜第 $it",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.surface,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        HeatScore(null, item.hotLabel)
    }
    Divider()
}

// ---------------------------------------------------------------- 通用零件

/** 名次：超大粗体，第 1 名用品牌红，其余是暖灰。 */
@Composable
private fun RankNumber(rank: Int) {
    Text(
        text = rank.toString(),
        fontSize = 25.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.Black,
        color = if (rank == 1) HeatRed else MaterialTheme.colorScheme.outline,
        modifier = Modifier.width(36.dp),
    )
}

@Composable
private fun CorroborationTag(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.onSurface)
            .padding(horizontal = 7.dp, vertical = 2.5.dp)
    ) {
        Text(
            "$count 站同榜",
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.surface,
        )
    }
}

/** 右侧综合热度；没有热度时退化成平台自己的热度文本。 */
@Composable
private fun HeatScore(heat: Int?, fallback: String? = null) {
    if (heat != null) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = heat.toString(),
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "热度",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    } else {
        Text(
            text = fallback.orEmpty(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun NoticeBar(failed: List<Platform>) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Text(
            text = failed.joinToString("、") { it.label } + " 暂时没拿到，其余榜单正常",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(4.dp))
}

/** 骨架屏：比转圈更能表达"内容马上就到"，也不会让页面跳一下。 */
@Composable
private fun SkeletonList() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(1.5.dp),
    ) {
        repeat(7) {
            Row(Modifier.padding(vertical = 14.dp)) {
                Box(
                    Modifier
                        .width(26.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.92f)
                            .height(15.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.4f)
                            .height(10.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

private fun clock(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))
