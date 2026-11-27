package com.aiverse.feature.agent.presentation.component

import android.app.ActivityManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.aiverse.core.theme.LocalHyperOSStyle
import com.aiverse.core.theme.LocalSurfaceGlassStyle
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.glassBorderOr
import com.aiverse.core.theme.glassOr
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import com.aiverse.R
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import com.aiverse.feature.agent.domain.model.AgentMode
import com.aiverse.feature.agent.domain.model.Role
import com.aiverse.feature.plugins.domain.newmodel.PluginPage
import com.aiverse.feature.plugins.domain.newmodel.PluginUiEntry
import com.aiverse.feature.plugins.presentation.PluginChatHeaderMenuItems
import com.aiverse.feature.plugins.presentation.PluginChatWelcomeEntries
import com.aiverse.feature.onboarding.domain.OnboardingStep
import com.aiverse.feature.onboarding.presentation.onboardingTarget
import com.aiverse.feature.settings.domain.model.WelcomeEntry
import com.aiverse.feature.settings.domain.model.WelcomeEntryAction
import com.aiverse.feature.settings.domain.model.WelcomeIconKind
import com.aiverse.feature.settings.presentation.component.ModelLogoIcon
import com.aiverse.feature.settings.presentation.component.welcomeEntryVectorIcon
import com.aiverse.core.ui.AppTextField
import com.aiverse.core.ui.PressableSurface
import com.aiverse.core.ui.RoleAvatar
import com.aiverse.core.ui.dialogTextFieldColors
import com.aiverse.feature.settings.data.remote.ModelTestResult
import com.aiverse.feature.settings.domain.model.AIProviderConfig
import com.aiverse.feature.settings.domain.model.ModelMetadata
import compose.icons.FeatherIcons
import compose.icons.feathericons.MoreHorizontal
import compose.icons.feathericons.GitBranch
import compose.icons.feathericons.MessageCircle
import compose.icons.feathericons.Plus
import coil.compose.AsyncImage
import compose.icons.feathericons.Terminal

