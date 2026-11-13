package com.aiverse.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.UIDensity
import com.aiverse.core.ui.SegmentedTabs

/**
 * 界面密度子页面：从「外观设置」进入，三档（紧致/标准/宽松）实时切换并持久化。
 *
 * 顶栏标题与返回由 [com.aiverse.feature.settings.presentation.component.SettingsScreen]
 * 的 Scaffold 统一提供（见 [SettingsSection.AppearanceDensity]），本 Composable 只负责内容体，
 * 结构与 [AppearanceSettingsSection] 等设置子页面一致（可滚动 Column + SettingsGroup）。
 */
@Composable
internal fun DensitySettingsSection(
    uiDensity: UIDensity,
    onSetUiDensity: (UIDensity) -> Unit
) {
    val options = UIDensity.entries
    val labels = listOf(
        stringResource(R.string.density_compact),
        stringResource(R.string.density_standard),
        stringResource(R.string.density_comfortable)
    )
    val t = LocalUIDensityTokens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(t.sm)
    ) {
        SettingsGroupHeader(text = stringResource(R.string.settings_appearance_density))
        SettingsGroup {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                Text(
                    text = stringResource(R.string.settings_appearance_density),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = Spacing.sm)
                )
                SegmentedTabs(
                    selected = options.indexOf(uiDensity).coerceAtLeast(0),
                    labels = labels,
                    onSelect = { index -> onSetUiDensity(options[index]) }
                )
            }
        }
    }
}
