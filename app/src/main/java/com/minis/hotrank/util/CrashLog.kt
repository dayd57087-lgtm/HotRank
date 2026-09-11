package com.minis.hotrank.util

import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃现场记录。
 *
 * 为什么要做这个：这是个没有图形调试环境的项目（构建在云端、安装只在手机），
 * 一旦运行时崩溃，能拿到的信息只有"闪退"两个字，无法定位。
 *
 * 做法是接管未捕获异常，把堆栈和时间写进 SharedPreferences。
 * 下次启动时 MainActivity 读出来，用一个可选中复制的页面展示 ——
 * 用户截图回来，就能精确定位到哪一行。
 *
 * 只保留最后一次崩溃：要的是最新现场，不是历史堆积。
 */
object CrashLog {

    private const val PREFS = "crash_log"
    private const val KEY_STACK = "stack"
    private const val KEY_TIME = "time"
    private const val KEY_VERSION = "version"

    data class Report(val stack: String, val time: Long, val version: String)

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true

        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_STACK, error.stackTraceToString())
                    .putLong(KEY_TIME, System.currentTimeMillis())
                    .putString(KEY_VERSION, buildStamp(appContext))
                    .commit() // 进程马上要死，必须用 commit 同步落盘
            }
            // 交回系统默认处理，保证正常的崩溃上报/重启行为不被破坏
            previous?.uncaughtException(thread, error)
        }
    }

    /** 取出并清除。取一次就够，避免每次启动都弹。 */
    fun consume(context: Context): Report? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stack = prefs.getString(KEY_STACK, null)
        if (stack.isNullOrBlank()) return null

        val report = Report(
            stack = stack,
            time = prefs.getLong(KEY_TIME, 0L),
            version = prefs.getString(KEY_VERSION, "").orEmpty(),
        )
        prefs.edit().clear().commit()
        return report
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun buildStamp(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        "${info.versionName} ($code) · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"
    }.getOrDefault("unknown")

    fun formatTime(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(timestamp))
}
