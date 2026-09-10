package com.minis.hotrank.data

import android.content.Context
import com.minis.hotrank.model.RankedEvent
import org.json.JSONArray
import org.json.JSONObject

data class Subscription(val keyword: String, val createdAt: Long)

/** 一个关键词命中了哪些事件。 */
data class KeywordHit(val keyword: String, val events: List<RankedEvent>)

/**
 * 关键词订阅的本地存储。
 *
 * 用 SharedPreferences + JSON 而不是 Room：订阅量级是几条到几十条，
 * 上数据库是负收益。去重记录用 "关键词|标题" 做键，避免同一件事反复推送。
 */
class SubscriptionStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("hot_subscriptions", Context.MODE_PRIVATE)

    fun list(): List<Subscription> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val keyword = obj.optString("k").trim()
                if (keyword.isEmpty()) null
                else Subscription(keyword, obj.optLong("t"))
            }
        }.getOrDefault(emptyList())
    }

    /** 关键词已存在（忽略大小写）则返回 false。 */
    fun add(keyword: String): Boolean {
        val clean = keyword.trim()
        if (clean.isEmpty()) return false
        val current = list()
        if (current.any { it.keyword.equals(clean, ignoreCase = true) }) return false

        save(current + Subscription(clean, System.currentTimeMillis()))
        return true
    }

    fun remove(keyword: String) {
        save(list().filterNot { it.keyword.equals(keyword, ignoreCase = true) })
    }

    /** 检查是否已经推送过这个组合，避免每天反复推同一条。 */
    fun shouldNotify(keyword: String, title: String): Boolean =
        !notifiedKeys().contains(dedupeKey(keyword, title))

    fun markNotified(pairs: List<Pair<String, String>>) {
        if (pairs.isEmpty()) return
        val keys = notifiedKeys().toMutableSet()
        val now = System.currentTimeMillis()
        pairs.forEach { keys += dedupeKey(it.first, it.second) }

        val payload = JSONArray()
        keys.toList().takeLast(MAX_DEDUPE).forEach { key ->
            payload.put(JSONObject().put("k", key).put("t", now))
        }
        prefs.edit().putString(KEY_SEEN, payload.toString()).apply()
    }

    private fun notifiedKeys(): Set<String> {
        val raw = prefs.getString(KEY_SEEN, null) ?: return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.optString("k")?.ifEmpty { null }
            }.toSet()
        }.getOrDefault(emptySet())
    }

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    private fun save(items: List<Subscription>) {
        val array = JSONArray()
        items.forEach {
            array.put(JSONObject().put("k", it.keyword).put("t", it.createdAt))
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun dedupeKey(keyword: String, title: String) =
        keyword.lowercase() + "|" + title.hashCode()

    companion object {
        private const val KEY_ITEMS = "items"
        private const val KEY_SEEN = "seen"
        private const val KEY_ENABLED = "enabled"
        private const val MAX_DEDUPE = 500
    }
}

/** 订阅匹配：关键词做去标点小写包含匹配，中文直接子串命中。 */
object KeywordMatcher {

    private val STRIP = Regex("[^\\p{IsHan}A-Za-z0-9]")

    fun match(events: List<RankedEvent>, keywords: List<String>): List<KeywordHit> =
        keywords.mapNotNull { keyword ->
            val needle = normalize(keyword)
            if (needle.isEmpty()) return@mapNotNull null

            val hits = events.filter { event ->
                normalize(event.title).contains(needle) ||
                    event.members.any { normalize(it.title).contains(needle) }
            }
            if (hits.isEmpty()) null else KeywordHit(keyword, hits)
        }

    private fun normalize(text: String) = STRIP.replace(text, "").lowercase()
}
