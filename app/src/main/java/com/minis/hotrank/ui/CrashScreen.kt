@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.minis.hotrank.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minis.hotrank.util.CrashLog
import com.minis.hotrank.ui.theme.HeatRed

/**
 * 崩溃现场展示页。
 *
 * 目的很单纯：让"闪退"变成"一段可复制的堆栈"。
 * 用户截图发回来，就能直接定位到出错的文件和行号，不用靠猜。
 * 文本包在 SelectionContainer 里，可以长按选中复制。
 */
@Composable
fun CrashScreen(
    report: CrashLog.Report,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        Text(
            text = "上次崩溃了",
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "下面是具体的错误信息。把它截图发给我，就能定位问题。",
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = CrashLog.formatTime(report.time),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = HeatRed,
        )
        if (report.version.isNotEmpty()) {
            Text(
                text = report.version,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            SelectionContainer {
                Text(
                    text = report.stack,
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { copy(context, report.stack) },
                modifier = Modifier.weight(1f),
            ) {
                Text("复制错误信息")
            }
            Button(
                onClick = onContinue,
                modifier = Modifier.weight(1f),
            ) {
                Text("继续使用")
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun copy(context: Context, text: String) {
    runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("HotRank crash", text))
    }
}
