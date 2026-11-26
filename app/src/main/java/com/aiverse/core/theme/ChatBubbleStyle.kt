package com.aiverse.core.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.aiverse.R

/** 聊天气泡样式（作用于收发两侧消息气泡）。在设置页「聊天气泡」中可选，共 9 种。 */
enum class ChatBubbleStyle(val id: String, @androidx.annotation.StringRes val labelRes: Int) {
    NO_BUBBLE("no_bubble", R.string.bubble_no_bubble),
    CLASSIC_OUTLINE("classic_outline", R.string.bubble_classic_outline),
    SOFT_SHADOW("soft_shadow", R.string.bubble_soft_shadow),
    MINIMAL_WHITE("minimal_white", R.string.bubble_minimal_white),
    DARK_MODE("dark_mode", R.string.bubble_dark_mode),
    COMIC("comic", R.string.bubble_comic),
    FRESH_GREEN("fresh_green", R.string.bubble_fresh_green),
    HEARTBEAT("heartbeat", R.string.bubble_heartbeat),
    ORIGINAL("original", R.string.bubble_original);

    companion object {
        fun fromId(id: String?): ChatBubbleStyle =
            entries.firstOrNull { it.id == id } ?: CLASSIC_OUTLINE
    }
}

/** 聊天气泡所属角色：用户发出的消息（USER）与 AI 回复（ASSISTANT），两侧可各自独立配置样式。 */
enum class ChatBubbleRole { USER, ASSISTANT }

/** 用户消息气泡样式，全局可读（由 AIEditorTheme 注入）。 */
val LocalUserBubbleStyle = staticCompositionLocalOf { ChatBubbleStyle.CLASSIC_OUTLINE }

/** AI 回复气泡样式，全局可读（由 AIEditorTheme 注入）。 */
val LocalAssistantBubbleStyle = staticCompositionLocalOf { ChatBubbleStyle.CLASSIC_OUTLINE }

/**
 * 插件对底部聊天输入框的尺寸与阴影覆盖；无插件声明时为 null，渲染端回退现有默认。
 * 字段均为已校验的数值，渲染端不再自行 clamp。
 */
data class ChatInputStyleOverride(
    val minHeight: Int?,
    val cornerRadius: Int?,
    val shadow: Int?
)

/** 插件声明的聊天输入框覆盖（由 [AIEditorTheme] 从插件令牌下发）；无插件声明时为 null。 */
val LocalChatInputStyle = staticCompositionLocalOf<ChatInputStyleOverride?> { null }

/**
 * 插件外观覆盖层（core 侧视图）：MainActivity 把插件域声明的宿主装饰令牌
 * 按「插件 > 用户设置 > 默认」叠加后映射成这里的纯基础类型，避免 core 反向依赖 feature。
 * 全部字段可空，null 表示该项无插件声明、保持现有用户设置/默认外观。
 */
data class PluginStyleOverride(
    val cardRadius: Int? = null,
    val buttonRadius: Int? = null,
    val fieldRadius: Int? = null,
    val bubbleRadius: Int? = null,
    val imageRadius: Int? = null,
    val userBubbleOpacity: Int? = null,
    val assistantBubbleOpacity: Int? = null,
    val chatPanelOpacity: Int? = null,
    /** 颜色文本：语义令牌名或 `#RRGGBB` / `#AARRGGBB`，由主题按当前配色方案解析。 */
    val bubbleUserColor: String? = null,
    val bubbleUserTextColor: String? = null,
    val topBarColor: String? = null,
    val topBarTextColor: String? = null,
    val drawerColor: String? = null,
    val drawerTextColor: String? = null,
    val iconColor: String? = null,
    val chatInput: ChatInputStyleOverride? = null
)

/**
 * 颜色令牌可取的语义名。语义名随宿主主题（含深色）解析，显式 `#` 十六进制则是固定值。
 * 扩充属于宿主插件 API 变更，需同步解析器与文档。
 *
 * 宿主共享：解析（feature/plugins 校验声明）与渲染（core 主题把声明解析成 [Color]）两端
 * 都要认得同一组语义名，故放在 core/theme 供双方依赖（feature → core 方向）。
 */
object PluginStyleColorTokens {
    const val PRIMARY = "primary"
    const val ON_PRIMARY = "onPrimary"
    const val SECONDARY = "secondary"
    const val TERTIARY = "tertiary"
    const val SURFACE = "surface"
    const val SURFACE_VARIANT = "surfaceVariant"
    const val BACKGROUND = "background"
    const val ON_SURFACE = "onSurface"
    const val ON_SURFACE_VARIANT = "onSurfaceVariant"
    const val OUTLINE = "outline"
    const val ERROR = "error"
    const val SUCCESS = "success"
    const val WARNING = "warning"
    const val INFO = "info"
    const val CARD_SURFACE = "cardSurface"
    const val PAGE_BACKGROUND = "pageBackground"

    val ALL = setOf(
        PRIMARY, ON_PRIMARY, SECONDARY, TERTIARY, SURFACE, SURFACE_VARIANT, BACKGROUND,
        ON_SURFACE, ON_SURFACE_VARIANT, OUTLINE, ERROR, SUCCESS, WARNING, INFO,
        CARD_SURFACE, PAGE_BACKGROUND
    )

    private val HEX = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

    /** 语义令牌名或 `#RRGGBB` / `#AARRGGBB` 视为合法颜色声明。 */
    fun isValidColor(value: String): Boolean = value in ALL || HEX.matches(value)
}
