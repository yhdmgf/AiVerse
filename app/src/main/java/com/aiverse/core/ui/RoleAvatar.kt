package com.aiverse.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import compose.icons.FeatherIcons
import compose.icons.feathericons.User
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.feature.agent.domain.model.AvatarKind
import com.aiverse.feature.agent.domain.model.Role
import java.io.File

/**
 * 统一角色头像（issue：合并 agent / settings 两侧的重复实现到 core/ui）。
 *
 * 按 [Role.avatarKind] 呈现：
 *
 * - EMOJI：居中渲染 emoji 字符，字号随 [size] 等比；
 * - IMAGE：coil 异步加载本地图片（[Role.avatarValue] 为本地文件路径），填满并裁剪进形状；
 * - NONE / 空内容：通用用户图标，尺寸为 [size] 的 0.5 倍。
 *
 * 按 [size] 分两档呈现（阈值用密度令牌 [com.aiverse.core.theme.UIDensityTokens.chipHeight]，不写字面 dp）：
 *
 * - `size >= t.chipHeight`（角色列表 / 详情页）：铺 `surfaceVariant.copy(alpha = 0.6f)` 背景盒，
 *   圆角统一用密度令牌 [com.aiverse.core.theme.UIDensityTokens.rSm]（settings 版观感）；
 * - `size < t.chipHeight`（顶栏角色胶囊等行内徽标）：不铺内层背景，字形直接显示在胶囊底色上，
 *   仅裁圆形避免 IMAGE 方角溢出。
 *
 * EMOJI 用 `includeFontPadding = false` + 行内/垂直居中，让居中的是字形本身而不是
 * 整条行盒（ascent + descent + 字体上下留白），消除小字号下 emoji 下沉与左偏。
 *
 * [size] 由调用点传入（调用点已 `t.scale(N)`），本组件不再二次缩放，保证各密度档位一致。
 *
 * @param role 角色；null（默认角色缺失）或 [Role.avatarValue] 为空时回退到通用用户图标。
 * @param size 头像边长（Dp，由调用点按密度缩放后传入）。
 * @param modifier 外部修饰，拼在容器最外层。
 * @param fallbackIconScale 回退到通用用户图标时，图标边长占 [size] 的比例。
 */
@Composable
fun RoleAvatar(
    role: Role?,
    size: Dp,
    modifier: Modifier = Modifier,
    fallbackIconScale: Float = 0.5f
) {
    val t = LocalUIDensityTokens.current
    // 行内徽标档（顶栏角色胶囊）：不铺内层背景；其余场景保留背景盒观感。
    val inlineBadge = size < t.chipHeight
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (inlineBadge) {
                    Modifier.clip(CircleShape)
                } else {
                    Modifier
                        .clip(RoundedCornerShape(t.rSm))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        when (role?.avatarKind) {
            AvatarKind.EMOJI -> {
                if (role.avatarValue.isNotBlank()) {
                    Text(
                        text = role.avatarValue,
                        fontSize = with(LocalDensity.current) { (size * 0.62f).toSp() },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = FeatherIcons.User,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(size * fallbackIconScale)
                    )
                }
            }
            AvatarKind.IMAGE -> {
                if (role.avatarValue.isNotBlank()) {
                    AsyncImage(
                        model = File(role.avatarValue),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = FeatherIcons.User,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(size * fallbackIconScale)
                    )
                }
            }
            else -> {
                Icon(
                    imageVector = FeatherIcons.User,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size * fallbackIconScale)
                )
            }
        }
    }
}
