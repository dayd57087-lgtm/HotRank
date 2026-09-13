package com.minis.hotrank.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.minis.hotrank.MainActivity
import com.minis.hotrank.R
import com.minis.hotrank.data.WidgetStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 桌面小组件：不用打开 App 就能看综合榜前 5 条。
 *
 * 用传统的 RemoteViews 而不是 Glance —— 这个项目只能在云端构建、无法本地运行验证，
 * Glance 会额外引入一组 Compose 依赖，版本冲突的代价是一次失败的构建轮次。
 * RemoteViews 零新依赖、API 稳定，对一个静态列表来说完全够用。
 *
 * 数据来源：WidgetStore 里预排好序的快照（不在这里跑聚合算法，也不做网络请求）。
 * 网络刷新交给 WidgetWorker 定时做，App 自己刷新成功后也会顺手写一份。
 */
class HotRankWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        render(context, appWidgetManager, appWidgetIds)
        // 挂着小组件就让它保持有数据：这里只是把刷新任务排上队，不阻塞渲染
        WidgetUpdater.schedulePeriodic(context)
        WidgetUpdater.refreshSoon(context)
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.schedulePeriodic(context)
        WidgetUpdater.refreshSoon(context)
    }

    override fun onDisabled(context: Context) {
        // 最后一个小组件被移除就停掉定时任务，不留无用唤醒
        WidgetUpdater.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            WidgetUpdater.refreshSoon(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.minis.hotrank.WIDGET_REFRESH"
        private const val ROWS = 5

        private val ROW_IDS = intArrayOf(
            R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3,
            R.id.widget_row_4, R.id.widget_row_5,
        )
        private val RANK_IDS = intArrayOf(
            R.id.widget_rank_1, R.id.widget_rank_2, R.id.widget_rank_3,
            R.id.widget_rank_4, R.id.widget_rank_5,
        )
        private val TITLE_IDS = intArrayOf(
            R.id.widget_title_1, R.id.widget_title_2, R.id.widget_title_3,
            R.id.widget_title_4, R.id.widget_title_5,
        )
        private val BADGE_IDS = intArrayOf(
            R.id.widget_badge_1, R.id.widget_badge_2, R.id.widget_badge_3,
            R.id.widget_badge_4, R.id.widget_badge_5,
        )

        /** 外部数据变化后调用，把最新快照刷到桌面。传入 null 表示刷新所有实例。 */
        fun renderAll(context: Context, ids: IntArray? = null) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val target = ids ?: manager.getAppWidgetIds(
                ComponentName(context, HotRankWidget::class.java)
            )
            if (target.isEmpty()) return
            render(context, manager, target)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val snapshot = WidgetStore(context).read()

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_hotrank)

                // 头部：更新时间 + 整块点击进 App
                views.setTextViewText(
                    R.id.widget_updated,
                    if (snapshot.updatedAt > 0L) clock(snapshot.updatedAt) + " 更新" else "尚未更新",
                )
                views.setOnClickPendingIntent(R.id.widget_header, openApp(context))

                // 手动刷新
                val refreshIntent = Intent(context, HotRankWidget::class.java).apply {
                    action = ACTION_REFRESH
                }
                views.setOnClickPendingIntent(
                    R.id.widget_refresh,
                    PendingIntent.getBroadcast(
                        context,
                        REQUEST_REFRESH,
                        refreshIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )

                if (snapshot.isEmpty) {
                    // 还没有数据（刚添加小组件、后台还没跑完）：给一句可操作的提示，
                    // 而不是摆一个空白框让人以为坏了
                    views.setTextViewText(R.id.widget_empty, "下拉打开 App 加载榜单")
                    views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_empty, View.GONE)
                }

                repeat(ROWS) { index ->
                    val item = snapshot.items.getOrNull(index)
                    if (item == null) {
                        views.setViewVisibility(ROW_IDS[index], View.GONE)
                    } else {
                        views.setViewVisibility(ROW_IDS[index], View.VISIBLE)
                        views.setTextViewText(RANK_IDS[index], item.rank.toString())
                        views.setInt(
                            RANK_IDS[index],
                            "setTextColor",
                            if (item.rank == 1) COLOR_RED else COLOR_MUTED,
                        )
                        views.setTextViewText(TITLE_IDS[index], item.title)

                        if (item.corroboration > 1) {
                            views.setViewVisibility(BADGE_IDS[index], View.VISIBLE)
                            views.setTextViewText(BADGE_IDS[index], "${item.corroboration}站")
                        } else {
                            views.setViewVisibility(BADGE_IDS[index], View.GONE)
                        }

                        // 点条目直接打开对应内容（和 App 内列表的点击行为保持一致）
                        views.setOnClickPendingIntent(
                            ROW_IDS[index],
                            openUrl(context, item.url, index),
                        )
                    }
                }

                manager.updateAppWidget(id, views)
            }
        }

        private fun openApp(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                REQUEST_OPEN_APP,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun openUrl(context: Context, url: String, index: Int): PendingIntent {
            val intent = if (url.isBlank()) {
                Intent(context, MainActivity::class.java)
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return PendingIntent.getActivity(
                context,
                REQUEST_OPEN_ITEM + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun clock(timestamp: Long): String =
            SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))

        private const val REQUEST_REFRESH = 1100
        private const val REQUEST_OPEN_APP = 1101
        private const val REQUEST_OPEN_ITEM = 1110

        private val COLOR_RED = Color.parseColor("#C4342A")
        private val COLOR_MUTED = Color.parseColor("#9C9385")
    }
}
