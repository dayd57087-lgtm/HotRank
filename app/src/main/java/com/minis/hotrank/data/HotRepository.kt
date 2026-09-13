package com.minis.hotrank.data

import android.content.Context
import com.minis.hotrank.model.HotItem
import com.minis.hotrank.model.Platform
import com.minis.hotrank.model.RankedEvent
import com.minis.hotrank.model.TimeBucket
import com.minis.hotrank.model.TimelineEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

data class HotFeed(
    val events: List<RankedEvent>,
    val byPlatform: Map<Platform, List<HotItem>>,
    val failed: List<Platform>,
    val onlineCount: Int,
    val updatedAt: Long,
    /** "平台|标题" -> 该条在综合榜上的名次，用于原始榜里的「上综合榜第N」衔接标记。 */
    val crossLink: Map<String, Int>,
    /** 已按发布时间倒序、且过滤到 24 小时内的全网热点。 */
    val timeline: List<TimelineEntry>,
    /** 时间线拉到没有（源挂了），用来区分"没内容"和"没拿到"。 */
    val timelineFailed: Boolean,
)

/**
 * 缓存 + 调度。
 *
 * 缓存按「平台原始列表」存，不存排序结果 —— 排序是纯计算，每次重新跑，
 * 这样以后调算法参数不会让老缓存变成脏数据。extra 字段一并缓存，
 * 否则详情页会没摘要没图。
 *
 * 时间线不走缓存：它的价值就在于"新"，读到旧的就失去意义了。
 */
class HotRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("hot_cache", Context.MODE_PRIVATE)
    private val seenStore = SeenHotStore(appContext)

    /**
     * @param includeTimeline 是否同时拉全网热点。
     *   小组件只关心综合榜排序结果，不需要新闻，传 false 可以省一次网络请求。
     */
    suspend fun load(force: Boolean, includeTimeline: Boolean = true): HotFeed = coroutineScope {
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

        val events = RankingEngine.rank(byPlatform)

        // 综合榜名次反查表：让用户在某个平台的原始榜里，也能看到这条"在两个榜上都出现了"
        val crossLink = HashMap<String, Int>(events.size * 2)
        events.forEachIndexed { index, event ->
            event.members.forEach { member ->
                crossLink[key(member.platform, member.title)] = index + 1
            }
        }

        // 时间线：记录当前榜 -> 拉新闻 -> 交叉关联
        val timelineDeferred = async(Dispatchers.IO) {
            if (byPlatform.isNotEmpty()) seenStore.record(byPlatform)
            if (!includeTimeline) return@async null

            val live = seenStore.liveIndex(byPlatform)
            val cutoff = System.currentTimeMillis() - DAY

            val news = NewsApi.fetch()
            if (news.isEmpty()) return@async null

            news.asSequence()
                .filter { it.publishedAt >= cutoff }
                .map { entry -> entry.copy(hotLinks = seenStore.linksFor(entry, live)) }
                .toList()
        }

        val timeline = timelineDeferred.await()

        HotFeed(
            events = events,
            byPlatform = byPlatform,
            failed = failed,
            onlineCount = byPlatform.size,
            updatedAt = updatedAt,
            crossLink = crossLink,
            timeline = timeline.orEmpty(),
            timelineFailed = timeline == null,
        )
    }

    fun crossLinkKey(platform: Platform, title: String) = key(platform, title)

    private fun key(platform: Platform, title: String) = platform.apiId + "|" + title

    private class Slot(val items: List<HotItem>, val updatedAt: Long)

    private fun loadOne(platform: Platform, force: Boolean): Slot {
        val listKey = "list2_" + platform.apiId
        val timeKey = "ts2_" + platform.apiId
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
            val stats = JSONObject()
            item.stats.forEach { (k, v) -> stats.put(k, v) }
            array.put(
                JSONObject()
                    .put("r", item.rank)
                    .put("t", item.title)
                    .put("u", item.url)
                    .put("hl", item.hotLabel ?: "")
                    .put("hn", item.rawHeat ?: 0.0)
                    .put("s", item.summary ?: "")
                    .put("i", item.image ?: "")
                    .put("c", item.contentId ?: "")
                    .put("st", stats)
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

                val stats = LinkedHashMap<String, String>()
                obj.optJSONObject("st")?.let { st ->
                    st.keys().forEach { k -> stats[k] = st.optString(k) }
                }

                val heat = obj.optDouble("hn", 0.0)
                out += HotItem(
                    platform = platform,
                    rank = obj.optInt("r", i + 1),
                    title = title,
                    url = obj.optString("u"),
                    hotLabel = obj.optString("hl").ifEmpty { null },
                    rawHeat = if (heat > 0.0) heat else null,
                    summary = obj.optString("s").ifEmpty { null },
                    image = obj.optString("i").ifEmpty { null },
                    contentId = obj.optString("c").ifEmpty { null },
                    stats = stats,
                )
            }
            out
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** 把时间线按发布时间分段，空段不返回。 */
    fun bucketOf(entry: TimelineEntry): TimeBucket {
        val minutes = ((System.currentTimeMillis() - entry.publishedAt) / 60_000L).toInt()
        return TimeBucket.entries.firstOrNull { minutes < it.maxMinutes } ?: TimeBucket.SIX_TO_DAY
    }

    companion object {
        private const val TTL = 10 * 60 * 1000L
        private const val DAY = 24 * 60 * 60 * 1000L
    }
}
