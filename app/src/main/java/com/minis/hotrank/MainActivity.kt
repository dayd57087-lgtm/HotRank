package com.minis.hotrank

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.minis.hotrank.notify.Notifier
import com.minis.hotrank.ui.HotScreen
import com.minis.hotrank.ui.theme.HotRankTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 通知渠道越早建越好：渠道建好之后用户就能在系统设置里看到并单独配置
        Notifier.ensureChannel(this)
        setContent {
            HotRankTheme {
                HotScreen()
            }
        }
    }
}
