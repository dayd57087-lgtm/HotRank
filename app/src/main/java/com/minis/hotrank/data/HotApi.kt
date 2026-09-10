package com.minis.hotrank.data

import com.minis.hotrank.model.HotItem
import com.minis.hotrank.model.Platform
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 拉取原始 JSON 并解析成 HotItem。
 *
 * 源策略：主源 uapis.cn 覆盖全部 6 个平台；备用源 60s.viki.moe 覆盖其中 3 个。
 * 主源某平台挂了自动走备用源，两个都挂返回空列表，由 Repository 决定是否降级到旧缓存。
 */
object HotApi {

    private const val PRIMARY = "https://uapis.cn/api/v1/misc/hotboard?type=%s"

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val FALLBACK: Map<Platform, String> = mapOf(
        Platform.WEIBO to "https://60s.viki.moe/v2/weibo",
        Platform.ZHIHU to "https://60s.viki.moe/v2/zhihu",
        Platform.TOUTIAO to "https://60s.viki.moe/v2/toutiao",
    )

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** B站一次给 100 条，其余 30~51 条，多的对聚合没意义，单平台封顶 50。 */
    private const val MAX_ITEMS = 50

    private val UNIT_RE = Regex("^([\\d.]+)(万|亿)?")

    // 从 url 里挖内容 ID，用于精确深链
    private val ZHIHU_ID = Regex("/question/(\\d+)")
    private val BILI_ID = Regex("/video/(BV[0-9A-Za-z]+)")
    private val TOUTIAO_ID = Regex("/trending/(\\d+)")

    fun fetch(platform: Platform): List<HotItem> {
        val candidates = buildList {
            add(PRIMARY.format(platform.apiId))
            FALLBACK[platform]?.let { add(it) }
        }

        candidates.forEachIndexed { index, url ->
            val body = get(url) ?: return@forEachIndexed
            val items = if (index == 0) parsePrimary(body, platform) else parseFallback(body, platform)
            if (items.isNotEmpty()) return items
        }
        return emptyList()
    }

