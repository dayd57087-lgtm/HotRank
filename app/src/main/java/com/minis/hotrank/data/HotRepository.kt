package com.minis.hotrank.data

import android.content.Context
import com.minis.hotrank.model.HotItem
import com.minis.hotrank.model.Platform
import com.minis.hotrank.model.RankedEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

data class HotFeed(
    val events: List<RankedEvent>,
    val failed: List<Platform>,
    val onlineCount: Int,
    val updatedAt: Long,
)

/**
 * 缓存 + 调度。
 *
 * 缓存按「平台原始列表」存，不存排序结果 —— 排序是纯计算，每次重新跑，
 * 这样以后调算法参数不会让老缓存变成脏数据。
 *
 * 策略：
 *   1. 缓存有效期内且非强制刷新 -> 直接用缓存
 *   2. 否则并发拉 6 个平台，成功就写缓存
 *   3. 拉取失败 -> 退回过期缓存（宁可能看旧数据，也不给白屏）
 */
class HotRepository(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("hot_cache", Context.MODE_PRIVATE)

    suspend fun load(force: Boolean): HotFeed = coroutineScope {
        val results = Platform.entries
            .map { platform -> async(Dispatchers.IO) { platform to loadOne(platform, force) } }
            .awaitAll()

        val byPlatform = LinkedHashMap<Platform, List<HotItem>>()
        val failed = ArrayList<Platform>()
        var updatedAt = 0L

        for ((platform, slot) in results) {
            if (slot.items.isEmpty()) {
                failed += platform
                continue
            }
            byPlatform[platform] = slot.items
            if (slot.updatedAt > updatedAt) updatedAt = slot.updatedAt
        }

        HotFeed(
            events = RankingEngine.rank(byPlatform),
            failed = failed,
            onlineCount = byPlatform.size,
            updatedAt = updatedAt,
        )
    }

    private class Slot(val items: List<HotItem>, val updatedAt: Long)

    private fun loadOne(platform: Platform, force: Boolean): Slot {
        val listKey = "list_" + platform.apiId
        val timeKey = "ts_" + platform.apiId
        val cachedAt = prefs.getLong(timeKey, 0L)

        if (!force && prefs.contains(listKey) && System.currentTimeMillis() - cachedAt < TTL) {
            readJson(prefs.getString(listKey, null), platform)?.let { return Slot(it, cachedAt) }
        }

        val fetched = HotApi.fetch(platform)
        if (fetched.isNotEmpty()) {
            val now = System.currentTimeMillis()
            prefs.edit()
                .putString(listKey, toJson(fetched))
                .putLong(timeKey, now)
                .apply()
            return Slot(fetched, now)
        }

        readJson(prefs.getString(listKey, null), platform)?.let { return Slot(it, cachedAt) }
        return Slot(emptyList(), 0L)
    }

    private fun toJson(items: List<HotItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("r", item.rank)
                    .put("t", item.title)
                    .put("u", item.url)
                    .put("hl", item.hotLabel ?: "")
                    .put("hn", item.rawHeat ?: 0.0)
            )
        }
        return array.toString()
    }

    private fun readJson(raw: String?, platform: Platform): List<HotItem>? {
        if (raw.isNullOrEmpty()) return null
        return runCatching {
            val array = JSONArray(raw)
            val out = ArrayList<HotItem>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val title = obj.optString("t")
                if (title.isEmpty()) continue
                val heat = obj.optDouble("hn", 0.0)
                out += HotItem(
                    platform = platform,
                    rank = obj.optInt("r", i + 1),
                    title = title,
                    url = obj.optString("u"),
                    hotLabel = obj.optString("hl").ifEmpty { null },
                    rawHeat = if (heat > 0.0) heat else null,
                )
            }
            out
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val TTL = 10 * 60 * 1000L
    }
}
