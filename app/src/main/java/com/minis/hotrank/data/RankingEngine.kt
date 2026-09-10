package com.minis.hotrank.data

import com.minis.hotrank.model.HotItem
import com.minis.hotrank.model.Platform
import com.minis.hotrank.model.RankedEvent
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 聚合排序引擎 —— 这个 App 最核心的一段逻辑。
 *
 * 要解决的问题：6 个平台的热度值量纲完全不同（微博 183 万、知乎「3504 万热度」、
 * B站「1395775 播放」、头条干脆不给），直接比大小毫无意义，直接轮流插入也不叫聚合。
 *
 * 四步：
 *
 *  1) 平台内归一化
 *     - 热度分 heatScore = ln(1+h) / ln(1+该平台最高热度)   -> 0~1
 *       用对数是因为热度是重尾分布，线性归一会让头部几条把其余全压成 0
 *     - 名次分 rankScore = 1 - (名次-1)/(总数-1)             -> 0~1
 *     - 该平台不提供热度值时，退化为名次分并打 0.85 折，避免白占便宜
 *
 *  2) 加权合成：base = 平台权重 x (0.6 x 热度分 + 0.4 x 名次分)
 *
 *  3) 跨平台事件聚类（星型聚类，只跟组代表比，防止 A~B~C 传递成大杂烩）
 *     标题先归一化（去掉标点/空格/emoji，转小写），再用字符二元组 Jaccard 相似度。
 *     两道守卫：
 *       - 数字冲突否决：两边都含数字且毫无交集 -> 直接判定不是同一事件
 *         （没有这条，"iPhone17Pro降价" 会被并进 "iPhone18Pro" 事件组）
 *       - 包含关系需短串 >= 6 字，防止极短标题被任意长标题吞掉
 *
 *  4) 多站同榜加成：涉及 n 个不同平台 -> x(1 + 0.15 x (n-1))，上限 1.6x
 *     这是聚合榜真正的价值：某条在单个平台排第 47 无人问津，
 *     但三个平台都在推，它就值得排到前面。
 *     实测中「中国女篮无缘世界杯四强」在微博仅第 47 位，因 4 站同榜升到全榜第 2。
 *
 * 最后把分数线性映射成 0~100 的「综合热度」，全榜第一恒为 100。
 */
object RankingEngine {

    private const val W_HEAT = 0.6
    private const val W_RANK = 0.4
    private const val NO_HEAT_PENALTY = 0.85

    private const val SIM_THRESHOLD = 0.40
    private const val MIN_SHORT_LEN = 6
    private const val CONTAINMENT_SCORE = 0.9

    private const val CLUSTER_STEP = 0.15
    private const val CLUSTER_CAP = 1.60

    private val KEEP = Regex("[^\\p{IsHan}A-Za-z0-9]")
    private val DIGITS = Regex("\\d+")

    fun rank(byPlatform: Map<Platform, List<HotItem>>): List<RankedEvent> {
        val scored = scoreAll(byPlatform)
        if (scored.isEmpty()) return emptyList()

        val clusters = cluster(scored)
        val peak = clusters.maxOf { clusterScore(it) }.takeIf { it > 0.0 } ?: 1.0

        return clusters
            .map { toEvent(it, peak) }
            .sortedByDescending { it.score }
    }

    // ---------- 1&2. 归一化 + 加权 ----------

    private class Scored(
        val item: HotItem,
        val platform: Platform,
        val base: Double,
        val norm: String,
    )

