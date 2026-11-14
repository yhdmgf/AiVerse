package com.aiverse.feature.agent.presentation.component

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
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
import com.aiverse.core.theme.LocalHyperOSStyle
import com.aiverse.core.theme.LocalSurfaceGlassStyle
import com.aiverse.core.theme.glassOr
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import com.aiverse.R
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import com.aiverse.feature.agent.domain.model.AgentMode
import com.aiverse.feature.onboarding.domain.OnboardingStep
import com.aiverse.feature.onboarding.presentation.onboardingTarget
import com.aiverse.feature.settings.domain.model.WelcomeEntry
import com.aiverse.feature.settings.domain.model.WelcomeEntryAction
import com.aiverse.feature.settings.domain.model.WelcomeIconKind
import com.aiverse.feature.settings.presentation.component.ModelLogoIcon
import com.aiverse.feature.settings.presentation.component.welcomeEntryVectorIcon
import com.aiverse.core.ui.AppTextField
import com.aiverse.core.ui.PressableSurface
import com.aiverse.core.ui.dialogTextFieldColors
import com.aiverse.feature.settings.data.remote.ModelTestResult
import com.aiverse.feature.settings.domain.model.AIProviderConfig
import com.aiverse.feature.settings.domain.model.ModelMetadata
import compose.icons.FeatherIcons
import compose.icons.feathericons.MoreHorizontal
import compose.icons.feathericons.GitBranch
import compose.icons.feathericons.Menu
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
    onOpenMessageTimeline: () -> Unit = {}
) {
    Surface(
        color = glassOr(MaterialTheme.semanticColors.surfaceGlass, MaterialTheme.colorScheme.background),
        border = if (LocalSurfaceGlassStyle.current) {
            BorderStroke(1.dp, MaterialTheme.semanticColors.surfaceGlassBorder)
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            var showRenameDialog by remember { mutableStateOf(false) }
            var showModelSheet by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                // 大屏常驻侧栏时隐掉汉堡键：侧栏已经摆在左边，再给个开关反而困惑。
                if (showMenuButton) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.onboardingTarget(OnboardingStep.OPEN_SIDEBAR)
                    ) {
                        Icon(
                            FeatherIcons.Menu,
                            contentDescription = stringResource(R.string.chat_open_sidebar),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Spacer(modifier = Modifier.width(Spacing.sm))
                }
                Column(modifier = Modifier.weight(1f)) {
                    // 点标题弹出「重命名会话」对话框：按容器圆角裁剪高亮，不再方块溢出。
                    if (sessionId != null && onRenameSession != null) {
                        PressableSurface(
                            onClick = { showRenameDialog = true },
                            shape = RoundedCornerShape(Radius.sm),
                            contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs)
                        ) {
                            Text(
                                text = sessionTitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(
                            text = sessionTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val hyperOS = LocalHyperOSStyle.current
                    if (hyperOS) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            // 点击高亮裁剪进 pill 圆角，不再方块溢出容器。
                            PressableSurface(
                                onClick = { showModelSheet = true },
                                enabled = onSelectModel != null,
                                shape = RoundedCornerShape(50),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (!modelName.isNullOrBlank()) {
                                        ModelLogoIcon(modelName = modelName, size = 12.dp)
                                    }
                                    Text(
                                        text = modelName?.takeIf { it.isNotBlank() }
                                            ?: stringResource(R.string.chat_no_model_selected),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } else {
                        // 点模型名直接打开「模型切换」弹窗：给容器边界+圆角，高亮不再凭空一块。
                        PressableSurface(
                            onClick = { showModelSheet = true },
                            enabled = onSelectModel != null,
                            shape = RoundedCornerShape(Radius.sm),
                            contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.xs)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                if (!modelName.isNullOrBlank()) {
                                    ModelLogoIcon(modelName = modelName, size = 14.dp)
                                }
                                Text(
                                    text = modelName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_no_model_selected),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
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
                    onCompact = onCompactContext
                )
                // ··· 更多菜单：新建会话 / 打开 Git / 打开终端 收进下拉菜单。
                HeaderOverflowMenu(
                    onNewChat = onNewChat,
                    onNavigateToGit = onNavigateToGit,
                    onNavigateToTerminal = onNavigateToTerminal,
                    onOpenMessageTimeline = onOpenMessageTimeline
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
            // 点模型名弹「模型切换」弹窗（复用输入区模型选择 ModelSheet）。
            if (showModelSheet && onSelectModel != null) {
                ModelSheet(
                    providers = providers,
                    currentProviderId = providerId,
                    currentModel = modelName.orEmpty(),
                    modelMetadata = modelMetadata,
                    onSelect = { p, m ->
                        onSelectModel(p, m)
                        showModelSheet = false
                    },
                    onDismiss = { showModelSheet = false },
                    runModelTest = runModelTest
                )
            }
        }
    }
}

/** 顶栏 ··· 更多菜单：新建会话 / 消息导航 / 打开 Git / 打开终端 收进下拉菜单（原常驻 3 按钮）。 */
@Composable
private fun HeaderOverflowMenu(
    onNewChat: () -> Unit,
    onNavigateToGit: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onOpenMessageTimeline: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        CompactHeaderIconButton(
            icon = FeatherIcons.MoreHorizontal,
            contentDescription = stringResource(R.string.chat_header_more),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = { menuExpanded = true }
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
    onCompact: () -> Unit
) {
    var bubbleExpanded by remember { mutableStateOf(false) }
    // 圆环中心相对根窗口的 X 坐标（px），用于把浮窗水平居中到屏幕。
    var ringCenterX by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val screenCenterX = with(density) { (screenWidthDp / 2f).dp.roundToPx() }
    Box(
        modifier = Modifier.onGloballyPositioned { coords ->
            ringCenterX = coords.positionInRoot().x + coords.size.width / 2f
        }
    ) {
        ContextUsageRing(
            progress = progress,
            tokenEstimated = tokenEstimated,
            onToggle = { if (detail != null) bubbleExpanded = !bubbleExpanded }
        )
        if (detail != null) {
            val dropOffset = with(density) { 40.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(screenCenterX - ringCenterX.roundToInt(), dropOffset),
                onDismissRequest = { bubbleExpanded = false },
                // 仅展开期间可拦截外部点击/返回；收起后不再捕获，避免空弹层挡滚动。
                properties = PopupProperties(focusable = bubbleExpanded)
            ) {
                // 展开/收起带淡入淡出 + 高度过渡，不再硬切弹出消失。
                // 浮窗位于圆环下方（圆环在浮窗顶部一侧），故开合统一锚定顶部：
                // 展开时自顶部向下生长，收起时向上收缩回圆环，两个方向一致。
                AnimatedVisibility(
                    visible = bubbleExpanded,
                    enter = fadeIn(tween(200)) + expandVertically(tween(200), expandFrom = Alignment.Top),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(200), shrinkTowards = Alignment.Top)
                ) {
                    ComicUsageBubble(
                        detail = detail,
                        onCompact = onCompact,
                        onClose = { bubbleExpanded = false },
                        thresholdPercent = thresholdPercent
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
    thresholdPercent: Int
) {
    Surface(
        modifier = Modifier.widthIn(max = 340.dp),
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        // 边框统一收在浮窗这一层：内层明细卡不再描边，避免「外一层里一层」双边框。
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        ContextUsageDetailCard(
            detail = detail,
            onCompact = onCompact,
            onClose = onClose,
            thresholdPercent = thresholdPercent
        )
    }
}

/**
 * 上下文用量环（顶栏常驻）：环内居中显示占用百分比；
 * 颜色按占用程度：无占用白色、≤50% 绿色、≤75% 黄色、>75% 红色。
 * 点击触发 onToggle（弹出漫画气泡）。
 */
@Composable
private fun ContextUsageRing(
    progress: Float,
    tokenEstimated: Boolean,
    onToggle: () -> Unit
) {
    val ringColor = contextRingColor(progress)
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val usageLabel = stringResource(
        if (tokenEstimated) R.string.chat_context_usage_estimated else R.string.chat_context_usage,
        (progress * 100).toInt()
    )
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggle)
            .semantics { contentDescription = usageLabel },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val arcSize = size.minDimension - stroke
            val topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f)
            // 无占用时轨道用灰色描边而非纯白：白色在浅色主题下不可见，会让圆环看起来"消失"。
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (progress > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        // 环内居中百分比数字：颜色跟随占用程度（无占用时用可读的浅色，避免白字在浅色主题下不可见）。
        Text(
            text = stringResource(R.string.chat_context_usage_percent, (progress * 100).toInt()),
            style = MaterialTheme.typography.labelSmall,
            color = if (progress > 0f) ringColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
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
            .clip(RoundedCornerShape(Radius.sm))
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WelcomeState(
    bottomReserve: Dp,
    modifier: Modifier = Modifier,
    welcomeEntries: List<WelcomeEntry> = emptyList(),
    onNavigateToTerminal: () -> Unit = {},
    onNavigateToGit: () -> Unit = {},
    onNavigateToBrowser: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onFillPrompt: (String) -> Unit = {}
) {
    val enabledEntries = remember(welcomeEntries) { welcomeEntries.filter { it.enabled } }
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
            Image(
                painter = painterResource(R.drawable.ic_launcher_monochrome),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(60.dp)
            )
            Text(
                text = stringResource(R.string.chat_placeholder),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.chat_input_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 仅渲染已启用入口；全部禁用则只保留提示语。
            if (enabledEntries.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.md))
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    maxItemsInEachRow = 2
                ) {
                    enabledEntries.forEach { entry ->
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
                }
            }
        }
    }
}

/** 快捷入口卡片：图标 + 标题 + 副标题（副标题可空，空则不占位）。disabled 时置灰且点击无动作。 */
@Composable
private fun WelcomeEntryCard(
    entry: WelcomeEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val cardDesc = stringResource(R.string.welcome_entry_card_desc, entry.title)
    PressableSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .semantics { contentDescription = cardDesc }
            .alpha(if (enabled) 1f else 0.45f),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentPadding = PaddingValues(Spacing.md)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WelcomeEntryIcon(entry)
            Column {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.subtitle.isNotBlank()) {
                    Text(
                        text = entry.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 快捷入口图标：EMOJI/TEXT 用文本渲染，IMAGE 用 coil 异步图片加载（缩略圆角），
 * VECTOR 用 Feather 本地矢量图标（与项目其它页面的图标惯例一致）。
 */
@Composable
private fun WelcomeEntryIcon(entry: WelcomeEntry) {
    when (entry.iconKind) {
        WelcomeIconKind.IMAGE -> {
            if (entry.iconValue.isNotBlank()) {
                AsyncImage(
                    // iconValue 为应用私有目录图片绝对路径，用 File 作为 model 让 coil 可靠加载本地文件。
                    model = java.io.File(entry.iconValue),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                )
            }
        }
        WelcomeIconKind.VECTOR -> {
            Icon(
                imageVector = welcomeEntryVectorIcon(entry.iconValue),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
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
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

