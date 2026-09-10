package com.minis.hotrank

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minis.hotrank.data.HotRepository
import com.minis.hotrank.data.Subscription
import com.minis.hotrank.data.SubscriptionStore
import com.minis.hotrank.model.Platform
import com.minis.hotrank.model.RankedEvent
import com.minis.hotrank.notify.Notifier
import com.minis.hotrank.work.KeywordScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HotUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val events: List<RankedEvent> = emptyList(),
    val failed: List<Platform> = emptyList(),
    val onlineCount: Int = 0,
    val updatedAt: Long = 0L,
    val error: String? = null,
    val subscriptions: List<Subscription> = emptyList(),
    val notifyEnabled: Boolean = false,
    val canPostNotification: Boolean = true,
) {
    /** 多站同榜的事件数 —— 这个数字本身就是产品的卖点。 */
    val corroboratedCount: Int get() = events.count { it.corroboration > 1 }
}

class HotViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = HotRepository(app)
    private val store = SubscriptionStore(app)

    private val _state = MutableStateFlow(HotUiState())
    val state: StateFlow<HotUiState> = _state.asStateFlow()

    init {
        Notifier.ensureChannel(getApplication())
        syncSubscriptions()
        refresh(force = false)
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            val hasData = _state.value.events.isNotEmpty()
            _state.value = _state.value.copy(
                loading = !hasData,
                refreshing = hasData,
                error = null,
            )

            val feed = repo.load(force)

            _state.value = _state.value.copy(
                loading = false,
                refreshing = false,
                events = feed.events,
                failed = feed.failed,
                onlineCount = feed.onlineCount,
                updatedAt = feed.updatedAt,
                error = if (feed.events.isEmpty()) "没能拿到榜单，检查下网络再试" else null,
            )
        }
    }

    // ---------- 订阅 ----------

    private fun syncSubscriptions() {
        _state.value = _state.value.copy(
            subscriptions = store.list(),
            notifyEnabled = store.enabled,
            canPostNotification = Notifier.canNotify(getApplication()),
        )
    }

    /** 返回 false 表示关键词重复。 */
    fun addKeyword(keyword: String): Boolean {
        val ok = store.add(keyword)
        if (ok) onSubscriptionsChanged()
        return ok
    }

    fun removeKeyword(keyword: String) {
        store.remove(keyword)
        onSubscriptionsChanged()
    }

    fun setNotifyEnabled(enabled: Boolean) {
        store.enabled = enabled
        if (enabled) {
            KeywordScheduler.schedule(getApplication())
            KeywordScheduler.runOnce(getApplication())
        } else {
            KeywordScheduler.cancel(getApplication())
        }
        syncSubscriptions()
    }

    fun checkNow() {
        KeywordScheduler.runOnce(getApplication())
    }

    private fun onSubscriptionsChanged() {
        val hasAny = store.list().isNotEmpty()
        // 有订阅就确保后台任务在跑，订阅清空就停掉，不留无用的唤醒
        if (hasAny && store.enabled) {
            KeywordScheduler.schedule(getApplication())
        } else if (!hasAny) {
            KeywordScheduler.cancel(getApplication())
        }
        syncSubscriptions()
    }

    /** 从订阅页返回主界面时刷新权限状态。 */
    fun refreshPermissionState() {
        _state.value = _state.value.copy(
            canPostNotification = Notifier.canNotify(getApplication())
        )
    }
}
