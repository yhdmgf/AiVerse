package com.aiverse.feature.settings.domain.model

import android.content.Context
import com.aiverse.R
import kotlinx.serialization.Serializable

/**
 * 欢迎卡片（新对话页快捷入口）的动作类型。
 *
 * 点击卡片后执行的动作：
 * - [OPEN_TERMINAL]：打开终端。
 * - [OPEN_GIT]：打开 Git（版本控制页）。
 * - [FILL_PROMPT]：把 [WelcomeEntry.promptText] 填入输入框（可再编辑后发送）。
 * - [OPEN_BROWSER]：打开内置浏览器。
 * - [OPEN_HISTORY]：打开历史会话。
 */
@Serializable
enum class WelcomeEntryAction {
    OPEN_TERMINAL,
    OPEN_GIT,
    FILL_PROMPT,
    OPEN_BROWSER,
    OPEN_HISTORY
}

/**
 * 欢迎卡片图标的呈现方式。
 *
 * - [VECTOR]：[WelcomeEntry.iconValue] 为 Feather 图标的简单名（如 "Terminal"、"GitBranch"、
 *   "Code"、"AlertCircle"），由 UI 层解析成 compose.icons.FeatherIcons 对应的 [androidx.compose.ui.graphics.vector.ImageVector] 渲染。
 *   预置项与用户自定义入口均使用（图标选择弹层的矢量网格）。
 * - [EMOJI]：[WelcomeEntry.iconValue] 为一个 emoji 字符。**已废弃**：仅用于旧数据解码兼容，不再生成；
 *   加载时由 [com.aiverse.feature.settings.data.repository.WelcomeEntriesRepository] 迁移为 [VECTOR]。
 * - [TEXT]：[WelcomeEntry.iconValue] 为不超过 3 个字的文本（如「AI」「代码」）。
 * - [IMAGE]：[WelcomeEntry.iconValue] 为应用私有目录内图片文件的绝对路径（由 [com.aiverse.feature.settings.data.repository.WelcomeEntriesRepository.saveIconImage] 落盘）。
 */
@Serializable
enum class WelcomeIconKind {
    VECTOR,
    EMOJI,
    TEXT,
    IMAGE
}

/**
 * 新对话页可配置快捷入口（欢迎卡片）的数据模型。
 *
 * 用户可在设置页增删改、排序、启停；预置项（[isPreset] = true）由字符串资源解析，
 * 用户可删除或编辑但恢复默认时重新生成。
 *
 * @property id 全局唯一 id（自定义项用 UUID，预置项用固定常量如 "preset_terminal"）。
 * @property title 卡片标题；预置项由字符串资源解析后存入（UI 直接显示，无需再解析）。
 * @property subtitle 卡片说明，可为空字符串表示不显示。
 * @property iconKind 图标呈现方式。
 * @property iconValue 图标内容：Feather 图标简单名 / 不超过 3 字文本 / 图片绝对路径。
 * @property action 点击后的动作。
 * @property promptText [action] == [WelcomeEntryAction.FILL_PROMPT] 时填入输入框的提示词模板。
 * @property enabled 是否启用；禁用的卡片显示但点击无动作（或在设置页置灰）。
 * @property isPreset 是否为预置项（预置项 id 固定，恢复默认时重建）。
 */
@Serializable
data class WelcomeEntry(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val iconKind: WelcomeIconKind = WelcomeIconKind.VECTOR,
    val iconValue: String = VECTOR_ICON_DEFAULT,
    val action: WelcomeEntryAction = WelcomeEntryAction.FILL_PROMPT,
    val promptText: String = "",
    val enabled: Boolean = true,
    val isPreset: Boolean = false
)

/** 预置项固定 id：终端。 */
const val PRESET_ID_TERMINAL = "preset_terminal"

/** 预置项固定 id：Git。 */
const val PRESET_ID_GIT = "preset_git"

/** 预置项固定 id：快捷提问（填入提示词）。 */
const val PRESET_ID_ASSIST = "preset_assist"

