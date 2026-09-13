package com.minis.hotrank.model

/**
 * 一条原始热榜条目。
 *
 * hotLabel 给人看（"183.5万" / "3504万热度"），rawHeat 参与归一化计算，两者分开。
 * summary / image / contentId / stats 来自接口的 extra 字段，各平台给的完整度差别很大：
 *   知乎 desc+image(相对路径，实际取不到图) / 百度 desc+img / 抖音 cover+播放数 /
 *   B站 desc+pic+BV号+完整统计 / 微博 无 / 头条 无
 * 所以详情页必须能逐级降级，缺什么就少显示什么，不能假设一定存在。
 */
data class HotItem(
    val platform: Platform,
    val rank: Int,
    val title: String,
    val url: String,
    val hotLabel: String?,
    val rawHeat: Double?,
    val summary: String? = null,
    val image: String? = null,
    val contentId: String? = null,
    val stats: Map<String, String> = emptyMap(),
)

/**
 * packageName 用于把跳转精确限定到目标 App，避免系统弹「选择应用」。
 * nature 描述该平台**以什么形式**承载一个话题 —— 这是「各平台对比」的核心信息：
 * 同一件事，微博上是话题讨论、知乎上是问答、B站上是视频，呈现方式完全不同。
 */
enum class Platform(
    val apiId: String,
    val label: String,
    val argb: Long,
    val weight: Double,
    val packageName: String,
    val short: String,
    val nature: String,
) {
    WEIBO("weibo", "微博", 0xFFE6162D, 1.00, "com.sina.weibo", "微博", "话题讨论"),
    BAIDU("baidu", "百度", 0xFF2932E1, 0.95, "com.baidu.searchbox", "百度", "资讯搜索"),
    DOUYIN("douyin", "抖音", 0xFFFE2C55, 0.95, "com.ss.android.ugc.aweme", "抖音", "短视频"),
    TOUTIAO("toutiao", "头条", 0xFFF04142, 0.90, "com.ss.android.article.news", "头条", "资讯报道"),
    ZHIHU("zhihu", "知乎", 0xFF0084FF, 0.88, "com.zhihu.android", "知乎", "问答讨论"),
    BILIBILI("bilibili", "B站", 0xFFFB7299, 0.85, "tv.danmaku.bili", "B站", "视频"),
}

/** 聚合后的「事件」：可能由多个平台的条目共同支撑。 */
data class RankedEvent(
    val title: String,
    val url: String,
    val score: Double,
    val heatIndex: Int,
    val members: List<HotItem>,
    val platforms: List<Platform>,
) {
    val corroboration: Int get() = platforms.size
    val topRank: Int get() = members.firstOrNull()?.rank ?: 0
}

/**
 * 详情页的数据载体。综合榜传一个事件的全部成员，平台原始榜传单条，
 * 这样同一个详情页能服务两种入口，不需要两套界面。
 */
data class DetailData(
    val title: String,
    val heatIndex: Int?,
    val members: List<HotItem>,
) {
    val corroboration: Int get() = members.map { it.platform }.distinct().size

    /** 各平台给的配图里挑一张能用的。 */
    val image: String? get() = members.firstNotNullOfOrNull { it.image }

    /** 摘要取最长的那个 —— 通常也是最完整的一条。 */
    val summary: String? get() = members
        .mapNotNull { it.summary?.trim() }
        .filter { it.isNotEmpty() }
        .maxByOrNull { it.length }
}

fun RankedEvent.toDetail() = DetailData(title, heatIndex, members)

fun HotItem.toDetail() = DetailData(title, null, listOf(this))
