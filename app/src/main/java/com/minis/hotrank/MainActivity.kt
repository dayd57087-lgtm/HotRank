package com.minis.hotrank

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.minis.hotrank.notify.Notifier
import com.minis.hotrank.ui.CrashScreen
import com.minis.hotrank.ui.HotScreen
import com.minis.hotrank.ui.theme.HotRankTheme
import com.minis.hotrank.util.CrashLog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannel(this)

        // 上次如果崩过，先把现场显示出来 —— 让"闪退"变成一段能发回来的堆栈。
        // 用户点「继续使用」后本次会话不再打扰（CrashLog.consume 已经清掉记录）。
        val crashReport = CrashLog.consume(this)

        setContent {
            HotRankTheme {
                var dismissed by remember { mutableStateOf(false) }
                if (crashReport != null && !dismissed) {
                    CrashScreen(crashReport) { dismissed = true }
                } else {
                    HotScreen()
                }
            }
        }
    }
}