/** 预置项固定 id：分析项目（填入提示词）。 */
const val PRESET_ID_ANALYZE = "preset_analyze"

/** [WelcomeIconKind.VECTOR] 图标名：终端（Feather Terminal）。 */
const val VECTOR_ICON_TERMINAL = "Terminal"

/** [WelcomeIconKind.VECTOR] 图标名：Git（Feather GitBranch）。 */
const val VECTOR_ICON_GIT_BRANCH = "GitBranch"

/** [WelcomeIconKind.VECTOR] 图标名：快捷提问（Feather Code）。 */
const val VECTOR_ICON_CODE = "Code"

/** [WelcomeIconKind.VECTOR] 图标名：分析项目（Feather AlertCircle）。 */
const val VECTOR_ICON_ALERT_CIRCLE = "AlertCircle"

/** [WelcomeIconKind.VECTOR] 默认图标名（Feather MessageCircle）；未知名/旧 emoji 数据回退到它。 */
const val VECTOR_ICON_DEFAULT = "MessageCircle"

/**
 * 预置欢迎卡片：从字符串资源解析中文标题/说明/提示词。
 *
 * 资源文件由设置页 UI 区块补全（key 命名见下），本函数只引用 [R.string]，资源缺失时由 Lead 协调补资源。
 * 预置项均启用，默认排序即返回顺序。
 *
 * 资源 key 约定（供资源区块引用）：
 * - [R.string.welcome_entry_preset_terminal_title] / _subtitle
 * - [R.string.welcome_entry_preset_git_title] / _subtitle
 * - [R.string.welcome_entry_preset_assist_title] / _subtitle / _prompt
 * - [R.string.welcome_entry_preset_analyze_title] / _subtitle / _prompt
 */
suspend fun defaultWelcomeEntries(context: Context): List<WelcomeEntry> =
    buildDefaultWelcomeEntries(context)

/**
 * 预置欢迎卡片的非挂起构建（供 [WelcomeEntriesRepository] 首次加载与恢复默认复用）。
 * 见 [defaultWelcomeEntries]。
 */
internal fun buildDefaultWelcomeEntries(context: Context): List<WelcomeEntry> = listOf(
    WelcomeEntry(
        id = PRESET_ID_TERMINAL,
        title = context.getString(R.string.welcome_entry_preset_terminal_title),
        subtitle = context.getString(R.string.welcome_entry_preset_terminal_subtitle),
        iconKind = WelcomeIconKind.VECTOR,
        iconValue = VECTOR_ICON_TERMINAL,
        action = WelcomeEntryAction.OPEN_TERMINAL,
        isPreset = true
    ),
    WelcomeEntry(
        id = PRESET_ID_GIT,
        title = context.getString(R.string.welcome_entry_preset_git_title),
        subtitle = context.getString(R.string.welcome_entry_preset_git_subtitle),
        iconKind = WelcomeIconKind.VECTOR,
        iconValue = VECTOR_ICON_GIT_BRANCH,
        action = WelcomeEntryAction.OPEN_GIT,
        isPreset = true
    ),
    WelcomeEntry(
        id = PRESET_ID_ASSIST,
        title = context.getString(R.string.welcome_entry_preset_assist_title),
        subtitle = context.getString(R.string.welcome_entry_preset_assist_subtitle),
        iconKind = WelcomeIconKind.VECTOR,
        iconValue = VECTOR_ICON_CODE,
        action = WelcomeEntryAction.FILL_PROMPT,
        promptText = context.getString(R.string.welcome_entry_preset_assist_prompt),
        isPreset = true
    ),
    WelcomeEntry(
        id = PRESET_ID_ANALYZE,
        title = context.getString(R.string.welcome_entry_preset_analyze_title),
        subtitle = context.getString(R.string.welcome_entry_preset_analyze_subtitle),
        iconKind = WelcomeIconKind.VECTOR,
        iconValue = VECTOR_ICON_ALERT_CIRCLE,
        action = WelcomeEntryAction.FILL_PROMPT,
        promptText = context.getString(R.string.welcome_entry_preset_analyze_prompt),
        isPreset = true
    )
)
