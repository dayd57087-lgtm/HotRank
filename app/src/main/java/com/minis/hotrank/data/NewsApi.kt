package com.minis.hotrank.data

import com.minis.hotrank.model.TimelineEntry
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 全网热点新闻源（腾讯新闻热榜）。
 *
 * 为什么需要它：热榜接口**没有任何一条带发布时间** —— 唯一的时间是榜单级的
 * update_time（"榜单什么时候刷新的"），不是"这条新闻什么时候发生的"。
 * 想做真正按发布时间排序的时间线，就必须引入带真实时间的源。
 *
 * 实测该接口能提供：
 *   真实发布时间（标准 Unix 秒级时间戳）/ 摘要 / 配图 / 来源媒体 / 阅读数 / 评论数
 * 配图为绝对 https 地址，可直连。
 */
object NewsApi {

    private const val ENDPOINT =
        "https://r.inews.qq.com/gw/event/hot_ranking_list?page_size=50"

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** 单次最多取 50 条；实测 page_size 超过 50 反而只返回 21 条，所以固定 50。 */
    private const val MAX_ITEMS = 50

    fun fetch(): List<TimelineEntry> {
        val body = runCatching {
            client.newCall(
                Request.Builder()
                    .url(ENDPOINT)
                    .header("User-Agent", UA)
                    .header("Accept", "application/json")
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                response.body?.string()
            }
        }.getOrNull() ?: return emptyList()

        return parse(body)
    }

    private fun parse(json: String): List<TimelineEntry> = runCatching {
        val root = JSONObject(json)
        val groups = root.optJSONArray("idlist") ?: return emptyList()
        if (groups.length() == 0) return emptyList()

        val list = groups.optJSONObject(0)?.optJSONArray("newslist") ?: return emptyList()
        val out = ArrayList<TimelineEntry>(MAX_ITEMS)

        for (i in 0 until minOf(list.length(), MAX_ITEMS)) {
            val obj = list.optJSONObject(i) ?: continue

            val title = obj.optString("title").trim()
            if (title.isEmpty()) continue

            // timestamp 是标准的 Unix 秒级时间戳；个别条目缺失时退回解析 time 字符串
            val seconds = obj.optLong("timestamp", 0L).takeIf { it > 0L }
                ?: parseTimeString(obj.optString("time"))
                ?: continue

            val id = obj.optString("id").ifEmpty { title.hashCode().toString() }

            out += TimelineEntry(
                id = id,
                title = title,
                source = seq(obj, "source", "chlname"),
                publishedAt = seconds * 1000L,
                summary = obj.optString("abstract").trim().ifEmpty { null }?.take(300),
                image = firstImage(obj),
                url = seq(obj, "url", "surl"),
                readCount = obj.optInt("readCount", 0),
                commentCount = obj.optInt("commentNum", 0),
            )
        }
        out.sortedByDescending { it.publishedAt }
    }.getOrDefault(emptyList())

    /** bigImage / thumbnails_big 都是 URL 数组，取第一张。 */
    private fun firstImage(obj: JSONObject): String? {
        for (key in arrayOf("bigImage", "thumbnails_big", "thumbnails")) {
            val array = obj.optJSONArray(key) ?: continue
            if (array.length() == 0) continue
            val raw = array.optString(0).trim()
            if (raw.startsWith("https://")) return raw
            if (raw.startsWith("http://")) return "https://" + raw.removePrefix("http://")
        }
        return null
    }

    private fun seq(obj: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            val value = obj.optString(key).trim()
            if (value.isNotEmpty()) return value
        }
        return ""
    }

    /** "2026-09-11 20:14:52" 按北京时间（UTC+8）解析。 */
    private fun parseTimeString(raw: String): Long? {
        if (raw.isEmpty()) return null
        return runCatching {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
            format.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
            format.parse(raw)?.time?.div(1000L)
        }.getOrNull()
    }
}
