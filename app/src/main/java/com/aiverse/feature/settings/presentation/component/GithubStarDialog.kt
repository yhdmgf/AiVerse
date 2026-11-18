package com.aiverse.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Spacing
import compose.icons.FeatherIcons
import compose.icons.feathericons.Github
import compose.icons.feathericons.Star

/**
 * 应用启动时的 GitHub Star 提示弹窗。
 *
 * 与旧的关于页提示卡不同，这里做成启动弹窗：勾选左下角「不再显示」后点击任意按钮
 * （去点 Star / 稍后再说 / 点外部关闭）都会通过 [onStar]/[onDismiss] 回传
 * dontShowAgain=true，由调用方持久化，下次启动不再弹出；未勾选则下次启动仍会提示。
 *
 * 弹窗自身不碰持久化，只回传勾选状态，保持 UI 与存储解耦。
 */
@Composable
fun GithubStarDialog(
    onStar: (dontShowAgain: Boolean) -> Unit,
    onDismiss: (dontShowAgain: Boolean) -> Unit
) {
    var dontShowAgain by remember { mutableStateOf(false) }
    val density = LocalUIDensityTokens.current
    AlertDialog(
        onDismissRequest = { onDismiss(dontShowAgain) },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = FeatherIcons.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(density.scale(20))
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = stringResource(R.string.about_star_prompt_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(density.scale(12))) {
                Text(
                    text = stringResource(R.string.about_star_prompt_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                // 「不再显示」勾选框：置于内容区左下角，勾选后点任意关闭路径即永久隐藏。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it }
                    )
                    Text(
                        text = stringResource(R.string.about_star_prompt_dismiss),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onStar(dontShowAgain) },
                shape = density.buttonShape
            ) {
                Icon(
                    imageVector = FeatherIcons.Github,
                    contentDescription = null,
                    modifier = Modifier.size(density.scale(16))
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.about_star_prompt_action))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss(dontShowAgain) }) {
                Text(stringResource(R.string.about_star_prompt_later))
            }
        }
    )
}
