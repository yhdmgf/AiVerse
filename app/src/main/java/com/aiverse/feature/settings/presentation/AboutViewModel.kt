package com.aiverse.feature.settings.presentation

import androidx.lifecycle.ViewModel
import com.aiverse.feature.settings.data.remote.ContributorWallService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 关于页轻量 ViewModel：仅用于把 [ContributorWallService] 交给 AboutSection，
 * 避免往已臃肿的 SettingsViewModel 里再加依赖。取数本身由 AboutSection 内的
 * remember/LaunchedEffect 驱动，本类不持有状态。
 */
@HiltViewModel
class AboutViewModel @Inject constructor(
    val contributorWallService: ContributorWallService
) : ViewModel()
