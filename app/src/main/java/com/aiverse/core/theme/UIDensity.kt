package com.aiverse.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 界面密度档位：整体缩放间距（dp）、圆角（dp）与组件高度（dp），与字号缩放解耦。
 *
 * Compose 里 [androidx.compose.ui.unit.Density.fontScale] 只缩放 `sp` 文字、不缩放 `dp` 间距，
 * 这是「字号调了有效、间距/圆角改不到」的根因。密度档专门补上 `dp` 这一侧的缩放。
 *
 * [STANDARD] 为默认档（scale=1.0），行为与历史版本完全一致，老用户升级无感。
 */
enum class UIDensity(val id: String, val scale: Float) {
    COMPACT("compact", 0.85f),
    STANDARD("standard", 1.0f),
    COMFORTABLE("comfortable", 1.2f);

    companion object {
        fun fromId(id: String?): UIDensity = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

/**
 * 密度感知的设计令牌：把通用间距/圆角档 + 组件语义档按当前 [UIDensity.scale]
 * 一次性换算成 `dp`。组件只消费这里的值，不再写字面 `dp`，密度档切换即可全局生效。
 *
 * 由 [AIEditorTheme] 按 `uiDensity` 构造并通过 [LocalUIDensityTokens] 下发，
 * 任意位置以 `val t = LocalUIDensityTokens.current` 读取。
 */
data class UIDensityTokens(
    val density: UIDensity,
    /** 通用间距档（已按密度缩放）。 */
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val xxl: Dp,
    /** 通用圆角档（已按密度缩放）。 */
    val rXs: Dp,
    val rSm: Dp,
    val rMd: Dp,
    val rMdLarge: Dp,
    val rLg: Dp,
    val rXl: Dp,
    /** 全圆角（药丸），不参与密度缩放。 */
    val rPill: Dp = Radius.pill,
    /** 组件语义圆角（已按密度缩放）：卡片 / 按钮 / 输入框 / 气泡 / chip。 */
    val cardCorner: Dp,
    val buttonCorner: Dp,
    val fieldCorner: Dp,
    val bubbleCorner: Dp,
    val chipCorner: Dp,
    /** 图片/附件圆角（dp）：独立于卡片/按钮/输入框，默认 12dp。 */
    val imageCorner: Dp,
    /** M3 标准触控目标（48dp）按密度缩放，用作按钮/列表项基准高度。 */
    val touchTarget: Dp,
    /** 输入类控件（文本框/下拉）基准高度。 */
    val fieldHeight: Dp,
    /** chip / 小按钮基准高度。 */
    val chipHeight: Dp,
    /** 底部悬浮 Tab 栏容器高度（含 6dp 底部留白）。 */
    val tabBarHeight: Dp,
    /** 设置页列表 cell 行内垂直内边距（基准 11dp，对齐普通 SettingsRow）。 */
    val listCellPaddingVertical: Dp,
    /** 设置页列表 cell 卡片间距（基准 8dp）。 */
    val listCellGap: Dp,
    /** 阴影档（按密度缩放）：小（胶囊/卡片浮起）、中（主要悬浮栏/抽屉）。 */
    val elevationSm: Dp,
    val elevationMd: Dp,
    /** 阴影环境光 / 点光默认不透明度（浅色基准）。 */
    val ambientAlpha: Float,
    val spotAlpha: Float,
    /** 阴影环境光 / 点光默认不透明度（深色基准，阴影更深）。 */
    val ambientAlphaDark: Float,
    val spotAlphaDark: Float
) {
    /** 按语义取卡片圆角 Shape。 */
    val cardShape get() = RoundedCornerShape(cardCorner)

    /** 按语义取按钮圆角 Shape。 */
    val buttonShape get() = RoundedCornerShape(buttonCorner)

    /** 按语义取输入框圆角 Shape。 */
    val fieldShape get() = RoundedCornerShape(fieldCorner)

    /** 按语义取气泡圆角 Shape。 */
    val bubbleShape get() = RoundedCornerShape(bubbleCorner)

    /** 按语义取 chip 圆角 Shape。 */
    val chipShape get() = RoundedCornerShape(chipCorner)

    /**
     * 把「标准档基准值（裸 dp 数）」按当前密度缩放。用于无精确语义 token 对应的
     * 容器高度、最小宽度等结构性尺寸；语义值一律优先用上面具名 token。
     */
    fun scale(baseDp: Int): Dp = (baseDp * factor).dp

    /** 当前令牌所属密度档的缩放系数（标准档为 1.0）。 */
    private val factor: Float = density.scale

    companion object {
        /** 标准档令牌：所有值等于 [Spacing] / [Radius] 原始定义，行为与历史版本一致。 */
        val Standard = forDensity(UIDensity.STANDARD)

        /** 按密度档换算全部令牌。 */
        fun forDensity(
            density: UIDensity,
            cardCornerOverride: Int? = null,
            buttonCornerOverride: Int? = null,
            fieldCornerOverride: Int? = null,
            bubbleCornerOverride: Int? = null,
            imageCornerOverride: Int? = null
        ): UIDensityTokens {
            val s = density.scale
            return UIDensityTokens(
                density = density,
                xs = (Spacing.xs.value * s).dp,
                sm = (Spacing.sm.value * s).dp,
                md = (Spacing.md.value * s).dp,
                lg = (Spacing.lg.value * s).dp,
                xl = (Spacing.xl.value * s).dp,
                xxl = (Spacing.xxl.value * s).dp,
                rXs = (Radius.xs.value * s).dp,
                rSm = (Radius.sm.value * s).dp,
                rMd = (Radius.md.value * s).dp,
                rMdLarge = (Radius.mdLarge.value * s).dp,
                rLg = (Radius.lg.value * s).dp,
                rXl = (Radius.xl.value * s).dp,
                cardCorner = ((cardCornerOverride ?: 12) * s).dp,
                buttonCorner = ((buttonCornerOverride ?: 10) * s).dp,
                fieldCorner = ((fieldCornerOverride ?: Radius.mdLarge.value.toInt()) * s).dp,
                bubbleCorner = ((bubbleCornerOverride ?: 10) * s).dp,
                chipCorner = (8 * s).dp,
                imageCorner = ((imageCornerOverride ?: 12) * s).dp,
                touchTarget = (48 * s).dp,
                fieldHeight = (52 * s).dp,
                chipHeight = (32 * s).dp,
                tabBarHeight = (68 * s).dp,
                listCellPaddingVertical = 8.dp,
                listCellGap = (8 * s).dp,
                elevationSm = (3 * s).dp,
                elevationMd = (8 * s).dp,
                ambientAlpha = 0.08f,
                spotAlpha = 0.14f,
                ambientAlphaDark = 0.35f,
                spotAlphaDark = 0.50f
            )
        }
    }
}

/** 当前密度令牌，由 [AIEditorTheme] 注入；默认 [UIDensityTokens.Standard]。 */
val LocalUIDensityTokens = staticCompositionLocalOf { UIDensityTokens.Standard }
