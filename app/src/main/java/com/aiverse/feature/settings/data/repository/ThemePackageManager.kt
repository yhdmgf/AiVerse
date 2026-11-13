package com.aiverse.feature.settings.data.repository

import com.aiverse.core.theme.AppThemePreset
import com.aiverse.core.theme.ThemePackage
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 主题包管理器：把 [ThemeSettingsRepository]（配色/明暗/气泡）与
 * [AppearanceSettingsRepository]（字号/密度）编排成「一键套用 / 快照导出」。
 *
 * 套用是整体事务式覆盖——一次性写入主题包包含的全部设置，观感整体切换。
 * 导出是聚合两个仓储当前值成一个 [ThemePackage]，可随备份走或单独分享。
 */
@Singleton
class ThemePackageManager @Inject constructor(
    private val themeSettingsRepository: ThemeSettingsRepository,
    private val appearanceSettingsRepository: AppearanceSettingsRepository
) {
    /** 聚合当前全部外观设置为一份主题包快照（用于导出 / 备份）。 */
    suspend fun currentPackage(): ThemePackage = ThemePackage(
        themeMode = themeSettingsRepository.themeModeFlow.first().name,
        presetId = themeSettingsRepository.themePresetIdFlow.first()
            ?: AppThemePreset.findById(null).id,
        dynamicColor = themeSettingsRepository.dynamicColorFlow.first(),
        iconColorful = themeSettingsRepository.iconColorFlow.first(),
        userBubbleStyleId = themeSettingsRepository.userBubbleStyleFlow.first().id,
        assistantBubbleStyleId = themeSettingsRepository.assistantBubbleStyleFlow.first().id,
        chatFontSizeSp = appearanceSettingsRepository.chatFontSizeSpFlow.first(),
        inputFontSizeSp = appearanceSettingsRepository.inputFontSizeSpFlow.first(),
        uiFontScale = appearanceSettingsRepository.uiFontScaleFlow.first(),
        uiDensityId = appearanceSettingsRepository.uiDensityFlow.first().id,
        codeFontSizeSp = appearanceSettingsRepository.codeFontSizeSpFlow.first(),
        codeWrapEnabled = appearanceSettingsRepository.codeWrapEnabledFlow.first(),
        codeAutoFoldEnabled = appearanceSettingsRepository.codeAutoFoldEnabledFlow.first(),
        codeShowLineNumber = appearanceSettingsRepository.codeShowLineNumberFlow.first(),
        userBubbleOpacity = appearanceSettingsRepository.userBubbleOpacityFlow.first(),
        assistantBubbleOpacity = appearanceSettingsRepository.assistantBubbleOpacityFlow.first()
    )

    /** 一键套用主题包：整体覆盖全部外观设置。 */
    suspend fun applyPackage(pkg: ThemePackage) {
        themeSettingsRepository.setThemeMode(
            AppThemeMode.entries.firstOrNull { it.name == pkg.themeMode } ?: AppThemeMode.AUTO
        )
        themeSettingsRepository.setThemePresetId(pkg.presetId)
        themeSettingsRepository.setDynamicColorEnabled(pkg.dynamicColor)
        themeSettingsRepository.setIconColorEnabled(pkg.iconColorful)
        themeSettingsRepository.setUserBubbleStyle(pkg.userBubbleStyle)
        themeSettingsRepository.setAssistantBubbleStyle(pkg.assistantBubbleStyle)
        appearanceSettingsRepository.setChatFontSizeSp(pkg.chatFontSizeSp)
        appearanceSettingsRepository.setInputFontSizeSp(pkg.inputFontSizeSp)
        appearanceSettingsRepository.setUiFontScale(pkg.uiFontScale)
        appearanceSettingsRepository.setUiDensity(pkg.uiDensity)
        appearanceSettingsRepository.setCodeFontSizeSp(pkg.codeFontSizeSp)
        appearanceSettingsRepository.setCodeWrapEnabled(pkg.codeWrapEnabled)
        appearanceSettingsRepository.setCodeAutoFoldEnabled(pkg.codeAutoFoldEnabled)
        appearanceSettingsRepository.setCodeShowLineNumber(pkg.codeShowLineNumber)
        appearanceSettingsRepository.setUserBubbleOpacity(pkg.userBubbleOpacity)
        appearanceSettingsRepository.setAssistantBubbleOpacity(pkg.assistantBubbleOpacity)
    }
}
