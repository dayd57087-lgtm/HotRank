package com.minis.hotrank

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.minis.hotrank.ui.HotScreen
import com.minis.hotrank.ui.theme.HotRankTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HotRankTheme {
                HotScreen()
            }
        }
    }
}
