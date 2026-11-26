package com.aiverse.feature.plugins.domain

/**
 * `contributes.styleTokens` 声明的宿主组件纯装饰外观令牌。所有字段可选；未声明的字段保持
 * 宿主当前外观。这里只承载"要改成什么值"，宿主如何映射到自身组件由宿主决定，插件不指定作用位置。
 */
data class PluginStyleTokensRef(
    // 圆角类（dp）
    val chatInputRadius: Int? = null,
    val cardRadius: Int? = null,
    val buttonRadius: Int? = null,
    val fieldRadius: Int? = null,
    val bubbleRadius: Int? = null,
    val imageRadius: Int? = null,
    // 透明度类（百分比）
    val userBubbleOpacity: Int? = null,
    val assistantBubbleOpacity: Int? = null,
    val chatPanelOpacity: Int? = null,
    // 颜色类：语义令牌名或 #RRGGBB / #AARRGGBB
    val bubbleUserColor: String? = null,
    val bubbleUserTextColor: String? = null,
    val topBarColor: String? = null,
    val topBarTextColor: String? = null,
    val drawerColor: String? = null,
    val drawerTextColor: String? = null,
    val iconColor: String? = null,
    // 尺寸类（dp）
    val chatInputMinHeight: Int? = null,
    // 阴影类（dp）
    val chatInputShadow: Int? = null
) {
    companion object {
        /** 一个字段都没声明的空令牌，作为合并的起点。 */
        fun empty() = PluginStyleTokensRef()

        /**
         * 按字段取第一个非 null 声明者，列表顺序即优先级。调用方应先按插件 id 排序，
         * 保证结果稳定可复现；列表为空时返回全空令牌。
         */
        fun mergeStyleTokens(tokens: List<PluginStyleTokensRef?>): PluginStyleTokensRef {
            fun <T> first(selector: (PluginStyleTokensRef) -> T?): T? =
                tokens.firstNotNullOfOrNull { selector(it) }
            return PluginStyleTokensRef(
                chatInputRadius = first { it.chatInputRadius },
                cardRadius = first { it.cardRadius },
                buttonRadius = first { it.buttonRadius },
                fieldRadius = first { it.fieldRadius },
                bubbleRadius = first { it.bubbleRadius },
                imageRadius = first { it.imageRadius },
                userBubbleOpacity = first { it.userBubbleOpacity },
                assistantBubbleOpacity = first { it.assistantBubbleOpacity },
                chatPanelOpacity = first { it.chatPanelOpacity },
                bubbleUserColor = first { it.bubbleUserColor },
                bubbleUserTextColor = first { it.bubbleUserTextColor },
                topBarColor = first { it.topBarColor },
                topBarTextColor = first { it.topBarTextColor },
                drawerColor = first { it.drawerColor },
                drawerTextColor = first { it.drawerTextColor },
                iconColor = first { it.iconColor },
                chatInputMinHeight = first { it.chatInputMinHeight },
                chatInputShadow = first { it.chatInputShadow }
            )
        }
    }
}

