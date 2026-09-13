package com.minis.hotrank.data

import android.content.Context
import com.minis.hotrank.model.RankedEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * 小组件专用的轻量缓存。
 *
 * 为什么不直接读热榜缓存：那张表存的是 6 个平台的原始条目（含摘要、配图、统计），
 * 几百 KB 的 JSON，小组件只需要最终排好序的十来条标题。
 * 单独存一份小的，解析快、也不怕以后热榜缓存结构变动影响小组件。
 *
 * 存的是**排好序的结果**而不是原始数据 —— 小组件没有能力也没有必要重跑聚合算法。
 */
class WidgetStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("widget_cache", Context.MODE_PRIVATE)

    data class Item(
        val rank: Int,
        val title: String,
        val heat: Int,
        val corroboration: Int,
        val url: String,
    )

    data class Snapshot(val items: List<Item>, val updatedAt: Long) {
        val isEmpty: Boolean get() = items.isEmpty()
    }

    fun write(events: List<RankedEvent>, updatedAt: Long) {
        val array = JSONArray()
        events.take(MAX_ITEMS).forEachIndexed { index, event ->
            array.put(
                JSONObject()
                    .put("r", index + 1)
                    .put("t", event.title)
                    .put("h", event.heatIndex)
                    .put("c", event.corroboration)
                    .put("u", event.url)
            )
        }
        prefs.edit()
            .putString(KEY_ITEMS, array.toString())
            .putLong(KEY_TIME, if (updatedAt > 0L) updatedAt else System.currentTimeMillis())
            .apply()
    }

    fun read(): Snapshot {
        val raw = prefs.getString(KEY_ITEMS, null)
            ?: return Snapshot(emptyList(), 0L)
        val updatedAt = prefs.getLong(KEY_TIME, 0L)

        val items = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val title = obj.optString("t")
                if (title.isEmpty()) return@mapNotNull null
                Item(
                    rank = obj.optInt("r", i + 1),
                    title = title,
                    heat = obj.optInt("h", 0),
                    corroboration = obj.optInt("c", 1),
                    url = obj.optString("u"),
                )
            }
        }.getOrDefault(emptyList())

        return Snapshot(items, updatedAt)
    }

    private companion object {
        const val KEY_ITEMS = "items"
        const val KEY_TIME = "updated_at"
        const val MAX_ITEMS = 10
    }
}