@Composable
internal fun ChatHeader(
    sessionTitle: String,
    modelName: String?,
    inputTokens: Int,
    outputTokens: Int,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToGit: () -> Unit,
    onNavigateToBrowser: () -> Unit = {},
    currentMode: AgentMode,
    onToggleMode: (AgentMode) -> Unit,
    connectionState: com.aiverse.feature.agent.domain.container.ConnectionState? = null,
    showMenuButton: Boolean = true,
    terminalActive: Boolean = false,
    gitActive: Boolean = false,
    browserActive: Boolean = false,
    /** 右上角思考强度入口（闪电图标 + 弹层），由 AIChatPanel 传入 ReasoningEffortSelector。 */
    reasoningEntrance: @Composable () -> Unit = {},
    /** 当前会话 id：非空时点标题弹出「重命名会话」对话框。 */
    sessionId: String? = null,
    /** 重命名会话回调（sessionId, 新标题）；为空则标题不可点。 */
    onRenameSession: ((String, String) -> Unit)? = null,
    /** 当前提供商 id，模型切换弹窗高亮当前项用。 */
    providerId: String = "",
    /** 模型切换弹窗数据源（providers / 模型元数据）。 */
    providers: List<AIProviderConfig> = emptyList(),
    modelMetadata: Map<String, ModelMetadata> = emptyMap(),
    /** 选择模型回调（providerId, model）；为空则模型名不可点。 */
    onSelectModel: ((String, String) -> Unit)? = null,
    /** 模型「测试」入口，透传给模型切换弹窗。 */
    runModelTest: (suspend (AIProviderConfig, String) -> ModelTestResult)? = null,
    /** 上下文用量占比（0f~1f），驱动顶栏用量圆环。 */
    tokenProgress: Float = 0f,
    /** 用量是否为估算值（圆环语义文案区分「预计/已用」）。 */
    tokenEstimated: Boolean = false,
    /** 上下文用量明细（当前会话）；null 时圆环点击不弹气泡。 */
    contextUsageDetail: ContextUsageDetail? = null,
    /** 自动压缩触发阈值（百分比），用量明细进度条上画界限标记。 */
    compactionThresholdPercent: Int = 85,
    /** 一键压缩当前会话上下文。 */
    onCompactContext: () -> Unit = {},
    /** 打开当前会话的消息时间线浮窗（顶栏 ··· 菜单入口）。 */
    onOpenMessageTimeline: () -> Unit = {},
    /** 当前会话绑定角色（已解析；null 表示默认角色未解析，显示默认名）。 */
    currentRole: Role? = null,
    /** 点击角色入口：打开角色切换抽屉。 */
    onOpenRoleSwitcher: () -> Unit = {},
    /** 副行展示的思考档位文案（如「中」）；null 表示不展示档位。 */
    reasoningEffortLabel: String? = null,
    /** 顶栏「···」菜单里的插件槽位入口。 */
    pluginHeaderEntries: List<PluginUiEntry> = emptyList(),
    pluginLanguageTag: String = "",
    onOpenPluginPage: (PluginPage) -> Unit = {},
    onRunPluginAction: (PluginUiEntry) -> Unit = {},
    /** 插件声明的顶栏着色；null=不铺色，保持内容渐隐的默认融合效果。 */
    topBarColor: Color? = null
) {
    // 顶栏不再有表面（issue #269）：删掉 Surface 底色与玻璃描边，默认不铺任何颜色（融合靠「不铺」不靠「铺得淡」）。
    // 滚过的消息改由内容层的顶部渐隐负责淡出（见 AIChatPanel 内容层 drawWithContent）。
    // 标题/图标/胶囊保持原色，状态栏留白不变。
    // 仅当插件显式声明了 topBarColor 时才铺一层全强度纯色（不渐变、不随滚动淡出），
    // 否则该插件字段会静默失效；null 时完全透明。此 Box 不参与 headerHeightPx 测量，加背景不影响测量。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (topBarColor != null) Modifier.background(topBarColor) else Modifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            var showRenameDialog by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 两行文字约 38dp；原 56dp 下限再叠 8dp 上下内距，副行下方会空出一圈。
                    // 收到 50dp 下限 + 5dp 内距：内容仍完整，底部留白少 6dp。
                    .heightIn(min = 50.dp)
                    .padding(horizontal = Spacing.sm, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                // 大屏常驻侧栏时隐掉汉堡键：侧栏已经摆在左边，再给个开关反而困惑。
                if (showMenuButton) {
                    HeaderMenuBarsButton(
                        onClick = onOpenDrawer,
                        contentDescription = stringResource(R.string.chat_open_sidebar),
                        tint = com.aiverse.core.theme.pluginIconColorOr(MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.onboardingTarget(OnboardingStep.OPEN_SIDEBAR)
                    )
                } else {
                    Spacer(modifier = Modifier.width(Spacing.sm))
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val titleStyle = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                    val titleColor = com.aiverse.core.theme.pluginTopBarTextColor()
                        ?: MaterialTheme.colorScheme.onBackground
                    // 点标题弹出「重命名会话」对话框：按容器圆角裁剪高亮，不再方块溢出。
                    if (sessionId != null && onRenameSession != null) {
                        PressableSurface(
                            onClick = { showRenameDialog = true },
                            shape = RoundedCornerShape(LocalUIDensityTokens.current.buttonCorner),
                            contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = 1.dp)
                        ) {
                            Text(
                                text = sessionTitle,
                                style = titleStyle,
                                color = titleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(
                            text = sessionTitle,
                            style = titleStyle,
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // 副行：模型 logo + 「模型名 / 档位」，纯只读文字、不可点；模型胶囊与角色胶囊已退役（issue #271）。
                    val subtitleParts = buildList {
                        modelName?.takeIf { it.isNotBlank() }?.let { add(it) }
                        reasoningEffortLabel?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                    Row(
                        modifier = Modifier.heightIn(min = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        if (!modelName.isNullOrBlank()) {
                            ModelLogoIcon(modelName = modelName, size = 14.dp)
                        }
                        Text(
                            text = subtitleParts.joinToString(" / ")
                                .ifBlank { stringResource(R.string.chat_no_model_selected) },
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // 思考强度入口（右上角，常驻）
                reasoningEntrance()
                // 上下文用量圆环（··· 左边，固定显示），点击弹漫画气泡显示用量明细。
                HeaderContextUsageButton(
                    progress = tokenProgress,
                    tokenEstimated = tokenEstimated,
                    detail = contextUsageDetail,
                    thresholdPercent = compactionThresholdPercent,
                    onCompact = onCompactContext,
                    modelName = modelName
                )
                // ··· 更多菜单：新建会话 / 打开 Git / 打开终端 收进下拉菜单。
                HeaderOverflowMenu(
                    onNewChat = onNewChat,
                    onNavigateToGit = onNavigateToGit,
                    onNavigateToTerminal = onNavigateToTerminal,
                    onOpenMessageTimeline = onOpenMessageTimeline,
                    pluginEntries = pluginHeaderEntries,
                    pluginLanguageTag = pluginLanguageTag,
                    onOpenPluginPage = onOpenPluginPage,
                    onRunPluginAction = onRunPluginAction
                )
            }
            // 远程模式：左边 SSH 连接状态，右边 token 累计统计
            if (connectionState != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ConnectionIndicator(state = connectionState)
                    TokenStats(
                        inputTokens = inputTokens,
                        outputTokens = outputTokens
                    )
                }
            }
            // 点标题弹「重命名会话」对话框（复用侧栏抽屉的输入样式）。
            if (showRenameDialog && sessionId != null && onRenameSession != null) {
                var renameText by remember(sessionId) { mutableStateOf(sessionTitle) }
                AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = { Text(stringResource(R.string.chat_rename_session)) },
                    text = {
                        AppTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            singleLine = true,
                            label = stringResource(R.string.chat_session_name),
                            modifier = Modifier.fillMaxWidth(),
                            colors = dialogTextFieldColors()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                onRenameSession(sessionId, renameText)
                                showRenameDialog = false
                            },
                            enabled = renameText.isNotBlank() && renameText != sessionTitle
                        ) { Text(stringResource(R.string.common_rename)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRenameDialog = false }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                )
            }
        }
    }
}

/** 顶栏当前角色入口：头像 + 角色名，点击打开角色切换抽屉。未解析出角色（默认角色缺失）时显示默认名。 */
@Composable
private fun RoleSwitcherPill(
    currentRole: Role?,
    onClick: () -> Unit
) {
    val t = LocalUIDensityTokens.current
    val hyperOS = LocalHyperOSStyle.current
    val glass = LocalSurfaceGlassStyle.current
    val roleName = currentRole?.name?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.chat_role_default)
    if (hyperOS || glass) {
        Surface(
            shape = RoundedCornerShape(Radius.pill),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(t.scale(1), MaterialTheme.colorScheme.outlineVariant)
        ) {
            // 点击高亮裁剪进 pill 圆角，不再方块溢出容器。
            PressableSurface(
                onClick = onClick,
                shape = RoundedCornerShape(Radius.pill),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = t.scale(10), vertical = t.scale(5)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    RoleAvatar(role = currentRole, size = t.scale(18), fallbackIconScale = 0.75f)
                    Text(
                        text = roleName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = t.scale(96))
                    )
                }
            }
        }
    } else {
        PressableSurface(
            onClick = onClick,
            shape = RoundedCornerShape(LocalUIDensityTokens.current.buttonCorner),
            contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.xs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                RoleAvatar(role = currentRole, size = t.scale(18), fallbackIconScale = 0.75f)
                Text(
                    text = roleName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = t.scale(96))
                )
            }
        }
    }
}

