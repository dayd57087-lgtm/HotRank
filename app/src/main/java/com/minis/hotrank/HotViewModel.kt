package com.minis.hotrank

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minis.hotrank.data.AppSettings
import com.minis.hotrank.data.DetailForm
import com.minis.hotrank.data.HotRepository
import com.minis.hotrank.data.Subscription
import com.minis.hotrank.data.SubscriptionStore
import com.minis.hotrank.model.HotItem
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
    val byPlatform: Map<Platform, List<HotItem>> = emptyMap(),
    val failed: List<Platform> = emptyList(),
    val onlineCount: Int = 0,
    val updatedAt: Long = 0L,
    val error: String? = null,
    val subscriptions: List<Subscription> = emptyList(),
    val notifyEnabled: Boolean = false,
    val detailForm: DetailForm = DetailForm.FULLSCREEN,
    val showCrossLink: Boolean = true,
) {
    val corroboratedCount: Int get() = events.count { it.corroboration > 1 }
}

class HotViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = HotRepository(app)
    private val store = SubscriptionStore(app)
    private val settings = AppSettings(app)

    private val _state = MutableStateFlow(HotUiState())
    val state: StateFlow<HotUiState> = _state.asStateFlow()

    /** "平台|标题" -> 综合榜名次。列表里做跨榜衔接标记用。 */
    private var crossLink: Map<String, Int> = emptyMap()

    init {
        Notifier.ensureChannel(getApplication())
        syncSubscriptions()
        _state.value = _state.value.copy(
            detailForm = settings.detailForm,
            showCrossLink = settings.showCrossLink,
        )
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
            crossLink = feed.crossLink

            _state.value = _state.value.copy(
                loading = false,
                refreshing = false,
                events = feed.events,
                byPlatform = feed.byPlatform,
                failed = feed.failed,
                onlineCount = feed.onlineCount,
                updatedAt = feed.updatedAt,
                error = if (feed.events.isEmpty()) "没能拿到榜单，检查下网络再试" else null,
            )
        }
    }

    /**
     * 某条原始榜单条目在综合榜上的名次。
     * 用来在平台原始榜里标出「上综合榜第 N」—— 让用户明白两个榜的关系不是割裂的。
     */
    fun aggregateRankOf(item: HotItem): Int? =
        crossLink[repo.crossLinkKey(item.platform, item.title)]

    // ---------- 设置 ----------

    fun setDetailForm(form: DetailForm) {
        settings.detailForm = form
        _state.value = _state.value.copy(detailForm = form)
    }

    fun setShowCrossLink(enabled: Boolean) {
        settings.showCrossLink = enabled
        _state.value = _state.value.copy(showCrossLink = enabled)
    }

    // ---------- 订阅 ----------

    private fun syncSubscriptions() {
        _state.value = _state.value.copy(
            subscriptions = store.list(),
            notifyEnabled = store.enabled,
        )
    }

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
        if (hasAny && store.enabled) {
            KeywordScheduler.schedule(getApplication())
        } else if (!hasAny) {
            KeywordScheduler.cancel(getApplication())
        }
        syncSubscriptions()
    }
}
