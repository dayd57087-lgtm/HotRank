package com.minis.hotrank.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.minis.hotrank.data.HotRepository
import com.minis.hotrank.data.WidgetStore
import java.util.concurrent.TimeUnit

/**
 * 小组件的后台刷新。
 *
 * 只有桌面上挂着小组件时才会被调度（启用时排任务、移除时取消），
 * 所以不会在用户没使用小组件的情况下白白唤醒。
 *
 * 注意这里**只拉 6 个热榜、不拉新闻**（includeTimeline = false）——
 * 小组件只需要综合榜排序结果，多一次网络请求纯属浪费。
 */
class WidgetWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = HotRepository(applicationContext)
        val feed = repo.load(force = false, includeTimeline = false)
        if (feed.events.isEmpty()) return Result.retry()

        WidgetStore(applicationContext).write(feed.events, feed.updatedAt)
        HotRankWidget.renderAll(applicationContext)
        return Result.success()
    }
}

object WidgetUpdater {

    private const val PERIODIC = "hotrank_widget_refresh"
    private const val ONESHOT = "hotrank_widget_refresh_once"

    private val connected = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * 30 分钟一轮。和小组件自身声明的 updatePeriodMillis 对齐，
     * 是"保持新鲜"和"别太耗电"之间我能接受的平衡点。
     */
    fun schedulePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP, // 已经有就不重置，避免频繁添加小组件反复重排
            PeriodicWorkRequestBuilder<WidgetWorker>(30, TimeUnit.MINUTES)
                .setConstraints(connected)
                .build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    /** 刚添加小组件 / 点了刷新按钮时，尽快来一次。 */
    fun refreshSoon(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONESHOT,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetWorker>().setConstraints(connected).build(),
        )
    }
}
