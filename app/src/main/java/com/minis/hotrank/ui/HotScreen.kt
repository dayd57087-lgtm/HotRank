@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.minis.hotrank.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minis.hotrank.HotUiState
import com.minis.hotrank.HotViewModel
import com.minis.hotrank.model.Platform
import com.minis.hotrank.model.RankedEvent
import com.minis.hotrank.ui.theme.BronzeMedal
import com.minis.hotrank.ui.theme.CorroborationGradient
import com.minis.hotrank.ui.theme.GoldMedal
import com.minis.hotrank.ui.theme.HeatBarGradient
import com.minis.hotrank.ui.theme.HeatRed
import com.minis.hotrank.ui.theme.HeaderGradient
import com.minis.hotrank.ui.theme.SilverMedal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HotScreen(vm: HotViewModel = viewModel()) {

    val state by vm.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Platform?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    // 选中平台时，按「该平台自己的名次」排，而不是全榜名次 —— 用户点微博就是想看微博榜
    val events = remember(state.events, selected) {
        selected?.let { platform ->
            state.events
                .filter { it.platforms.contains(platform) }
                .sortedBy { event ->
                    event.members.firstOrNull { it.platform == platform }?.rank ?: Int.MAX_VALUE
                }
        } ?: state.events
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        Column(Modifier.fillMaxSize()) {
            HeatHeader(state, onRefresh = { vm.refresh(force = true) })
            PlatformChips(selected) { selected = it }

            Box(Modifier.weight(1f)) {
                when {
                    state.loading -> SkeletonList()

                    state.error != null && events.isEmpty() -> ErrorState(state.error!!) {
                        vm.refresh(force = true)
                    }

                    else -> PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = { vm.refresh(force = true) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 14.dp,
                                end = 14.dp,
                                top = 4.dp,
                                bottom = 110.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (state.failed.isNotEmpty()) {
                                item { NoticeBar(state.failed) }
                            }
                            itemsIndexed(events) { index, event ->
                                EventCard(rank = index + 1, event = event)
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showSheet = true },
            containerColor = HeatRed,
            contentColor = Color.White,
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

    if (showSheet) {
        SubscribeSheet(
            subscriptions = state.subscriptions,
            notifyEnabled = state.notifyEnabled,
            onAdd = vm::addKeyword,
            onRemove = vm::removeKeyword,
            onToggleNotify = vm::setNotifyEnabled,
            onCheckNow = vm::checkNow,
            onPermissionChanged = vm::refreshPermissionState,
            onDismiss = { showSheet = false },
        )
    }
}

// ---------------------------------------------------------------- 头部

@Composable
private fun HeatHeader(state: HotUiState, onRefresh: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(HeaderGradient)) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("热", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "热榜聚合",
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${state.onlineCount} 站热榜 · 综合热度排序",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                    )
                }

                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "刷新",
                        tint = Color.White,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (state.updatedAt > 0L) StatPill(clock(state.updatedAt) + " 更新")
                if (state.corroboratedCount > 0) {
                    StatPill("${state.corroboratedCount} 个多站同榜", strong = true)
                }
            }
        }
    }
}

@Composable
private fun StatPill(text: String, strong: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = if (strong) 0.26f else 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ---------------------------------------------------------------- 平台筛选

@Composable
private fun PlatformChips(selected: Platform?, onSelect: (Platform?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlatformChip(
            label = "全部",
            selected = selected == null,
            accent = HeatRed,
            onClick = { onSelect(null) },
        )
        Platform.entries.forEach { platform ->
            PlatformChip(
                label = platform.label,
                selected = selected == platform,
                accent = Color(platform.argb),
                onClick = { onSelect(platform) },
            )
        }
    }
}

@Composable
private fun PlatformChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        color = if (selected) accent else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(50),
        border = if (selected) null
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp),
        )
    }
}

// ---------------------------------------------------------------- 事件卡片

@Composable
private fun EventCard(rank: Int, event: RankedEvent) {
    val context = LocalContext.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openInBrowser(context, event.url) },
    ) {
        Row(modifier = Modifier.padding(13.dp)) {

            RankBadge(rank)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (event.corroboration > 1) {
                    Spacer(Modifier.height(8.dp))
                    CorroborationBadge(event.corroboration)
                }

                Spacer(Modifier.height(9.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    event.platforms.take(4).forEach { PlatformPill(it) }
                }

                Spacer(Modifier.height(10.dp))

                HeatBar(event.heatIndex)
            }

            Spacer(Modifier.width(10.dp))

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.width(46.dp),
            ) {
                Text(
                    text = event.heatIndex.toString(),
                    color = HeatRed,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "综合热度",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    val background = when (rank) {
        1 -> GoldMedal
        2 -> SilverMedal
        3 -> BronzeMedal
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when (rank) {
        1, 2, 3 -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.toString(),
            color = foreground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 「N 站同榜」——聚合榜最有价值的信息，给最强的视觉权重。 */
@Composable
private fun CorroborationBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(CorroborationGradient)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            text = "$count 站同榜",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PlatformPill(platform: Platform) {
    val color = Color(platform.argb)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = platform.label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun HeatBar(value: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(value.coerceIn(4, 100) / 100f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(HeatBarGradient)
        )
    }
}

// ---------------------------------------------------------------- 状态

@Composable
private fun NoticeBar(failed: List<Platform>) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = failed.joinToString("、") { it.label } + " 暂时没拿到，其余榜单正常",
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** 骨架屏：比转圈更能表达"内容马上就到"，也不会让页面跳一下。 */
@Composable
private fun SkeletonList() {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(6) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(13.dp)) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.92f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                        )
                        Spacer(Modifier.height(9.dp))
                        Box(
                            Modifier
                                .fillMaxWidth(0.45f)
                                .height(11.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

// ---------------------------------------------------------------- 工具

private fun openInBrowser(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun clock(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))
