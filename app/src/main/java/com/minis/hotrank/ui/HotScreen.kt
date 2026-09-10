// 用全限定名做文件级 opt-in，避免不同 material3 版本里 FilterChip / PullToRefreshBox
// 的实验性标注不一致导致编译失败（多写一个无害，少写一个直接报错）
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.minis.hotrank.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minis.hotrank.HotViewModel
import com.minis.hotrank.model.HotItem
import com.minis.hotrank.model.Platform
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotScreen(vm: HotViewModel = viewModel()) {

    val state by vm.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Platform?>(null) }

    val list = remember(state.merged, state.byPlatform, selected) {
        selected?.let { state.byPlatform[it].orEmpty() } ?: state.merged
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("热榜聚合", fontWeight = FontWeight.Bold)
                        if (state.updatedAt > 0L) {
                            Text(
                                text = clock(state.updatedAt) + " 更新 · " +
                                    state.byPlatform.size + " 站在线",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh(force = true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PlatformChips(selected = selected, onSelect = { selected = it })

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.loading -> CenterBox { CircularProgressIndicator() }

                    state.error != null && list.isEmpty() -> CenterBox {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { vm.refresh(force = true) }) { Text("重试") }
                        }
                    }

                    else -> PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = { vm.refresh(force = true) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            if (state.failed.isNotEmpty()) {
                                item { NoticeBar(state.failed) }
                            }
                            items(list) { item -> HotRow(item) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformChips(selected: Platform?, onSelect: (Platform?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("全部") },
        )
        Platform.entries.forEach { platform ->
            FilterChip(
                selected = selected == platform,
                onClick = { onSelect(platform) },
                label = { Text(platform.label) },
            )
        }
    }
}

@Composable
private fun HotRow(item: HotItem) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openInBrowser(context, item.url) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(item.rank)

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformTag(item.platform)
                item.hot?.let { hot ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = hot,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun RankBadge(rank: Int) {
    val background = when (rank) {
        1 -> Color(0xFFE53935)
        2 -> Color(0xFFFB8C00)
        3 -> Color(0xFFFDD835)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when (rank) {
        1, 2 -> Color.White
        3 -> Color(0xFF3E2723)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(24.dp)
            .background(background, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.toString(),
            color = foreground,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PlatformTag(platform: Platform) {
    val color = Color(platform.argb)
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
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
private fun NoticeBar(failed: List<Platform>) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = failed.joinToString("、") { it.label } + " 暂时没拿到，其余榜单正常",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) { content() }
}

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
