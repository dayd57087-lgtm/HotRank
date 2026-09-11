package com.minis.hotrank.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.minis.hotrank.model.HotItem
import com.minis.hotrank.model.Platform

/**
 * 跳转到各平台 App。
 *
 * 核心逻辑：**先看这个 App 装没装，装了就直接跳 App，没装才落到浏览器。**
 *
 * ⚠️ 有一个必须踩过的坑：Android 11（API 30）起有「软件包可见性」限制。
 * 只要 targetSdk >= 30，PackageManager 默认**查不到**任何没在 AndroidManifest 的
 * <queries> 里声明过的包 —— 表现为 getPackageInfo 抛 NameNotFound、
 * resolveActivity 返回 null。如果只靠 resolveActivity 判断，结果就是
 * 「明明装了 App，却每次都降级到浏览器」。
 * 所以 manifest 里必须显式声明这 6 个包名，见 <queries> 段。
 *
 * 各平台能跳到的精度不一样（接口给的信息完整度不同）：
 *   知乎 / B站 / 头条 有内容 ID  -> 能精确跳到那条内容
 *   微博 / 百度 / 抖音 只有关键词 -> 只能跳到该 App 的搜索结果页
 * 界面据此显示「打开」或「搜索」，不糊弄成一样。
 */
object PlatformLauncher {

    /** 跳转的最终去向，用来给界面提示用。 */
    enum class Result { PRECISE_IN_APP, SEARCH_IN_APP, BROWSER, NOTHING }

    /**
     * 判断 App 是否已安装。
     * matchDirectBootAwareAlpha 之类的新 flag 不必要，用最朴素的查询即可。
     */
    fun isInstalled(context: Context, platform: Platform): Boolean = runCatching {
        context.packageManager.getPackageInfo(platform.packageName, 0)
        true
    }.getOrDefault(false)

    /** 有内容 ID 才能精确直达；否则只能到搜索结果页。界面用它决定按钮文案。 */
    fun canOpenPrecisely(item: HotItem): Boolean =
        item.contentId != null && preciseUris(item).isNotEmpty()

    /**
     * 入口：先判断装没装。
     *   装了  -> 精确页 -> 搜索页 -> https(App Links) -> 浏览器
     *   没装  -> 直接浏览器
     */
    fun open(context: Context, item: HotItem): Result {
        val platform = item.platform

        if (!isInstalled(context, platform)) {
            openInBrowser(context, item.url)
            return Result.BROWSER
        }

        for (uri in preciseUris(item)) {
            if (launchInApp(context, uri, platform.packageName)) return Result.PRECISE_IN_APP
        }
        for (uri in searchUris(item)) {
            if (launchInApp(context, uri, platform.packageName)) return Result.SEARCH_IN_APP
        }
        // App 装了但上面的 scheme 都没接住：试试 https 交给 App Links
        if (launchInApp(context, Uri.parse(item.url), platform.packageName)) {
            return Result.PRECISE_IN_APP
        }

        openInBrowser(context, item.url)
        return Result.BROWSER
    }

    /** 从事件的多条成员里挑一条最适合跳的（优先能精确直达的）。 */
    fun openBest(context: Context, members: List<HotItem>): Result {
        val target = members.firstOrNull { canOpenPrecisely(it) } ?: members.firstOrNull()
            ?: return Result.NOTHING
        return open(context, target)
    }

    // ---------- 精确路径（需要内容 ID）----------

    private fun preciseUris(item: HotItem): List<Uri> {
        val id = item.contentId ?: return emptyList()
        return when (item.platform) {
            Platform.ZHIHU -> listOf(
                Uri.parse("zhihu://questions/$id"),
                Uri.parse("zhihu://question/$id"),
            )
            Platform.BILIBILI -> listOf(
                Uri.parse("bilibili://video/$id"),
                Uri.parse("bilibili://bangumi/play/$id"),
            )
            // 头条的私有 scheme 没有可靠公开信息，不瞎猜；
            // 它装了的话会走下面的 https + App Links 那条路
            Platform.TOUTIAO -> emptyList()
            else -> emptyList()
        }
    }

    // ---------- 搜索路径（只有关键词）----------

    private fun searchUris(item: HotItem): List<Uri> {
        val keyword = Uri.encode(item.title)
        return when (item.platform) {
            Platform.WEIBO -> listOf(
                Uri.parse("sinaweibo://searchall?q=$keyword"),
                Uri.parse("sinaweibo://search?q=$keyword"),
                Uri.parse("sinaweibo://sosearch?q=$keyword"),
            )
            Platform.ZHIHU -> listOf(
                Uri.parse("zhihu://search?q=$keyword"),
                Uri.parse("zhihu://search?query=$keyword"),
            )
            Platform.DOUYIN -> listOf(
                Uri.parse("snssdk1128://search?keyword=$keyword"),
                Uri.parse("snssdk1128://search/result?keyword=$keyword"),
            )
            Platform.BAIDU -> listOf(
                Uri.parse("baiduboxapp://search?word=$keyword"),
                Uri.parse("baiduboxapp://v1/browser/search?word=$keyword"),
            )
            Platform.BILIBILI -> listOf(
                Uri.parse("bilibili://search?keyword=$keyword"),
            )
            Platform.TOUTIAO -> emptyList()
        }
    }

    // ---------- 实际启动 ----------

    /**
     * setPackage 把候选限定到目标 App：装了就一定能处理，没装 resolveActivity 直接 null。
     * 这样既不会弹「选择应用」框，也不依赖软件包可见性去猜。
     */
    private fun launchInApp(context: Context, uri: Uri, packageName: String): Boolean = runCatching {
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