    private fun scoreAll(byPlatform: Map<Platform, List<HotItem>>): List<Scored> {
        val out = ArrayList<Scored>()

        byPlatform.forEach { (platform, items) ->
            if (items.isEmpty()) return@forEach

            // 归一化基准用本平台内部的最高热度，这样各平台之间才可比
            val maxHeat = items.mapNotNull { it.rawHeat }.filter { it > 0 }.maxOrNull()
            val total = items.size

            items.forEachIndexed { index, item ->
                val rankScore = if (total <= 1) 1.0 else 1.0 - index.toDouble() / (total - 1)

                val heat = item.rawHeat
                val heatScore = if (heat != null && heat > 0 && maxHeat != null && maxHeat > 0) {
                    ln(1 + heat) / ln(1 + maxHeat)
                } else {
                    null
                }

                val base = if (heatScore == null) {
                    rankScore * NO_HEAT_PENALTY
                } else {
                    W_HEAT * heatScore + W_RANK * rankScore
                }

                out += Scored(item, platform, base * platform.weight, normalize(item.title))
            }
        }
        return out
    }

    // ---------- 3. 聚类 ----------

    private class Cluster(val rep: Scored) {
        val members = ArrayList<Scored>().apply { add(rep) }
        val platforms: List<Platform> by lazy {
            members.sortedByDescending { it.base }.map { it.platform }.distinct()
        }
    }

    private fun cluster(scored: List<Scored>): List<Cluster> {
        val sorted = scored.sortedByDescending { it.base }
        val clusters = ArrayList<Cluster>()

        // 星型聚类：只和组代表比。单链接聚类会让 A~B、B~C 传递合并成一个大杂烩。
        sorted.forEach { candidate ->
            val hit = clusters.firstOrNull {
                similarity(candidate.norm, it.rep.norm) >= SIM_THRESHOLD
            }
            if (hit != null) hit.members += candidate else clusters += Cluster(candidate)
        }
        return clusters
    }

    private fun clusterScore(cluster: Cluster): Double =
        cluster.rep.base * boost(cluster.platforms.size)

    private fun boost(platformCount: Int): Double =
        min(1 + CLUSTER_STEP * (platformCount - 1), CLUSTER_CAP)

    private fun toEvent(cluster: Cluster, peak: Double): RankedEvent {
        val members = cluster.members.sortedByDescending { it.base }
        val platforms = cluster.platforms
        val score = cluster.rep.base * boost(platforms.size)

        return RankedEvent(
            title = cluster.rep.item.title,
            url = cluster.rep.item.url,
            score = score,
            heatIndex = (score / peak * 100.0).roundToInt().coerceIn(0, 100),
            members = members.map { it.item },
            platforms = platforms,
        )
    }

    // ---------- 标题相似度 ----------

    /**
     * 注意：Java 正则的 \w 是 ASCII 语义，不含中文。这里必须显式写 \p{IsHan}，
     * 否则中文会被当成非单词字符整段删掉，归一化后所有标题都变成空串，
     * 结果是全部合并成一个事件。（Python 的 \w 默认 Unicode，行为不同，移植时容易踩。）
     */
    fun normalize(title: String): String = KEEP.replace(title, "").lowercase()

    private fun bigrams(s: String): Set<String> =
        if (s.length <= 1) setOf(s)
        else (0 until s.length - 1).mapTo(HashSet()) { s.substring(it, it + 2) }

    /** 数字冲突否决：两边都含数字且没有交集 -> 一定不是同一件事。 */
    fun hasDigitConflict(a: String, b: String): Boolean {
        val da = DIGITS.findAll(a).map { it.value }.toSet()
        val db = DIGITS.findAll(b).map { it.value }.toSet()
        return da.isNotEmpty() && db.isNotEmpty() && (da intersect db).isEmpty()
    }

    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (hasDigitConflict(a, b)) return 0.0

        val short = if (a.length <= b.length) a else b
        val long = if (a.length <= b.length) b else a
        if (short.length >= MIN_SHORT_LEN && long.contains(short)) return CONTAINMENT_SCORE

        val setA = bigrams(a)
        val setB = bigrams(b)
        if (setA.isEmpty() || setB.isEmpty()) return 0.0
        val inter = setA.count { it in setB }
        val union = setA.size + setB.size - inter
        return if (union == 0) 0.0 else inter.toDouble() / union
    }
}
