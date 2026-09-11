package com.minis.hotrank.data

import android.content.Context
import com.minis.hotrank.model.HotItem
import com.minis.hotrank.model.HotLink
import com.minis.hotrank.model.Platform
import com.minis.hotrank.model.TimelineEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * 「曾上热搜」记录。
 *
 * 为什么需要它：用户要"已掉榜"标记，但热榜和新闻源是两套数据 ——
 * 新闻是按发布时间往下走的，本身没有上榜/掉榜的概念。
 * 所以本地持续记录"哪些标题曾经出现在热榜上"：
 *   - 命中当前热榜        -> 标「热搜」
 *   - 命中历史但已不在榜   -> 标「曾上热搜 · 已掉榜」
 *
 * 这样"已掉榜"才有落脚点，而且顺带解决了一个更有价值的问题：
 * 区分"真的全网在聊"和"只是媒体在发"。
 *
 * 注意：内部全部用显式 for 循环而不是嵌套 forEach ——
 * 嵌套 forEach 里的 return@forEach 容易指向错误的那一层，可读性也差。
 */
class SeenHotStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("hot_seen", Context.MODE_PRIVATE)

    private class Sighting(val platform: Platform, val rank: Int, val seenAt: Long)

    /** 归一化标题 -> 该标题在各平台上最后一次见到的样子 */
    private val cache = LinkedHashMap<String, MutableMap<Platform, Sighting>>()

    /** 把当前热榜上的所有条目记为"见过"。每次刷新热榜后调用。 */
    fun record(byPlatform: Map<Platform, List<HotItem>>) {
        load()
        val now = System.currentTimeMillis()

        for ((platform, items) in byPlatform) {
            for (item in items) {
                val key = RankingEngine.normalize(item.title)
                if (key.isEmpty()) continue
                val slots = cache.getOrPut(key) { LinkedHashMap() }
                slots[platform] = Sighting(platform, item.rank, now)
            }
        }
        prune(now)
        save()
    }

    /**
     * 为一条时间线新闻找出所有热榜关联。
     *
     * 匹配用与聚合排序同一套相似度逻辑（含数字冲突否决），
     * 这样"iPhone17Pro"不会被误判成和"iPhone18Pro"是同一件事。
     */
    fun linksFor(entry: TimelineEntry, liveHot: Map<String, HotLink>): List<HotLink> {
        load()
        val norm = RankingEngine.normalize(entry.title)
        if (norm.isEmpty()) return emptyList()

        val found = LinkedHashMap<Platform, HotLink>()

        // 优先认当前榜上的（live = true）
        val exact = liveHot[norm]
        if (exact != null) {
            found[exact.platform] = exact
        } else {
            for ((key, link) in liveHot) {
                if (RankingEngine.similarity(norm, key) >= MATCH_THRESHOLD) {
                    found[link.platform] = link
                    break
                }
            }
        }

        // 再补历史记录（live = false）—— 同一平台当前已在榜就不重复加
        for ((key, byPlatform) in cache) {
            if (found.size >= MAX_LINKS) break
            if (RankingEngine.similarity(norm, key) < MATCH_THRESHOLD) continue
            for ((platform, sighting) in byPlatform) {
                if (platform !in found) {
                    found[platform] = HotLink(platform, sighting.rank, live = false)
                }
            }
        }

        return found.values.take(MAX_LINKS)
    }

    /** 当前热榜的 归一化标题 -> HotLink，供 linksFor 优先匹配。 */
    fun liveIndex(byPlatform: Map<Platform, List<HotItem>>): Map<String, HotLink> {
        val out = HashMap<String, HotLink>()
        for ((platform, items) in byPlatform) {
            for (item in items) {
                val key = RankingEngine.normalize(item.title)
                if (key.isEmpty()) continue
                val existing = out[key]
                // 同一标题在多个平台出现时，保留名次最好的那个
                if (existing == null || item.rank < existing.rank) {
                    out[key] = HotLink(platform, item.rank, live = true)
                }
            }
        }
        return out
    }

    private fun prune(now: Long) {
        val limit = now - RETENTION
        val emptyKeys = ArrayList<String>()
        for ((key, byPlatform) in cache) {
            val stale = byPlatform.filterValues { it.seenAt < limit }.keys
            stale.forEach { byPlatform.remove(it) }
            if (byPlatform.isEmpty()) emptyKeys += key
        }
        emptyKeys.forEach { cache.remove(it) }
    }

    private fun load() {
        if (cache.isNotEmpty()) return
        val raw = prefs.getString(KEY, null) ?: return
        runCatching {
            val root = JSONObject(raw)
            for (key in root.keys()) {
                val obj = root.optJSONObject(key) ?: continue
                val byPlatform = LinkedHashMap<Platform, Sighting>()
                for (apiId in obj.keys()) {
                    val platform = Platform.entries.firstOrNull { it.apiId == apiId } ?: continue
                    val arr = obj.optJSONArray(apiId) ?: continue
                    byPlatform[platform] = Sighting(
                        platform = platform,
                        rank = arr.optInt(0, 0),
                        seenAt = arr.optLong(1, 0L),
                    )
                }
                if (byPlatform.isNotEmpty()) cache[key] = byPlatform
            }
        }
    }

    private fun save() {
        val root = JSONObject()
        // takeLast 只对 List 定义，Set 必须先 toList —— 直接对 entries 调用会编译不过
        val recent = cache.entries.toList().takeLast(MAX_KEYS)
        for ((key, byPlatform) in recent) {
            val obj = JSONObject()
            for ((platform, sighting) in byPlatform) {
                obj.put(platform.apiId, JSONArray().put(sighting.rank).put(sighting.seenAt))
            }
            root.put(key, obj)
        }
        prefs.edit().putString(KEY, root.toString()).apply()
    }

    private companion object {
        const val KEY = "seen_titles"
        const val RETENTION = 24 * 60 * 60 * 1000L
        const val MAX_KEYS = 2000
        const val MAX_LINKS = 4
        const val MATCH_THRESHOLD = 0.55
    }
}
