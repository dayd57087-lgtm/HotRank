package com.minis.hotrank.data

import android.content.Context
import com.minis.hotrank.model.HotItem
import com.minis.hotrank.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

data class HotFeed(
    val merged: List<HotItem>,
    val byPlatform: Map<Platform, List<HotItem>>,
    val failed: List<Platform>,
    val updatedAt: Long,
)

/**
 * 缓存策略：
 *   1. 缓存 10 分钟内且非强制刷新 -> 直接读缓存
 *   2. 否则并发拉 6 个平台，成功就写缓存
 *   3. 拉取失败 -> 退回「过期缓存」（宁可能看旧数据，也不给白屏）
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
            // 混合流每个平台只取前 20：6 x 20 = 120 条，滚得完也不会变成流水账。
            merged = interleave(byPlatform.values.map { it.take(MERGED_DEPTH) }),
            byPlatform = byPlatform,
            failed = failed,
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

    /**
     * 各平台第 1 名 -> 各平台第 2 名 -> ... 交错合并。
     * 不做热度归一化排序，因为「微博 180 万热度」和「知乎 300 万热度」本来就不是一个量纲，
     * 强行混排出来的顺序看着科学其实是假的。
     */
    private fun interleave(lists: List<List<HotItem>>): List<HotItem> {
        if (lists.isEmpty()) return emptyList()
        val depth = lists.maxOf { it.size }
        val out = ArrayList<HotItem>(lists.sumOf { it.size })
        for (i in 0 until depth) {
            for (list in lists) list.getOrNull(i)?.let { out += it }
        }
        return out
    }

    private fun toJson(items: List<HotItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("r", item.rank)
                    .put("t", item.title)
                    .put("u", item.url)
                    .put("h", item.hot ?: "")
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
                out += HotItem(
                    platform = platform,
                    rank = obj.optInt("r", i + 1),
                    title = title,
                    url = obj.optString("u"),
                    hot = obj.optString("h").ifEmpty { null },
                )
            }
            out
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val TTL = 10 * 60 * 1000L
        private const val MERGED_DEPTH = 20
    }
}
