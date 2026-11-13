package com.aiverse.core.theme

import kotlinx.serialization.Serializable

/**
 * 主题包：把散落在 [com.aiverse.feature.settings.data.repository.ThemeSettingsRepository]
 * 与 [com.aiverse.feature.settings.data.repository.AppearanceSettingsRepository] 里的
 * 全部外观设置聚合成一个可整体「一键套用 / 导出 / 导入」的快照。
 *
 * 覆盖：明暗模式、配色主题、莫奈动态取色、图标彩、双侧聊天气泡样式、
 * 三档字号（聊天 / 输入框 / 代码块 / 界面缩放）、界面密度档、代码显示（换行/折叠/行号）、
 * 双侧气泡透明度。
 * 不含自定义背景图（二进制文件，单独走背景图备份通道）。
 *
 * 一键套用：一次写入以上全部设置，观感整体切换。
 * 导出/导入：序列化为 JSON 随备份文件走，或单独分享给他人。
 */
@Serializable
data class ThemePackage(
    val themeMode: String,
    val presetId: String,
    val dynamicColor: Boolean,
    val iconColorful: Boolean,
    val userBubbleStyleId: String,
    val assistantBubbleStyleId: String,
    val chatFontSizeSp: Int,
    val inputFontSizeSp: Int,
    val uiFontScale: Float,
    val uiDensityId: String,
    val codeFontSizeSp: Int = 13,
    val codeWrapEnabled: Boolean = false,
    val codeAutoFoldEnabled: Boolean = true,
    val codeShowLineNumber: Boolean = true,
    val userBubbleOpacity: Int = 100,
    val assistantBubbleOpacity: Int = 100
) {
    /** 解析成强类型密度档（未知 id 回退标准档）。 */
    val uiDensity: UIDensity get() = UIDensity.fromId(uiDensityId)

    /** 解析成强类型气泡样式（未知 id 回退经典描边）。 */
    val userBubbleStyle: ChatBubbleStyle get() = ChatBubbleStyle.fromId(userBubbleStyleId)

    /** 解析成强类型 AI 气泡样式。 */
    val assistantBubbleStyle: ChatBubbleStyle get() = ChatBubbleStyle.fromId(assistantBubbleStyleId)

    companion object {
        /** 当前 JSON 序列化用 schema 版本，便于未来演进。 */
        const val SCHEMA_VERSION = 1
    }
}
