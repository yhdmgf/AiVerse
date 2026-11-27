package com.aiverse.feature.agent.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.aiverse.core.ui.AdaptiveModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiverse.R
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import com.aiverse.feature.agent.presentation.AgentAttachment
import com.aiverse.feature.agent.presentation.AgentUIMessage
import com.aiverse.feature.agent.presentation.hasVisibleContent
import compose.icons.FeatherIcons
import compose.icons.feathericons.Bookmark
import compose.icons.feathericons.Copy
import compose.icons.feathericons.Download
import compose.icons.feathericons.Edit2
import compose.icons.feathericons.GitBranch
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Share2
import compose.icons.feathericons.Trash2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageActionsBottomSheet(
    message: AgentUIMessage,
    onDismiss: () -> Unit,
    onEditClick: () -> Unit,
    onCopyClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onForkClick: () -> Unit,
    onDeleteClick: () -> Unit,
    /** 图片 / 视频的保存、分享、插入聊天（issue #304）：从消息「更多」进入，不再在图片下方常驻操作行。 */
    onSaveImage: ((AgentAttachment) -> Unit)? = null,
    onShareImage: ((AgentAttachment) -> Unit)? = null,
    onInsertImage: ((AgentAttachment) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.lg)
        ) {
            Text(
                text = stringResource(R.string.chat_more_options),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            // 图片操作（issue #304）：原来贴在图片卡片下方的一行「保存 / 分享 / 插入聊天」
            // 收进这里，和复制 / 编辑 / 更多同级，用户消息与 AI 回复同一套规则。
            val imageAttachments = message.attachments.filter { it.isImage }
            if (imageAttachments.isNotEmpty() &&
                (onSaveImage != null || onShareImage != null || onInsertImage != null)
            ) {
                Text(
                    text = stringResource(R.string.chat_attachment_actions_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                )
                imageAttachments.forEach { attachment ->
                    if (onSaveImage != null) {
                        MessageActionItem(
                            icon = FeatherIcons.Download,
                            title = stringResource(R.string.chat_attachment_save),
                            subtitle = attachment.fileName,
                            onClick = {
                                onDismiss()
                                onSaveImage(attachment)
                            }
                        )
                    }
                    if (onShareImage != null) {
                        MessageActionItem(
                            icon = FeatherIcons.Share2,
                            title = stringResource(R.string.chat_attachment_share),
                            subtitle = attachment.fileName,
                            onClick = {
                                onDismiss()
                                onShareImage(attachment)
                            }
                        )
                    }
                    if (onInsertImage != null) {
                        MessageActionItem(
                            icon = FeatherIcons.Plus,
                            title = stringResource(R.string.chat_attachment_insert_chat),
                            subtitle = attachment.fileName,
                            onClick = {
                                onDismiss()
                                onInsertImage(attachment)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
            }

            // 编辑消息
            MessageActionItem(
                icon = FeatherIcons.Edit2,
                title = stringResource(R.string.chat_action_edit),
                onClick = {
                    onDismiss()
                    onEditClick()
                }
            )

            // 复制文本：纯图片消息没有文字可复制，隐藏该项
            if (message.content.hasVisibleContent()) {
                MessageActionItem(
                    icon = FeatherIcons.Copy,
                    title = stringResource(R.string.chat_action_copy),
                    onClick = {
                        onDismiss()
                        onCopyClick()
                    }
                )
            }

            // 收藏消息内容
            MessageActionItem(
                icon = FeatherIcons.Bookmark,
                title = stringResource(R.string.chat_action_favorite),
                onClick = {
                    onDismiss()
                    onFavoriteClick()
                }
            )

            // 从此消息分叉新会话：保留到此为止的完整历史，原会话不动
            MessageActionItem(
                icon = FeatherIcons.GitBranch,
                title = stringResource(R.string.chat_action_fork),
                onClick = {
                    onDismiss()
                    onForkClick()
                }
            )

            // 删除消息
            MessageActionItem(
                icon = FeatherIcons.Trash2,
                title = stringResource(R.string.chat_action_delete),
                isDestructive = true,
                onClick = {
                    onDismiss()
                    onDeleteClick()
                }
            )
        }
    }
}

@Composable
private fun MessageActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = color
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
