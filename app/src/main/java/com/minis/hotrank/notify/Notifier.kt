package com.minis.hotrank.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.minis.hotrank.MainActivity
import com.minis.hotrank.R
import com.minis.hotrank.data.KeywordHit

object Notifier {

    private const val CHANNEL_ID = "hot_keyword_matches"
    private const val NOTIFICATION_ID = 3001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "关键词上榜提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "订阅的关键词出现在热搜榜时提醒你"
            }
        )
    }

    /** Android 13+ 要用户明确授权才能发通知。 */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notifyMatches(context: Context, hits: List<KeywordHit>) {
        if (hits.isEmpty() || !canNotify(context)) return
        ensureChannel(context)

        val total = hits.sumOf { it.events.size }
        val title = if (hits.size == 1) {
            "「${hits.first().keyword}」上榜了"
        } else {
            "${hits.size} 个关键词上榜"
        }

        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        var shown = 0
        hits.forEach { hit ->
            hit.events.forEach { event ->
                if (shown < 6) {
                    val tag = if (event.corroboration > 1) " · ${event.corroboration}站同榜" else ""
                    style.addLine("【${hit.keyword}】${event.title}$tag")
                    shown++
                }
            }
        }
        if (total > shown) style.setSummaryText("另有 ${total - shown} 条")

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hot)
            .setContentTitle(title)
            .setContentText(hits.first().events.first().title)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
