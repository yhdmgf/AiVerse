package com.aiverse.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aiverse.R
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import com.aiverse.feature.settings.data.repository.AppearanceSettingsRepository
import compose.icons.FeatherIcons
import compose.icons.feathericons.RotateCcw
import kotlin.math.roundToInt

/**
 * 独立圆角子页面：从「外观设置」进入，5 个圆角（卡片/按钮/输入框/气泡/图片）分别可调。
 *
 * 顶栏标题与返回由 [com.aiverse.feature.settings.presentation.component.SettingsScreen]
 * 的 Scaffold 统一提供（见 [SettingsSection.AppearanceCorner]），本 Composable 只负责内容体，
 * 结构与 [DensitySettingsSection] / [AppearanceSettingsSection] 等设置子页面一致（可滚动 Column + SettingsGroup）。
 */
@Composable
internal fun CornerRadiusSettingsSection(
    cardCorner: Int,
    buttonCorner: Int,
    fieldCorner: Int,
    bubbleCorner: Int,
    imageCorner: Int,
    onSetCardCorner: (Int) -> Unit,
    onSetButtonCorner: (Int) -> Unit,
    onSetFieldCorner: (Int) -> Unit,
    onSetBubbleCorner: (Int) -> Unit,
    onSetImageCorner: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        SettingsGroupHeader(text = stringResource(R.string.settings_appearance_corner))
        SettingsGroup {
            CornerSliderRow(
                title = stringResource(R.string.corner_card),
                value = cardCorner,
                onSet = onSetCardCorner
            )
            SettingsDivider()
            CornerSliderRow(
                title = stringResource(R.string.corner_button),
                value = buttonCorner,
                onSet = onSetButtonCorner
            )
            SettingsDivider()
            CornerSliderRow(
                title = stringResource(R.string.corner_input),
                value = fieldCorner,
                onSet = onSetFieldCorner
            )
            SettingsDivider()
            CornerSliderRow(
                title = stringResource(R.string.corner_bubble),
                value = bubbleCorner,
                onSet = onSetBubbleCorner
            )
            SettingsDivider()
            CornerSliderRow(
                title = stringResource(R.string.corner_image),
                value = imageCorner,
                onSet = onSetImageCorner
            )
        }

        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.RotateCcw,
                title = stringResource(R.string.settings_appearance_reset),
                onClick = {
                    onSetCardCorner(AppearanceSettingsRepository.DEFAULT_CARD_CORNER)
                    onSetButtonCorner(AppearanceSettingsRepository.DEFAULT_BUTTON_CORNER)
                    onSetFieldCorner(AppearanceSettingsRepository.DEFAULT_FIELD_CORNER)
                    onSetBubbleCorner(AppearanceSettingsRepository.DEFAULT_BUBBLE_CORNER)
                    onSetImageCorner(AppearanceSettingsRepository.DEFAULT_IMAGE_CORNER)
                }
            )
        }
    }
}

/** 单个圆角滑块行：拖动本地实时预览、松手落盘；落盘后 DataStore Flow 即时刷新全局。 */
@Composable
private fun CornerSliderRow(
    title: String,
    value: Int,
    onSet: (Int) -> Unit
) {
    var local by remember(value) { mutableStateOf(value) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${local}dp",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.semanticColors.subtleText
            )
        }
        Slider(
            value = local.toFloat(),
            onValueChange = { local = it.roundToInt() },
            onValueChangeFinished = { onSet(local) },
            valueRange = AppearanceSettingsRepository.MIN_CORNER.toFloat()..AppearanceSettingsRepository.MAX_CORNER.toFloat(),
            steps = (AppearanceSettingsRepository.MAX_CORNER - AppearanceSettingsRepository.MIN_CORNER - 1)
        )
    }
}
