package com.aiverse.feature.agent.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aiverse.feature.agent.domain.model.AvatarKind
import com.aiverse.feature.agent.domain.model.Role

/** 角色表：见 [Role]。 */
@Entity(tableName = "roles", indices = [Index(value = ["workspaceId"])])
data class RoleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    /** 注入系统提示词最前的身份正文（已去前导注释）。 */
    val identityPrompt: String,
    /** 逗号分隔的标签。 */
    val tags: String,
    val avatarKind: String,
    val avatarValue: String,
    val enabled: Boolean,
    val isBuiltin: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    /** 绑定的工作区名；null 表示不绑定。 */
    val workspaceId: String? = null
) {
    fun toDomain(): Role = Role(
        id = id,
        name = name,
        description = description,
        identityPrompt = identityPrompt,
        tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        avatarKind = runCatching { AvatarKind.valueOf(avatarKind) }.getOrDefault(AvatarKind.NONE),
        avatarValue = avatarValue,
        enabled = enabled,
        isBuiltin = isBuiltin,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        workspaceId = workspaceId
    )

    companion object {
        fun fromDomain(role: Role): RoleEntity = RoleEntity(
            id = role.id,
            name = role.name,
            description = role.description,
            identityPrompt = role.identityPrompt,
            tags = role.tags.joinToString(","),
            avatarKind = role.avatarKind.name,
            avatarValue = role.avatarValue,
            enabled = role.enabled,
            isBuiltin = role.isBuiltin,
            sortOrder = role.sortOrder,
            createdAt = role.createdAt,
            updatedAt = role.updatedAt,
            workspaceId = role.workspaceId
        )
    }
}
