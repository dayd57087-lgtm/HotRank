package com.minis.hotrank.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.minis.hotrank.data.HotApi
import com.minis.hotrank.data.KeywordMatcher
import com.minis.hotrank.data.RankingEngine
import com.minis.hotrank.data.SubscriptionStore
import com.minis.hotrank.model.HotItem
import com.minis.hotrank.model.Platform
import com.minis.hotrank.notify.Notifier
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

/**
 * 后台检查订阅关键词是否上榜。
 *
 * 走 WorkManager 而不是自己起 Service：系统会在合适的时机调度，
 * 不用管省电策略和进程保活。代价是周期最短 15 分钟，对热榜来说够用。
 */
class KeywordWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val store = SubscriptionStore(applicationContext)
        if (!store.enabled) return Result.success()

        val keywords = store.list().map { it.keyword }
        if (keywords.isEmpty()) return Result.success()

        val byPlatform: Map<Platform, List<HotItem>> = coroutineScope {
            Platform.entries
                .map { platform -> async { platform to HotApi.fetch(platform) } }
                .awaitAll()
                .filter { it.second.isNotEmpty() }
                .toMap()
        }
        if (byPlatform.isEmpty()) return Result.retry()

        val events = RankingEngine.rank(byPlatform)
        val hits = KeywordMatcher.match(events, keywords)

        // 过滤掉已经推送过的（关键词, 标题）组合，避免每次检查都重复打扰
        val fresh = hits.mapNotNull { hit ->
            val events = hit.events.filter { store.shouldNotify(hit.keyword, it.title) }
            if (events.isEmpty()) null else hit.copy(events = events)
        }

        if (fresh.isNotEmpty()) {
            store.markNotified(fresh.flatMap { hit -> hit.events.map { hit.keyword to it.title } })
            Notifier.notifyMatches(applicationContext, fresh)
        }
        return Result.success()
    }
}

object KeywordScheduler {

    private const val PERIODIC_NAME = "hot_keyword_check"
    private const val ONE_SHOT_NAME = "hot_keyword_check_once"

    private val connected = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** 30 分钟一轮。系统会按省电策略适当延后，这是正常的。 */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<KeywordWorker>(30, TimeUnit.MINUTES)
            .setConstraints(connected)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
    }

    /** 刚订阅完想立刻看到结果，不用等下一个周期。 */
    fun runOnce(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_SHOT_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<KeywordWorker>().setConstraints(connected).build(),
        )
    }
}