/** 顶栏侧栏开关：两条错位横杠（一长一短错开）自绘图形，替代三横线汉堡，图形约 20dp、触摸区 40dp。 */
@Composable
private fun HeaderMenuBarsButton(
    onClick: () -> Unit,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val t = LocalUIDensityTokens.current
    Box(
        modifier = modifier
            .size(t.scale(36))
            .clip(RoundedCornerShape(t.buttonCorner))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(t.scale(20))) {
            val stroke = t.scale(2).toPx()
            val cap = StrokeCap.Round
            // 长横杠：居中偏左，靠上。
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.32f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.32f),
                strokeWidth = stroke,
                cap = cap
            )
            // 短横杠：左端内收、靠下，与长杠错位。
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.68f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.68f),
                strokeWidth = stroke,
                cap = cap
            )
        }
    }
}

/** 顶栏 ··· 更多菜单：新建会话 / 消息导航 / 打开 Git / 打开终端 收进下拉菜单（原常驻 3 按钮）。 */
@Composable
private fun HeaderOverflowMenu(
    onNewChat: () -> Unit,
    onNavigateToGit: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onOpenMessageTimeline: () -> Unit = {},
    pluginEntries: List<PluginUiEntry> = emptyList(),
    pluginLanguageTag: String = "",
    onOpenPluginPage: (PluginPage) -> Unit = {},
    onRunPluginAction: (PluginUiEntry) -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        CompactHeaderIconButton(
            icon = FeatherIcons.MoreHorizontal,
            contentDescription = stringResource(R.string.chat_header_more),
            tint = com.aiverse.core.theme.pluginIconColorOr(MaterialTheme.colorScheme.onSurfaceVariant),
            onClick = { menuExpanded = true },
            // 终端入口收进「···」菜单后，新手引导第 1 步要把聚光灯打在「···」本体上，
            // 否则目标矩形为空，只剩一层遮罩，用户只能手动点下一步。
            modifier = Modifier.onboardingTarget(OnboardingStep.OPEN_TERMINAL)
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_new_session)) },
                leadingIcon = {
                    Icon(
                        FeatherIcons.Plus,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    menuExpanded = false
                    onNewChat()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_message_navigation)) },
                leadingIcon = {
                    Icon(
                        FeatherIcons.MessageCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    menuExpanded = false
                    onOpenMessageTimeline()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_open_git)) },
                leadingIcon = {
                    Icon(
                        FeatherIcons.GitBranch,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    menuExpanded = false
                    onNavigateToGit()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_open_terminal)) },
                leadingIcon = {
                    Icon(
                        FeatherIcons.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    menuExpanded = false
                    onNavigateToTerminal()
                }
            )
            if (pluginEntries.isNotEmpty()) {
                HorizontalDivider()
                PluginChatHeaderMenuItems(
                    entries = pluginEntries,
                    languageTag = pluginLanguageTag,
                    onOpenPage = onOpenPluginPage,
                    onRunAction = onRunPluginAction,
                    onDismiss = { menuExpanded = false }
                )
            }
        }
    }
}

