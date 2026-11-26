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
            // 按字段取第一个非 null 声明者，列表顺序即优先级；调用方应先按插件 id 排序保证结果稳定。
            // 先 filterNotNull 拿到非空元素，再用显式 for 循环取各字段首个非空值。刻意不用
            // firstNotNullOfOrNull / mapNotNull：它们的类型参数带 R : Any 非空上界，而字段本身是
            // Int? / String?，选择器返回可空值会让 R 推断成可空、违反上界并与字段类型不匹配。
            // 这里返回类型直接写死具体类型，零泛型推断。
            val declared = tokens.filterNotNull()
            fun firstRadius(selector: (PluginStyleTokensRef) -> Int?): Int? {
                for (token in declared) selector(token)?.let { return it }
                return null
            }
            fun firstColor(selector: (PluginStyleTokensRef) -> String?): String? {
                for (token in declared) selector(token)?.let { return it }
                return null
            }
            return PluginStyleTokensRef(
                chatInputRadius = firstRadius { it.chatInputRadius },
                cardRadius = firstRadius { it.cardRadius },
                buttonRadius = firstRadius { it.buttonRadius },
                fieldRadius = firstRadius { it.fieldRadius },
                bubbleRadius = firstRadius { it.bubbleRadius },
                imageRadius = firstRadius { it.imageRadius },
                userBubbleOpacity = firstRadius { it.userBubbleOpacity },
                assistantBubbleOpacity = firstRadius { it.assistantBubbleOpacity },
                chatPanelOpacity = firstRadius { it.chatPanelOpacity },
                bubbleUserColor = firstColor { it.bubbleUserColor },
                bubbleUserTextColor = firstColor { it.bubbleUserTextColor },
                topBarColor = firstColor { it.topBarColor },
                topBarTextColor = firstColor { it.topBarTextColor },
                drawerColor = firstColor { it.drawerColor },
                drawerTextColor = firstColor { it.drawerTextColor },
                iconColor = firstColor { it.iconColor },
                chatInputMinHeight = firstRadius { it.chatInputMinHeight },
                chatInputShadow = firstRadius { it.chatInputShadow }
            )
        }
    }
}

