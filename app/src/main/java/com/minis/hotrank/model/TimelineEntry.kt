package com.minis.hotrank.model

/**
 * 全网热点时间线的一条。
 *
 * 与 HotItem 是两套数据：HotItem 来自热榜（有榜单位置但**没有发布时间**），
 * TimelineEntry 来自新闻源（有真实发布时间但没有榜单概念）。
 * 把两者交叉关联起来，就得到这个 App 独有的一种判断：
 * **哪条新闻是真的全网在聊，哪条只是媒体在发。**
 */
data class TimelineEntry(
    val id: String,
    val title: String,
    val source: String,
    /** 真实发布时间（毫秒）。来源是标准的 Unix 时间戳。 */
    val publishedAt: Long,
    val summary: String?,
    val image: String?,
    val url: String,
    val readCount: Int,
    val commentCount: Int,
    /** 与热榜的关联，可能为空（纯新闻，没上过任何榜单）。 */
    val hotLinks: List<HotLink> = emptyList(),
) {
    /** 有任一关联平台当前仍在线，就算"正在热搜上"。 */
    val isHotNow: Boolean get() = hotLinks.any { it.live }

    /** 上过榜但现在已经掉了。 */
    val isFaded: Boolean get() = hotLinks.isNotEmpty() && hotLinks.none { it.live }
}

/** 一条时间线新闻和某个热榜条目的关联。 */
data class HotLink(
    val platform: Platform,
    /** 该平台上的名次；已掉榜时为 0。 */
    val rank: Int,
    /** true = 此刻仍在该平台榜上；false = 曾上榜、现已掉榜。 */
    val live: Boolean,
)

/** 时间线按发布时间分段。区间写成明确的数字，避免"今天早些时候"这种含糊表述。 */
enum class TimeBucket(val label: String, val maxMinutes: Int) {
    FRESH("1 小时内", 60),
    ONE_TO_THREE("1~3 小时前", 180),
    THREE_TO_SIX("3~6 小时前", 360),
    SIX_TO_DAY("6~24 小时前", 1440),
}
