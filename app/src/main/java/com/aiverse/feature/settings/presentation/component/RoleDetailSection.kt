package com.aiverse.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.ui.RoleAvatar
import com.aiverse.feature.agent.domain.model.Role
import com.aiverse.feature.agent.presentation.component.MarkdownContent

/**
 * 角色详情页：只读预览——名称 / 简介 / 头像 / 标签 / 状态 / 类型，以及身份 Prompt（Markdown）。
 *
 * 要改内容走顶栏的编辑按钮，进入 [RoleEditorScreen]。
 */
@Composable
internal fun RoleDetailSection(role: Role) {
    val t = LocalUIDensityTokens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = t.lg)
            .padding(bottom = t.xl),
        verticalArrangement = Arrangement.spacedBy(t.sm)
    ) {
        SettingsGroup {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = t.lg, vertical = t.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoleAvatar(role = role, size = t.scale(56))
                Spacer(modifier = Modifier.width(t.md))
                Column {
                    Text(
                        text = role.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (role.isBuiltin) {
                        Spacer(modifier = Modifier.height(t.xs))
                        RoleBadge(
                            text = stringResource(R.string.roles_badge_builtin),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            SettingsDivider()
            InfoRow(
                label = stringResource(R.string.roles_field_description),
                value = role.description.ifBlank { stringResource(R.string.roles_no_description) }
            )
            SettingsDivider()
            InfoRow(
                label = stringResource(R.string.roles_field_tags),
                value = role.tags.joinToString(", ").ifBlank { stringResource(R.string.roles_no_tags) }
            )
            if (!role.isBuiltin) {
                SettingsDivider()
                InfoRow(
                    label = stringResource(R.string.roles_field_status),
                    value = stringResource(
                        if (role.enabled) R.string.common_enabled else R.string.common_disabled
                    )
                )
            }
            SettingsDivider()
            InfoRow(
                label = stringResource(R.string.roles_field_type),
                value = stringResource(
                    if (role.isBuiltin) R.string.roles_badge_builtin else R.string.roles_badge_custom
                )
            )
            SettingsDivider()
            InfoRow(
                label = stringResource(R.string.roles_field_workspace),
                value = role.workspaceId?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.chat_role_workspace_follow)
            )
        }

        SettingsGroupHeader(text = stringResource(R.string.roles_field_identity))
        SettingsGroup {
            if (role.identityPrompt.isBlank()) {
                Text(
                    text = stringResource(R.string.roles_identity_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = t.lg, vertical = t.md)
                )
            } else {
                MarkdownContent(
                    text = role.identityPrompt,
                    color = MaterialTheme.colorScheme.onSurface,
                    lazyScroll = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        // 正文卡限高：长文本在卡内懒加载滚动（外层整页仍可继续滚），避免超大 md 全量渲染卡顿。
                        .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.62f)
                        .padding(horizontal = t.lg, vertical = t.md)
                )
            }
        }
    }
}

/** 键值行：左标签右值，值过长时换行。 */
@Composable
private fun InfoRow(label: String, value: String) {
    val t = LocalUIDensityTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = t.lg, vertical = t.md)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(t.md))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
