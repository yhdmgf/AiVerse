package com.aiverse.feature.settings.presentation

import androidx.lifecycle.ViewModel
import com.aiverse.feature.settings.data.repository.GithubStarPromptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 关于页 GitHub Star 提示卡的状态：是否已勾选「不再提示」。
 *
 * 独立于 [SettingsViewModel]，避免 Star 提示的持久化与更新检查等设置耦合，
 * 「不再提示」只影响关于页这一张提示卡的展示，作用域很小。
 */
@HiltViewModel
class GithubStarPromptViewModel @Inject constructor(
    private val repository: GithubStarPromptRepository
) : ViewModel() {

    private val _dismissed = MutableStateFlow(repository.dismissed)

    /** 是否已勾选「不再提示」（持久化，跨进程重启保留）。 */
    val dismissed: StateFlow<Boolean> = _dismissed.asStateFlow()

    /** 勾选「不再提示」：落盘并驱动 UI 隐藏提示卡。 */
    fun dismiss() {
        if (_dismissed.value) return
        _dismissed.value = true
        repository.dismissed = true
    }
}
