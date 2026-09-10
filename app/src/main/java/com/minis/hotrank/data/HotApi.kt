package com.minis.hotrank.data

import com.minis.hotrank.model.HotItem
import com.minis.hotrank.model.Platform
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 负责「取原始 JSON + 解析成 HotItem」。
 *
 * 源策略：主源 uapis.cn 覆盖全部 6 个平台；备用源 60s.viki.moe 覆盖其中 3 个。
 * 主源某平台挂了会自动走备用源，两个都挂则返回空列表，由 Repository 决定是否降级到旧缓存。
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

    /** 拉取单个平台的热榜。任何异常都吞掉返回空列表 —— 单源失败不能拖垮整页。 */
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
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            response.body?.string()
        }
    }.getOrNull()

    /** 主源结构：{"type":"weibo","list":[{"index":1,"title":"","url":"","hot_value":""}]} */
    private fun parsePrimary(json: String, platform: Platform): List<HotItem> = runCatching {
        val array = JSONObject(json).optJSONArray("list") ?: return emptyList()
        val out = ArrayList<HotItem>(MAX_ITEMS)
        for (i in 0 until minOf(array.length(), MAX_ITEMS)) {
            val obj = array.optJSONObject(i) ?: continue
            val title = obj.optString("title").trim()
            if (title.isEmpty()) continue
            out += HotItem(
                platform = platform,
                rank = obj.optInt("index", i + 1),
                title = title,
                url = obj.optString("url"),
                hot = formatHot(obj.optString("hot_value")),
            )
        }
        out
    }.getOrDefault(emptyList())

    /** 备用源结构：{"code":200,"data":[{"title":"","hot_value":1,"link":""}]} */
    private fun parseFallback(json: String, platform: Platform): List<HotItem> = runCatching {
        val array = JSONObject(json).optJSONArray("data") ?: return emptyList()
        val out = ArrayList<HotItem>(MAX_ITEMS)
        for (i in 0 until minOf(array.length(), MAX_ITEMS)) {
            val obj = array.optJSONObject(i) ?: continue
            val title = obj.optString("title").trim()
            if (title.isEmpty()) continue
            out += HotItem(
                platform = platform,
                rank = i + 1,
                title = title,
                url = obj.optString("link"),
                hot = formatHot(obj.optString("hot_value")),
            )
        }
        out
    }.getOrDefault(emptyList())

    /**
     * 各家热度格式不统一，实测有四种：
     *   "1834890"(微博) / "3504 万热度"(知乎) / "1395775播放"(B站) / ""(头条)
     * 纯数字 -> 归一成 万/亿；已经带单位的原样保留（可读性更好，别二次加工）。
     */
    private fun formatHot(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
        val number = value.toDoubleOrNull()
            ?: return value.replace(" ", "")
        return when {
            number >= 100_000_000 -> String.format(Locale.CHINA, "%.1f亿", number / 100_000_000)
            number >= 10_000 -> String.format(Locale.CHINA, "%.1f万", number / 10_000)
            else -> number.toInt().toString()
        }
    }

    /** B站一次给 100 条，全量保留没意义，单平台封顶 50。 */
    private const val MAX_ITEMS = 50
}