    private fun get(url: String): String? = runCatching {
        client.newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .build()
        ).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            response.body?.string()
        }
    }.getOrNull()

    /** 主源结构：{"list":[{"index":1,"title":"","url":"","hot_value":"","extra":{}}]} */
    private fun parsePrimary(json: String, platform: Platform): List<HotItem> = runCatching {
        val array = JSONObject(json).optJSONArray("list") ?: return emptyList()
        val out = ArrayList<HotItem>(MAX_ITEMS)
        for (i in 0 until minOf(array.length(), MAX_ITEMS)) {
            val obj = array.optJSONObject(i) ?: continue
            val title = obj.optString("title").trim()
            if (title.isEmpty()) continue

            val url = obj.optString("url")
            val extra = obj.optJSONObject("extra")
            val heat = obj.optString("hot_value")

            out += HotItem(
                platform = platform,
                rank = obj.optInt("index", i + 1),
                title = title,
                url = url,
                hotLabel = formatHot(heat),
                rawHeat = parseHeat(heat),
                summary = extractSummary(extra),
                image = extractImage(extra),
                contentId = extractContentId(platform, url, extra),
                stats = extractStats(platform, extra),
            )
        }
        out
    }.getOrDefault(emptyList())

    /** 备用源结构：{"data":[{"title":"","hot_value":1,"link":""}]}，没有 extra */
    private fun parseFallback(json: String, platform: Platform): List<HotItem> = runCatching {
        val array = JSONObject(json).optJSONArray("data") ?: return emptyList()
        val out = ArrayList<HotItem>(MAX_ITEMS)
        for (i in 0 until minOf(array.length(), MAX_ITEMS)) {
            val obj = array.optJSONObject(i) ?: continue
            val title = obj.optString("title").trim()
            if (title.isEmpty()) continue
            val url = obj.optString("link")
            val heat = obj.optString("hot_value")

            out += HotItem(
                platform = platform,
                rank = i + 1,
                title = title,
                url = url,
                hotLabel = formatHot(heat),
                rawHeat = parseHeat(heat),
                contentId = extractContentId(platform, url, null),
            )
        }
        out
    }.getOrDefault(emptyList())

    // ---------- extra 字段解析 ----------

    private fun extractSummary(extra: JSONObject?): String? {
        val desc = extra?.optString("desc")?.trim().orEmpty()
        return desc.ifEmpty { null }?.take(300)
    }

    /**
     * 图片可用性实测结论：
     *   百度 img / B站 pic / 抖音 cover 可以直接取到；
     *   知乎的 image 是相对路径，拿到的其实是 HTML 而不是图片，所以直接丢弃。
     *   B站给的是 http:// ，Android 9+ 默认禁明文，换成 https 再交给图片库。
     */
    private fun extractImage(extra: JSONObject?): String? {
        if (extra == null) return null
        val raw = extra.optString("img").ifEmpty { extra.optString("pic") }
            .ifEmpty { extra.optString("cover") }
            .trim()
        if (raw.isEmpty()) return null
        return when {
            raw.startsWith("http://") -> "https://" + raw.removePrefix("http://")
            raw.startsWith("https://") -> raw
            else -> null // 相对路径取不到，宁可不显示也别让详情页顶着个加载失败的框
        }
    }

    private fun extractContentId(platform: Platform, url: String, extra: JSONObject?): String? = when (platform) {
        Platform.ZHIHU -> ZHIHU_ID.find(url)?.groupValues?.get(1)
        Platform.TOUTIAO -> TOUTIAO_ID.find(url)?.groupValues?.get(1)
        Platform.BILIBILI -> extra?.optString("bvid")?.takeIf { it.isNotEmpty() }
            ?: BILI_ID.find(url)?.groupValues?.get(1)
        else -> null
    }

    /** 只有 B站和抖音给了真实统计数字，其余平台没有就返回空，界面自然不显示。 */
    private fun extractStats(platform: Platform, extra: JSONObject?): Map<String, String> {
        if (extra == null) return emptyMap()
        val out = LinkedHashMap<String, String>()

        when (platform) {
            Platform.BILIBILI -> {
                extra.optJSONObject("stat")?.let { stat ->
                    stat.optDouble("view", -1.0).takeIf { it >= 0 }?.let { out["播放"] = humanNumber(it) }
                    stat.optDouble("like", -1.0).takeIf { it >= 0 }?.let { out["点赞"] = humanNumber(it) }
                    stat.optDouble("danmaku", -1.0).takeIf { it >= 0 }?.let { out["弹幕"] = humanNumber(it) }
                }
                extra.optJSONObject("owner")?.optString("name")?.takeIf { it.isNotEmpty() }
                    ?.let { out["UP主"] = it }
                extra.optString("tname")?.takeIf { it.isNotEmpty() }?.let { out["分区"] = it }
            }

            Platform.DOUYIN -> {
                extra.optDouble("view_count", -1.0).takeIf { it >= 0 }
                    ?.let { out["播放"] = humanNumber(it) }
                extra.optDouble("video_count", -1.0).takeIf { it >= 0 }
                    ?.let { out["视频数"] = it.toInt().toString() }
            }

            else -> Unit
        }
        return out
    }

    // ---------- 数值格式化 ----------

    private fun formatHot(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
        val number = value.toDoubleOrNull() ?: return value.replace(" ", "")
        return humanNumber(number)
    }

    fun parseHeat(raw: String?): Double? {
        val value = raw?.trim()?.replace(" ", "") ?: return null
        if (value.isEmpty() || value == "null") return null
        val match = UNIT_RE.find(value) ?: return null
        val base = match.groupValues[1].toDoubleOrNull() ?: return null
        return when (match.groupValues[2]) {
            "万" -> base * 10_000
            "亿" -> base * 100_000_000
            else -> base
        }
    }

    fun humanNumber(number: Double): String = when {
        number >= 100_000_000 -> String.format(Locale.CHINA, "%.1f亿", number / 100_000_000)
        number >= 10_000 -> String.format(Locale.CHINA, "%.1f万", number / 10_000)
        else -> number.toInt().toString()
    }
}
