package com.minis.hotrank

import android.app.Application
import com.minis.hotrank.notify.Notifier
import com.minis.hotrank.util.CrashLog

class HotRankApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 越早装越好，要覆盖到 Application 初始化阶段之后的全部代码
        CrashLog.install(this)
        Notifier.ensureChannel(this)
    }
}