/** 顶栏上下文用量圆环（··· 左边，固定显示）：点击从圆环下方弹「漫画对话气泡」显示用量明细。 */
@Composable
private fun HeaderContextUsageButton(
    progress: Float,
    tokenEstimated: Boolean,
    detail: ContextUsageDetail?,
    thresholdPercent: Int,
    onCompact: () -> Unit,
    modelName: String? = null
) {
    // MutableTransitionState 驱动开合：动画中途翻转（快速反复点）时 Compose 会正确衔接当前进度，
    // 不会出现 exit 未播完就 enter 的竞争（旧的 mutableStateOf + AnimatedVisibility 在此会卡住）。
    val bubbleExpanded = remember { MutableTransitionState(false) }
    // 圆环中心相对根窗口的 X 坐标（px），用于把浮窗水平对齐到圆环并钳制在屏幕内。
    var ringCenterX by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val t = LocalUIDensityTokens.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val screenWidthPx = with(density) { (screenWidthDp).dp.roundToPx() }
    // 浮窗实际宽度（px），用于水平钳制与屏幕居中；初始给一个合理估计（最大宽 340dp），
    // 避免首次渲染用 0 宽度导致浮窗中心定位偏位，实际值由 ComicUsageBubble.onSizeChanged 更新。
    var bubbleWidthPx by remember { mutableStateOf(with(density) { 340.dp.roundToPx() }) }
    // 首次进入新会话时 detail 为 null（无消息、无真实用量），此时圆环显示但点击无反应，体验断裂。
    // 用空态兜底让点击始终生效：明细卡显示 0% 用量，用户能点开确认当前未占用。
    val effectiveDetail = detail ?: ContextUsageDetail(
        usedTokens = 0,
        budgetTokens = 0,
        fileTokens = 0,
        mcpTokens = 0,
        thinkingTokens = 0,
        otherTokens = 0,
        estimated = tokenEstimated
    )
    Box(
        modifier = Modifier.onGloballyPositioned { coords ->
            ringCenterX = coords.positionInRoot().x + coords.size.width / 2f
        }
    ) {
        ContextUsageRing(
            progress = progress,
            tokenEstimated = tokenEstimated,
            onToggle = { bubbleExpanded.targetState = !bubbleExpanded.targetState }
        )
        if (effectiveDetail != null) {
            val dropOffset = with(density) { t.scale(32).roundToPx() }
            // 横向：浮窗水平居中到屏幕（不随圆环位置偏移，观感最稳定），并钳制在屏幕左右边距内。
            // Popup 的 Alignment.TopCenter 使浮窗中心对齐圆环中心，offset.x 在此基准上平移到屏幕居中。
            val horizontalOffset = run {
                val bubbleWidth = bubbleWidthPx
                val screenCenterX = screenWidthPx / 2
                // 浮窗水平居中到屏幕时其左边缘的屏幕坐标。
                val centeredLeft = screenCenterX - bubbleWidth / 2
                // 钳制到屏幕内（左右各留一点边距，避免贴边）。
                val clampedLeft = centeredLeft.coerceIn(0, (screenWidthPx - bubbleWidth).coerceAtLeast(0))
                // 从「圆环中心对齐」基准平移到屏幕居中的左边缘。
                val ringCenter = ringCenterX.roundToInt()
                clampedLeft - (ringCenter - bubbleWidth / 2)
            }
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(horizontalOffset, dropOffset),
                onDismissRequest = { bubbleExpanded.targetState = false },
                // 仅展开期间可拦截外部点击/返回；收起后不再捕获，避免空弹层挡滚动。
                properties = PopupProperties(focusable = bubbleExpanded.targetState)
            ) {
                // 展开/收起带淡入淡出 + 高度过渡，不再硬切弹出消失。
                // 浮窗位于圆环下方（圆环在浮窗顶部一侧），故开合统一锚定顶部：
                // 展开时自顶部向下生长，收起时向上收缩回圆环，两个方向一致。
                AnimatedVisibility(
                    visibleState = bubbleExpanded,
                    enter = fadeIn(tween(200)) + expandVertically(tween(200), expandFrom = Alignment.Top),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(200), shrinkTowards = Alignment.Top)
                ) {
                    ComicUsageBubble(
                        detail = effectiveDetail,
                        onCompact = onCompact,
                        onClose = { bubbleExpanded.targetState = false },
                        thresholdPercent = thresholdPercent,
                        modelName = modelName,
                        onSizeChanged = { bubbleWidthPx = it.width }
                    )
                }
            }
        }
    }
}

