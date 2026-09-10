package com.minis.hotrank

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minis.hotrank.data.HotRepository
import com.minis.hotrank.model.HotItem
import com.minis.hotrank.model.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HotUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val merged: List<HotItem> = emptyList(),
    val byPlatform: Map<Platform, List<HotItem>> = emptyMap(),
    val failed: List<Platform> = emptyList(),
    val updatedAt: Long = 0L,
    val error: String? = null,
)

class HotViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = HotRepository(app)

    private val _state = MutableStateFlow(HotUiState())
    val state: StateFlow<HotUiState> = _state.asStateFlow()

    init {
        refresh(force = false)
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            val hasData = _state.value.merged.isNotEmpty()
            _state.value = _state.value.copy(
                loading = !hasData,
                refreshing = hasData,
                error = null,
            )

            val feed = repo.load(force)

            _state.value = HotUiState(
                loading = false,
                refreshing = false,
                merged = feed.merged,
                byPlatform = feed.byPlatform,
                failed = feed.failed,
                updatedAt = feed.updatedAt,
                error = if (feed.merged.isEmpty()) "没能拿到榜单，检查下网络再试" else null,
            )
        }
    }
}
