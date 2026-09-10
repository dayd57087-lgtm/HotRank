package com.minis.hotrank.model

/**
 * 一条原始热榜条目（来自某个平台的某个名次）。
 *
 * hotLabel 是给人看的文本（"183.5万" / "3504万热度" / "1395775播放"），
 * rawHeat 是解析出来的数值，只参与归一化计算，不直接展示。
 * 两者分开是因为各平台的热度格式完全不统一，而排序又必须要数值。
 */
data class HotItem(
    val platform: Platform,
    val rank: Int,
    val title: String,
    val url: String,
    val hotLabel: String?,
    val rawHeat: Double?,
)

/**
 * 平台。weight 是产品判断，不是技术参数 —— 它决定各平台在综合热度里的分量。
 * 数值集中放在这里，方便以后统一调整或做成用户可配置。
 */
enum class Platform(
    val apiId: String,
    val label: String,
    val argb: Long,
    val weight: Double,
) {
    WEIBO("weibo", "微博", 0xFFE6162D, 1.00),
    BAIDU("baidu", "百度", 0xFF2932E1, 0.95),
    DOUYIN("douyin", "抖音", 0xFFFE2C55, 0.95),
    TOUTIAO("toutiao", "头条", 0xFFF04142, 0.90),
    ZHIHU("zhihu", "知乎", 0xFF0084FF, 0.88),
    BILIBILI("bilibili", "B站", 0xFFFB7299, 0.85),
}

/**
 * 聚合后的「事件」。一个事件可能由多个平台的条目共同支撑。
 *
 * heatIndex     0~100 的综合热度，全榜最高的事件恒为 100，用于展示
 * members       参与该事件的原始条目，按贡献度降序
 * platforms     去重后的平台列表（按贡献度降序），size >= 2 即「多站同榜」
 */
data class RankedEvent(
    val title: String,
    val url: String,
    val score: Double,
    val heatIndex: Int,
    val members: List<HotItem>,
    val platforms: List<Platform>,
) {
    val corroboration: Int get() = platforms.size

    /** 该事件在贡献最大的那个平台上的名次，用于「微博 #2」这类展示。 */
    val topRank: Int get() = members.firstOrNull()?.rank ?: 0
}
