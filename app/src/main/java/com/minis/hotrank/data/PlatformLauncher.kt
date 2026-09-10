package com.minis.hotrank.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.minis.hotrank.model.HotItem
import com.minis.hotrank.model.Platform

/**
 * 跳转到各平台 App。
 *
 * 一个必须先说清的事实：**各平台给的信息完整度天然不平等**。
 * 接口返回里，知乎有 questionId、B站有 BV 号、头条有 trendingId —— 这些能精确跳到那条内容；
 * 而微博 / 百度 / 抖音只给了关键词，只能跳到该 App 的搜索结果页。
 * 所以界面上会区分「打开」和「搜索」两种状态，不糊弄成一样。
 *
 * 降级链（全程静默，不弹「选择应用」）：
 *   1. App 内精确页   —— 有 contentId 才尝试
 *   2. App 内搜索结果 —— 用关键词
 *   3. https 链接     —— 交给系统，App Links 可能直接唤起 App
 *   4. 系统浏览器     —— 最终兜底
 *
 * 每一层都用 setPackage + resolveActivity 探测，探测不到就往下走。
 * 这些 scheme 是各家 App 的内部约定，可能随版本变化，所以不能写死假设 ——
 * 一定要能无声地降级，而不是报错或弹框。
 */
object PlatformLauncher {

    /** contentId 是什么类型，决定用哪条精确路径。 */
    enum class IdKind { NONE, ZHIHU_QUESTION, BILIBILI_VIDEO, TOUTIAO_TRENDING }

    fun idKindOf(platform: Platform): IdKind = when (platform) {
        Platform.ZHIHU -> IdKind.ZHIHU_QUESTION
        Platform.BILIBILI -> IdKind.BILIBILI_VIDEO
        Platform.TOUTIAO -> IdKind.TOUTIAO_TRENDING
        else -> IdKind.NONE
    }

    /** 能否精确直达该条内容（有 ID 才行）。界面据此显示「打开」还是「搜索」。 */
    fun canOpenPrecisely(item: HotItem): Boolean =
        item.contentId != null && idKindOf(item.platform) != IdKind.NONE

    fun open(context: Context, item: HotItem) {
        val pkg = item.platform.packageName

        preciseUris(item).forEach { if (tryLaunch(context, it, pkg)) return }
        searchUris(item).forEach { if (tryLaunch(context, it, pkg)) return }
        // App Links：https 链接本身可能就能唤起 App
        tryLaunch(context, Uri.parse(item.url), pkg)
        // 最终兜底：浏览器
        openInBrowser(context, item.url)
    }

    /** 外层：从一个事件里挑一条最能跳的（优先能精确直达的）。 */
    fun openBest(context: Context, members: List<HotItem>) {
        val target = members.firstOrNull { canOpenPrecisely(it) } ?: members.firstOrNull()
        target?.let { open(context, it) }
    }

    private fun preciseUris(item: HotItem): List<Uri> {
        val id = item.contentId ?: return emptyList()
        return when (idKindOf(item.platform)) {
            IdKind.ZHIHU_QUESTION -> listOf(
                Uri.parse("zhihu://questions/$id"),
                Uri.parse("zhihu://question/$id"),
            )
            IdKind.BILIBILI_VIDEO -> listOf(
                Uri.parse("bilibili://video/$id"),
                Uri.parse("bilibili://bangumi/play/$id"),
            )
            // 头条的 scheme 不确定，只保留 https（它本身就能唤起 App），不瞎猜
            IdKind.TOUTIAO_TRENDING -> emptyList()
            IdKind.NONE -> emptyList()
        }
    }

    private fun searchUris(item: HotItem): List<Uri> {
        val keyword = Uri.encode(item.title)
        return when (item.platform) {
            Platform.WEIBO -> listOf(
                Uri.parse("sinaweibo://searchall?q=$keyword"),
                Uri.parse("sinaweibo://search?q=$keyword"),
            )
            Platform.ZHIHU -> listOf(
                Uri.parse("zhihu://search?q=$keyword"),
            )
            Platform.DOUYIN -> listOf(
                Uri.parse("snssdk1128://search?keyword=$keyword"),
            )
            Platform.BAIDU -> listOf(
                Uri.parse("baiduboxapp://search?word=$keyword"),
            )
            Platform.BILIBILI -> listOf(
                Uri.parse("bilibili://search?keyword=$keyword"),
            )
            Platform.TOUTIAO -> emptyList()
        }
    }

    /**
     * setPackage 把候选限定到目标 App：装了就一定能处理，没装 resolveActivity 返回 null。
     * 这样既不会弹选择框，也不用额外查 packageManager 的安装状态。
     */
    private fun tryLaunch(context: Context, uri: Uri, packageName: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) return false
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    private fun openInBrowser(context: Context, url: String) {
        if (url.isBlank()) return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