/**
 * 上下文用量浮窗：承载用量明细卡（复用 ContextUsageDetailCard），带阴影、单层边框。
 */
@Composable
private fun ComicUsageBubble(
    detail: ContextUsageDetail,
    onCompact: () -> Unit,
    onClose: () -> Unit,
    thresholdPercent: Int,
    modelName: String? = null,
    onSizeChanged: (androidx.compose.ui.unit.IntSize) -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .onGloballyPositioned { onSizeChanged(it.size) },
        shape = RoundedCornerShape(LocalUIDensityTokens.current.cardCorner),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        // 边框统一收在浮窗这一层：内层明细卡不再描边，避免「外一层里一层」双边框。
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        ContextUsageDetailCard(
            detail = detail,
            onCompact = onCompact,
            onClose = onClose,
            thresholdPercent = thresholdPercent,
            modelName = modelName.orEmpty()
        )
    }
}

/**
 * 上下文用量环（顶栏常驻）：26dp 迷你仪表盘，270° 弧从左下顺时针到右下、底部留 90° 缺口；
 * 颜色按占用程度：无占用白色、≤50% 绿色、≤75% 黄色、>75% 红色。
 * 无占用时只留淡轨道，弧与端帽均不画。点击触发 onToggle（弹出漫画气泡）。
 */
