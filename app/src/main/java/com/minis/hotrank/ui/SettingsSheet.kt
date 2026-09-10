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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minis.hotrank.data.DetailForm
import com.minis.hotrank.model.Platform

@Composable
fun SettingsSheet(
    detailForm: DetailForm,
    showCrossLink: Boolean,
    onSetDetailForm: (DetailForm) -> Unit,
    onSetShowCrossLink: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "设置",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(20.dp))

            SectionTitle("详情页打开方式")
            Spacer(Modifier.height(9.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FormOption(
                    title = "全屏页",
                    subtitle = "配图和摘要看得更舒展",
                    selected = detailForm == DetailForm.FULLSCREEN,
                    modifier = Modifier.weight(1f),
                ) { onSetDetailForm(DetailForm.FULLSCREEN) }

                FormOption(
                    title = "底部面板",
                    subtitle = "不丢失列表位置",
                    selected = detailForm == DetailForm.SHEET,
                    modifier = Modifier.weight(1f),
                ) { onSetDetailForm(DetailForm.SHEET) }
            }

            Spacer(Modifier.height(22.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "原始榜显示跨榜标记",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "在各平台自己的榜里，标出这条同时上了综合榜第几名",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = showCrossLink, onCheckedChange = onSetShowCrossLink)
            }

            Spacer(Modifier.height(22.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            SectionTitle("已接入平台")
            Spacer(Modifier.height(9.dp))
            Text(
                text = Platform.entries.joinToString(" · ") { it.label },
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FormOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .then(
                if (selected) Modifier.background(MaterialTheme.colorScheme.onSurface)
                else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
    ) {
        Column {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = if (selected) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                color = if (selected) MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
