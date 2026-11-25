package com.aiverse.feature.agent.domain.model

/** 角色头像的呈现类型。 */
enum class AvatarKind {
    /** 头像是一个 emoji 字符，存于 [Role.avatarValue]。 */
    EMOJI,

    /** 头像是一张图片（本地路径或 URI），存于 [Role.avatarValue]。 */
    IMAGE,

    /** 不显示头像。 */
    NONE
}

/**
 * 一个可绑定到会话的角色：接手默认身份提示词（原 00 号片段），为会话注入身份与职责。
 *
 * [identityPrompt] 是注入到系统提示词最前的身份正文（已去掉前导 `<!-- 描述 -->` 注释）。
 * 内置默认角色的 id 固定为 [DEFAULT_ROLE_ID]，其 [identityPrompt] 由迁移/启动 seeder 从原 00 号内容写入；
 * 用户自定义角色的 [identityPrompt] 由用户在角色编辑里填写。
 */
data class Role(
    val id: String,
    val name: String,
    /** 角色一句话简介，用于角色列表展示，不进系统提示词。 */
    val description: String = "",
    /** 注入系统提示词最前的身份正文；空表示该角色不额外注入身份（视为沿用无身份）。 */
    val identityPrompt: String = "",
    /** 标签，用于角色列表筛选与分组。 */
    val tags: List<String> = emptyList(),
    val avatarKind: AvatarKind = AvatarKind.NONE,
    /** 头像内容：emoji 字符或图片路径/URI；[avatarKind] 为 [AvatarKind.NONE] 时无意义。 */
    val avatarValue: String = "",
    /** false 时该角色不参与会话绑定与身份注入（仍保留在库里）。 */
    val enabled: Boolean = true,
    /** 内置角色不可删除，只可编辑身份正文与启停。 */
    val isBuiltin: Boolean = false,
    /** 列表排序权重，数值越小越靠前。 */
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** 绑定的工作区名（[Workspace.name]，唯一标识）。null 表示不绑定，跟随当前工作区行为。 */
    val workspaceId: String? = null
) {
    companion object {
        /** 内置默认角色 id：会话 [ChatSession.roleId] 为 null 时即绑定到它。 */
        const val DEFAULT_ROLE_ID = "role_default_builtin"

        /**
         * 角色名称的 UTF-8 字节上限（约 8 个中文字或 24 个英文字母）。
         *
         * 顶栏角色胶囊有最大宽度与省略号，长名称不会撑坏布局；按字节限而不是按字数限，
         * 是为了让中英文都能输入到接近同一个展示宽度。
         */
        const val NAME_MAX_BYTES = 24
    }
}