@Composable
private fun ContextUsageRing(
    progress: Float,
    tokenEstimated: Boolean,
    onToggle: () -> Unit
) {
    val t = LocalUIDensityTokens.current
    // 底环要能独立看见：outlineVariant 本身偏淡，再乘低 alpha 会接近透明；改用更实的 outline 并提高不透明度。
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val usageLabel = stringResource(
        if (tokenEstimated) R.string.chat_context_usage_estimated else R.string.chat_context_usage,
        (progress * 100).toInt()
    )
    Box(
        modifier = Modifier
            // 点击区与顶栏 32dp 图标按钮统一；圆环仍保持 26dp，在热区内居中。
            .size(t.scale(32))
            .clip(CircleShape)
            .clickable(onClick = onToggle)
            .semantics { contentDescription = usageLabel },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(t.scale(26))) {
            val stroke = t.scale(3).toPx()
            val arcSize = size.minDimension - stroke
            val topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f)
            val arcStyle = Stroke(width = stroke, cap = StrokeCap.Round)
            val capCenter = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val radius = arcSize / 2f
            // 无占用时轨道用灰色描边而非纯白：白色在浅色主题下不可见，会让圆环看起来"消失"。
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = arcStyle
            )
            if (progress > 0f) {
                val ringColor = contextRingColor(progress)
                val sweep = 270f * progress.coerceIn(0f, 1f)
                drawArc(
                    color = ringColor,
                    startAngle = 135f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                    style = arcStyle
                )
                // 弧端圆点：起点固定画在仪表盘开口左侧（135°），终点随进度推进。
                listOf(135f, 135f + sweep).forEach { angle ->
                    val rad = Math.toRadians(angle.toDouble())
                    drawCircle(
                        color = ringColor,
                        radius = stroke / 2f,
                        center = androidx.compose.ui.geometry.Offset(
                            capCenter.x + radius * cos(rad).toFloat(),
                            capCenter.y + radius * sin(rad).toFloat()
                        )
                    )
                }
            }
        }
    }
}

/** 上下文用量环颜色：无占用白色、≤50% 绿色、≤75% 黄色、>75% 红色。 */
internal fun contextRingColor(progress: Float): Color = when {
    progress <= 0f -> Color.White
    progress > 0.75f -> Color(0xFFE53935)
    progress > 0.5f -> Color(0xFFF9A825)
    else -> Color(0xFF43A047)
}

