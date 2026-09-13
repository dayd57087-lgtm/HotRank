package com.minis.hotrank.data

import android.content.Context

/** 详情页的呈现形式，可在设置里切换。 */
enum class DetailForm { FULLSCREEN, SHEET }

/**
 * 通用设置。跟订阅分开存 —— 订阅是有业务含义的数据，设置只是偏好。
 */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("hot_settings", Context.MODE_PRIVATE)

    /**
     * 默认全屏：详情页的配图和摘要在全屏下有足够的呼吸空间，
     * 塞进底部面板会显得很挤。想要"不丢失列表位置"的用户可以切到面板。
     */
    var detailForm: DetailForm
        get() = runCatching {
            DetailForm.valueOf(prefs.getString(KEY_DETAIL_FORM, null) ?: DetailForm.FULLSCREEN.name)
        }.getOrDefault(DetailForm.FULLSCREEN)
        set(value) = prefs.edit().putString(KEY_DETAIL_FORM, value.name).apply()

    /** 平台原始榜页是否显示「上综合榜第N」的衔接标记。 */
    var showCrossLink: Boolean
        get() = prefs.getBoolean(KEY_CROSS_LINK, true)
        set(value) = prefs.edit().putBoolean(KEY_CROSS_LINK, value).apply()

    private companion object {
        const val KEY_DETAIL_FORM = "detail_form"
        const val KEY_CROSS_LINK = "cross_link"
    }
}
