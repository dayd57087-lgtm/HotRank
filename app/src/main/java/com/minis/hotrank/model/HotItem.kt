package com.minis.hotrank.model

/** 一条热榜条目。所有数据源统一成这个结构。 */
data class HotItem(
    val platform: Platform,
    val rank: Int,
    val title: String,
    val url: String,
    val hot: String?,
)

/**
 * 平台枚举。apiId 是各家接口的类型参数，新增平台只需要在这里加一行 + 确认接口支持。
 * argb 用于列表里的平台标签配色。
 */
enum class Platform(val apiId: String, val label: String, val argb: Long) {
    WEIBO("weibo", "微博", 0xFFE6162D),
    ZHIHU("zhihu", "知乎", 0xFF0084FF),
    BAIDU("baidu", "百度", 0xFF2932E1),
    TOUTIAO("toutiao", "头条", 0xFFF04142),
    DOUYIN("douyin", "抖音", 0xFFFE2C55),
    BILIBILI("bilibili", "B站", 0xFFFB7299),
}