/** 顶栏紧凑图标按钮：32dp 点击区 + 18dp 图标，比默认 IconButton（40dp）更省横向空间。 */
@Composable
private fun CompactHeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeBackground: Boolean = false
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(LocalUIDensityTokens.current.buttonCorner))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .then(
                    if (activeBackground) {
                        Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ConnectionIndicator(
    state: com.aiverse.feature.agent.domain.container.ConnectionState
) {
    val (dotColor, text) = when (state) {
        com.aiverse.feature.agent.domain.container.ConnectionState.CONNECTED ->
            MaterialTheme.colorScheme.primary to stringResource(R.string.chat_ssh_connected)
        com.aiverse.feature.agent.domain.container.ConnectionState.CONNECTING ->
            MaterialTheme.colorScheme.tertiary to stringResource(R.string.chat_ssh_connecting)
        com.aiverse.feature.agent.domain.container.ConnectionState.FAILED ->
            MaterialTheme.colorScheme.error to stringResource(R.string.chat_ssh_failed)
        com.aiverse.feature.agent.domain.container.ConnectionState.DISCONNECTED ->
            MaterialTheme.colorScheme.outline to stringResource(R.string.chat_ssh_disconnected)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TokenStats(inputTokens: Int, outputTokens: Int) {
    val inStr = formatTokenCount(inputTokens.toLong())
    val outStr = formatTokenCount(outputTokens.toLong())
    Text(
        text = "↑$inStr ↓$outStr",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun RemoteConnectingPlaceholder(
    state: com.aiverse.feature.agent.domain.container.ConnectionState
) {
    val text = when (state) {
        com.aiverse.feature.agent.domain.container.ConnectionState.CONNECTING -> stringResource(R.string.chat_connecting_remote)
        com.aiverse.feature.agent.domain.container.ConnectionState.FAILED -> stringResource(R.string.chat_remote_connect_failed)
        com.aiverse.feature.agent.domain.container.ConnectionState.DISCONNECTED -> stringResource(R.string.chat_no_remote_connection)
        com.aiverse.feature.agent.domain.container.ConnectionState.CONNECTED -> ""
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (state == com.aiverse.feature.agent.domain.container.ConnectionState.CONNECTING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 新对话页空状态：提示语 + 已启用快捷入口卡片网格（welcome entry）。
 *
 * 入口全部为空或全部禁用时仅显示提示语；否则在提示语下方渲染卡片网格。
 * 仅显示已启用入口；disabled 入口不渲染。网格固定每行最多 2 个（卡片横排：图标在左、文案在右）。
 *
 * @param bottomReserve 底部输入区留白（用于垂直居中偏移）。
 * @param welcomeEntries 可配置快捷入口列表（仅渲染 enabled 项；全禁用时整体不显示网格）。
 * @param onNavigateToTerminal OPEN_TERMINAL 入口点击回调。
 * @param onNavigateToGit OPEN_GIT 入口点击回调。
 * @param onNavigateToBrowser OPEN_BROWSER 入口点击回调。
 * @param onNavigateToHistory OPEN_HISTORY 入口点击回调。
 * @param onFillPrompt FILL_PROMPT 入口点击回调，入参为填充提示词（是否填入由调用方决策）。
 */
@Composable
internal fun WelcomeState(
    bottomReserve: Dp,
    modifier: Modifier = Modifier,
    welcomeEntries: List<WelcomeEntry> = emptyList(),
    onNavigateToTerminal: () -> Unit = {},
    onNavigateToGit: () -> Unit = {},
    onNavigateToBrowser: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onFillPrompt: (String) -> Unit = {},
    pluginEntries: List<PluginUiEntry> = emptyList(),
    pluginLanguageTag: String = "",
    onOpenPluginPage: (PluginPage) -> Unit = {},
    onRunPluginAction: (PluginUiEntry) -> Unit = {}
) {
    val enabledEntries = remember(welcomeEntries) { welcomeEntries.filter { it.enabled } }
    // 按当前时间显示的问候 + 熬夜提醒；每分钟刷新，跨时段自动切换。
    var now by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = Calendar.getInstance()
        }
    }
    val currentHour = now.get(Calendar.HOUR_OF_DAY)
    val lateNight = currentHour in 0..4
    val greetingText = stringResource(
        when (currentHour) {
            in 5..10 -> R.string.chat_greeting_morning
            in 11..12 -> R.string.chat_greeting_noon
            in 13..17 -> R.string.chat_greeting_afternoon
            in 18..23 -> R.string.chat_greeting_evening
            else -> R.string.chat_greeting_late_night
        }
    )
    // 顶部图标：浅色用彩色图案（app_launcher_glyph，透明背景），暗色用白色图案（app_launcher_glyph_white），
    // 均为 APP 内 webp 位图，不走 PackageManager.loadIcon——后者会被系统壁纸/图标主题替换或变形。
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    BoxWithConstraints(
        modifier = modifier.padding(Spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        val targetOffset = minOf(-(maxHeight * 0.13f), -(bottomReserve / 2))
        val animatedOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = if (Build.VERSION.SDK_INT >= 30) snap() else tween(durationMillis = 220),
            label = "welcome-offset"
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.offset(y = animatedOffset)
        ) {
            if (!isDark) {
                // 浅色：APP 内 webp 位图（固定资源，不受系统主题影响）。
                Image(
                    painter = painterResource(R.drawable.app_launcher_glyph),
                    contentDescription = null,
                    modifier = Modifier.size(60.dp)
                )
            } else {
                // 暗色：白色图案（透明背景），深色背景上清晰可见。
                Image(
                    painter = painterResource(R.drawable.app_launcher_glyph_white),
                    contentDescription = null,
                    modifier = Modifier.size(60.dp)
                )
            }
            Text(
                text = stringResource(R.string.chat_placeholder),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (lateNight) {
                    stringResource(R.string.chat_greeting_with_reminder, greetingText)
                } else {
                    greetingText
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 仅渲染已启用入口；全部禁用则只保留提示语。
            if (enabledEntries.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.md))
            // 两列等宽对齐：FlowRow 的 scope 没有 weight，weight 不生效导致卡片宽度不一、两行没对齐。
            // 改用手动两列（chunked(2) 每行一个 Row，卡片各 weight(1f) 等宽），奇数个时右侧补 Spacer(weight) 占位保持对齐。
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
            ) {
                enabledEntries.chunked(2).forEach { rowEntries ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowEntries.forEach { entry ->
                            WelcomeEntryCard(
                                entry = entry,
                                enabled = entry.enabled,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    when (entry.action) {
                                        WelcomeEntryAction.OPEN_TERMINAL -> onNavigateToTerminal()
                                        WelcomeEntryAction.OPEN_GIT -> onNavigateToGit()
                                        WelcomeEntryAction.OPEN_BROWSER -> onNavigateToBrowser()
                                        WelcomeEntryAction.OPEN_HISTORY -> onNavigateToHistory()
                                        WelcomeEntryAction.FILL_PROMPT -> onFillPrompt(entry.promptText)
                                    }
                                }
                            )
                        }
                        // 奇数个入口时右侧补空白占位，保证与偶数行等宽对齐（用户要求「用空白硬撑」）。
                        if (rowEntries.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            }
            if (pluginEntries.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.lg))
                PluginChatWelcomeEntries(
                    entries = pluginEntries,
                    languageTag = pluginLanguageTag,
                    onOpenPage = onOpenPluginPage,
                    onRunAction = onRunPluginAction,
                    modifier = Modifier.widthIn(max = 480.dp)
                )
            }
        }
    }
}

/** 快捷入口卡片：图标 + 标题 + 副标题。四张卡片固定等高（[UIDensityTokens.welcomeEntryCardHeight]），disabled 时置灰且点击无动作。 */
@Composable
private fun WelcomeEntryCard(
    entry: WelcomeEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val t = LocalUIDensityTokens.current
    val semantic = MaterialTheme.semanticColors
    val cardDesc = stringResource(R.string.welcome_entry_card_desc, entry.title)
    // 卡片底色：玻璃档走磨砂玻璃面，其它档用入口卡片专属实色底（比通用 surfaceVariant 更贴近页面底色）；
    // 描边只在玻璃档生效，实色档自动透明（与输入框一致）。
    val cardShape = RoundedCornerShape(t.cardCorner)
    PressableSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(t.welcomeEntryCardHeight)
            .semantics { contentDescription = cardDesc }
            .alpha(if (enabled) 1f else 0.45f)
            .border(BorderStroke(t.scale(1), glassBorderOr(semantic.surfaceGlassBorder)), cardShape),
        shape = cardShape,
        color = glassOr(semantic.surfaceGlass, semantic.welcomeEntryCardSurface),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            // 固定卡高后内容居中：各卡片图标列对齐、文本在剩余宽度内省略，避免内容居中导致参差。
            modifier = Modifier.fillMaxWidth()
        ) {
            WelcomeEntryIcon(entry)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 副标题单行：卡片已固定等高，不再用 minLines 撑高；文案过长时单行省略。
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 快捷入口图标：EMOJI/TEXT 用文本渲染，IMAGE 用 coil 异步图片加载（缩略圆角），
 * VECTOR 用 Feather 本地矢量图标（与项目其它页面的图标惯例一致）。
 * 四类统一套 40dp 圆角托盘（8% 主色底）：图标本体 20dp 居中，设置页预览与空态观感一致。
 */
@Composable
private fun WelcomeEntryIcon(entry: WelcomeEntry) {
    val t = LocalUIDensityTokens.current
    Box(
        modifier = Modifier
            .size(t.scale(40))
            .clip(RoundedCornerShape(t.buttonCorner))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        when (entry.iconKind) {
            WelcomeIconKind.IMAGE -> {
                if (entry.iconValue.isNotBlank()) {
                    AsyncImage(
                        // iconValue 为应用私有目录图片绝对路径，用 File 作为 model 让 coil 可靠加载本地文件。
                        model = java.io.File(entry.iconValue),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(t.scale(20))
                            .clip(RoundedCornerShape(t.buttonCorner))
                    )
                }
            }
            WelcomeIconKind.VECTOR -> {
                Icon(
                    imageVector = welcomeEntryVectorIcon(entry.iconValue),
                    contentDescription = null,
                    modifier = Modifier.size(t.scale(20)),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            // 文字图标：≤3 字，以文本渲染 iconValue。
            WelcomeIconKind.TEXT -> {
                Text(
                    text = entry.iconValue,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // EMOJI：旧数据兼容兜底；加载时已迁移为 VECTOR，此处仅渲染默认矢量图标。
            else -> {
                Icon(
                    imageVector = welcomeEntryVectorIcon(entry.iconValue),
                    contentDescription = null,
                    modifier = Modifier.size(t.scale(20)),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
