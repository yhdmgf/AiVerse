package com.aiverse.feature.agent.presentation.component

import android.content.ClipData
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.aiverse.R
import com.aiverse.core.theme.Brand
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import com.aiverse.core.theme.LocalHyperOSStyle
import com.aiverse.core.theme.LocalInputFontSizeSp
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.glassBorderOr
import com.aiverse.core.theme.chatPanelGlassOr
import com.aiverse.core.theme.LocalChatInputStyle
import com.aiverse.core.theme.chatPanelSurface
import com.aiverse.core.ui.rememberImeBottomInset
import com.aiverse.core.ui.PressableSurface
import com.aiverse.feature.onboarding.domain.OnboardingStep
import com.aiverse.feature.onboarding.presentation.onboardingTarget
import com.aiverse.feature.agent.domain.command.SlashCommand
import com.aiverse.feature.agent.domain.command.SlashCommandKind
import com.aiverse.feature.agent.domain.model.AgentMode
import com.aiverse.feature.agent.domain.model.GoalStatus
import com.aiverse.feature.agent.presentation.SessionGoalUi
import com.aiverse.feature.agent.domain.model.ReasoningEffort
import com.aiverse.feature.agent.domain.model.TodoItem
import com.aiverse.feature.agent.domain.provider.ReasoningProtocol
import com.aiverse.feature.agent.domain.provider.reasoningCapabilityOf
import com.aiverse.feature.agent.domain.provider.reasoningProtocolOf
import com.aiverse.feature.agent.domain.permission.PermissionChoice
import com.aiverse.feature.agent.domain.tool.PendingToolPermission
import com.aiverse.feature.agent.domain.tool.mode.PlanApprovalRequest
import com.aiverse.feature.agent.domain.mention.FileMentionState
import com.aiverse.feature.agent.domain.mention.fileMentionQuery
import com.aiverse.feature.agent.presentation.QueuedRequest
import com.aiverse.feature.settings.data.remote.ModelTestResult
import com.aiverse.feature.settings.data.repository.InputBarPrefs
import com.aiverse.feature.settings.data.repository.DEFAULT_QUICK_ACTION_ORDER
import com.aiverse.feature.settings.data.repository.InputBarQuickAction
import com.aiverse.feature.settings.domain.model.AIProviderConfig
import com.aiverse.feature.settings.domain.model.ModelMetadata
import com.aiverse.feature.settings.domain.model.modelMetadataKey
import com.aiverse.feature.settings.domain.model.ProviderDashboardState
import com.aiverse.feature.workspace.presentation.WorkspaceViewModel
import com.aiverse.feature.workspace.presentation.component.WorkspaceIconButton
import com.aiverse.core.ui.ExpandableChevronIcon
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertCircle
import compose.icons.feathericons.ArrowUp
import compose.icons.feathericons.Check
import compose.icons.feathericons.Copy
import compose.icons.feathericons.Pause
import compose.icons.feathericons.Play
import compose.icons.feathericons.RotateCcw
import compose.icons.feathericons.Target
import compose.icons.feathericons.X
import compose.icons.feathericons.Maximize
import compose.icons.feathericons.Minimize
import compose.icons.feathericons.Box
import compose.icons.feathericons.Cpu
import compose.icons.feathericons.Folder
import compose.icons.feathericons.Globe
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Square
import compose.icons.feathericons.Shield
import compose.icons.feathericons.Zap
import kotlinx.coroutines.launch

/** 输入框高度标准档基准上下限（裸 dp）：超过上限后文本在框内滚动。与 TextField 的 heightIn 一致。
 *  实际值在 [ChatInputBar] 内按密度令牌缩放。 */
private const val INPUT_FIELD_MIN_HEIGHT_BASE_DP = 36
private const val INPUT_FIELD_MAX_HEIGHT_BASE_DP = 140

/**
 * 输入框快捷栏的一个可开关项插槽：供可见按钮渲染与溢出菜单（图标+文字）复用。
 *
 * [buttonContent] 为可见时渲染的按钮；[hostContent] 为溢出时承载内部弹层的隐形宿主
 * （buttonVisible=false，不显示按钮，但保持组合以响应 [QuickActionSlot.onTrigger] 打开弹层）。
 */
private class QuickActionSlot(
    val action: InputBarQuickAction,
    val icon: ImageVector,
    val labelRes: Int,
    val onTrigger: () -> Unit,
    val buttonContent: @Composable () -> Unit,
    val hostContent: @Composable () -> Unit
)

@Composable
internal fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    /** 回车键是否直接发送：开启后 IME 回车键变为「发送」，关闭则回车换行（默认）。 */
    enterToSend: Boolean = false,
    onStop: () -> Unit,
    isBusy: Boolean,
    workspaceViewModel: WorkspaceViewModel?,
    hasRunningSessions: () -> Boolean,
    onSwitchWorkspaceConfirmed: () -> Unit = {},
    activeProvider: AIProviderConfig?,
    providers: List<AIProviderConfig>,
    modelMetadata: Map<String, ModelMetadata>,
    onSelectModel: (String, String) -> Unit,
    currentMode: AgentMode,
    onToggleMode: (AgentMode) -> Unit,
    currentGoal: SessionGoalUi? = null,
    onSetGoalPaused: (Boolean) -> Unit = {},
    onClearGoal: () -> Unit = {},
    reasoningEffort: ReasoningEffort,
    onReasoningEffortChange: (ReasoningEffort) -> Unit,
    pendingAttachments: List<PendingUploadAttachment>,
    onRemoveAttachment: (Int) -> Unit,
    canUploadFiles: Boolean,
    canUploadImages: Boolean,
    onUploadFile: () -> Unit,
    onUploadApp: () -> Unit,
    onUploadImage: () -> Unit,
    onTakePhoto: () -> Unit,
    slashCommands: List<SlashCommand> = emptyList(),
    fileMentionState: FileMentionState = FileMentionState.Idle,
    onFileMentionOpen: () -> Unit = {},
    onFileMentionDismiss: () -> Unit = {},
    matchFileMentions: (String) -> List<String> = { emptyList() },
    queuedRequests: List<QueuedRequest> = emptyList(),
    onRemoveQueued: (String) -> Unit = {},
    onMoveQueued: (Int, Int) -> Unit = { _, _ -> },
    onEditQueued: (QueuedRequest) -> Unit = {},
    onInterjectQueued: (String) -> Unit = {},
    dashboardState: ProviderDashboardState = ProviderDashboardState.Idle,
    onRefreshDashboard: () -> Unit = {},
    onRefreshDashboardByButton: () -> Unit = {},
    onDashboardExpandedChange: (Boolean) -> Unit = {},
    todoItems: List<TodoItem> = emptyList(),
    sessionId: String = "",
    onTodoExpandedChange: (Boolean) -> Unit = {},
    forceCollapseDashboard: Boolean = false,
    /** 消息列表正在滚动时内容区淡出到 40%，停止滚动恢复；用于长列表阅读时降低底部干扰（同 git 页 tab 栏）。 */
    isScrolling: Boolean = false,
    forceOpenModelSheet: Boolean = false,
    onSelectModelInOnboarding: (() -> Unit)? = null,
    onModelSheetDismiss: (() -> Unit)? = null,
    /** 长按模型图标时打开 AI 供应商设置页。 */
    onOpenProviders: () -> Unit = {},
    /** 长按联网搜索图标时打开搜索提供商设置页。 */
    onOpenSearchProviders: () -> Unit = {},
    /** 点击「优化输入」按钮：把输入框内容交给模型润色后填回。 */
    onOptimizeInput: () -> Unit = {},
    /** 点击「优化输入」按钮的停止态：取消正在进行的优化任务。 */
    onCancelOptimize: () -> Unit = {},
    /** 优化输入是否进行中：进行时按钮显示进度环，点击改为停止。 */
    isOptimizing: Boolean = false,
    /** 是否存在可撤销的「优化输入」回填：为 true 时全屏按钮下方常驻显示撤销小按钮。 */
    canUndoOptimize: Boolean = false,
    /** 点击撤销小按钮：恢复最近一次「优化输入」回填前的旧草稿。 */
    onUndoOptimize: () -> Unit = {},
    /** 长按「优化输入」按钮时打开默认模型配置页。 */
    onOpenOptimizeModelSettings: () -> Unit = {},
    /** 点击输入区的 MCP 图标，直接打开 MCP 服务器设置页。 */
    onOpenMcp: () -> Unit = {},
    /** 待发送附件「应用内预览」：打开编辑器并直接进预览模式（容器路径）。 */
    onOpenInEditor: (String) -> Unit = {},
    /** 主页模型弹层的连通性测试：null（无设置页 ViewModel 的宿主）时弹层不显示测试按钮。 */
    onRunModelTest: (suspend (AIProviderConfig, String) -> ModelTestResult)? = null,
    /** 输入框快捷栏可开关项快照。 */
    inputBarPrefs: InputBarPrefs = InputBarPrefs(),
    /** 切换联网搜索工具启用状态。 */
    onToggleWebSearch: () -> Unit = {},
    /** 联网搜索工具当前是否启用。 */
    webSearchEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val hasContent = value.isNotBlank() || pendingAttachments.isNotEmpty()
    val canSend = hasContent
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showFullScreenInput by remember { mutableStateOf(false) }
    // TextField 的 String 重载在整串替换时会保留旧 selection 下标（点快捷命令补全时光标会停在中间），
    // 这里自持 TextFieldValue：外部改值时把光标移到末尾。
    var inputFieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (inputFieldValue.text != value) {
            inputFieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }
    // 命令名后出现空格（进入参数/正文）或 @（文件引用优先）即退出选命令阶段
    val slashArgs = value.substringAfter('/', "")
    val showSlashMenu = slashCommands.isNotEmpty() &&
        value.startsWith("/") &&
        !value.contains("\n") &&
        !slashArgs.contains(' ') &&
        !slashArgs.contains('@')
    val filteredCommands = if (showSlashMenu) {
        if (value == "/") slashCommands
        else slashCommands.filter { "/${it.name}".startsWith(value) }
    } else emptyList()

    // `@` 文件引用：`/` 菜单优先；查询词从文本末尾提取。忙碌排队时同样可用（队列消息支持引用）。
    val mentionQuery = if (!showSlashMenu) fileMentionQuery(value) else null
    val mentionFiles = remember(mentionQuery, fileMentionState) {
        if (mentionQuery != null && fileMentionState is FileMentionState.Ready) {
            matchFileMentions(mentionQuery)
        } else {
            emptyList()
        }
    }
    LaunchedEffect(mentionQuery) {
        if (mentionQuery != null) onFileMentionOpen() else onFileMentionDismiss()
    }
    // 选中文件：把 `@查询词` 替换为 `@相对路径 `，光标移到末尾。
    val applyMention: (String) -> Unit = { path ->
        val at = value.lastIndexOf('@')
        if (at >= 0) {
            val newText = value.substring(0, at) + "@" + path + " "
            inputFieldValue = TextFieldValue(newText, TextRange(newText.length))
            onValueChange(newText)
        }
    }

    Surface(
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth()
    ) {
        val densityTokens = LocalUIDensityTokens.current
        // 插件覆盖层：声明了 chatInputMinHeight 时以插件值为准（按密度缩放后再叠加），否则回退标准档基准。
        val chatInputOverride = LocalChatInputStyle.current
        val inputFieldMinHeight = chatInputOverride?.minHeight?.let(densityTokens::scale)
            ?: densityTokens.scale(INPUT_FIELD_MIN_HEIGHT_BASE_DP)
        val inputFieldMaxHeight = densityTokens.scale(INPUT_FIELD_MAX_HEIGHT_BASE_DP)
        val imeInset = rememberImeBottomInset()
        // 滚动弱化：内容区（slash 菜单/排队面板/数据面板/输入框本体）整体淡出到 40%，蒙版渐变保持不透明。
        val contentAlpha by animateFloatAsState(
            targetValue = if (isScrolling) 0.4f else 1f,
            animationSpec = tween(200),
            label = "inputbar-content-alpha"
        )
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val dashboardBodyMaxHeight = (maxHeight - imeInset - Spacing.md).coerceAtLeast(0.dp) * 0.4f
            // issue #269：原来的底部半透明渐变颜色蒙版已删除，改由 AIChatPanel 内容层的底部渐隐
            // （BlendMode.DstIn 擦除内容本身）负责防止内容压在输入框/导航栏上，不再往输入区铺色；
            // 该渐隐随 inputBarReservePx 上移，键盘弹起整块跟着上移。输入框自身的玻璃底保留。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = contentAlpha }
                    .padding(horizontal = densityTokens.lg)
                    .padding(bottom = Spacing.md)
                    .padding(bottom = imeInset)
            ) {
            if (filteredCommands.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.sm),
                    shape = RoundedCornerShape(Radius.lg),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    ) {
                        filteredCommands.forEach { command ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Radius.sm))
                                    .clickable {
                                        onValueChange(
                                            if (command.acceptsArgs) "/${command.name} " else "/${command.name}"
                                        )
                                    }
                                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "/${command.name}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(Spacing.sm))
                                Text(
                                    command.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (command.kind == SlashCommandKind.SKILL) {
                                    Spacer(Modifier.width(Spacing.sm))
                                    Text(
                                        stringResource(R.string.settings_skills),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (mentionQuery != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.sm),
                    shape = RoundedCornerShape(Radius.lg),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    ) {
                        // 提示拆成「一句话结论 + 原因/下一步」两行：容器未就绪时用户不知道要先去终端初始化，
                        // 只写「无法搜索文件」会被误以为是文件列表坏了。
                        val mentionHint: Pair<String, String?>? = when (fileMentionState) {
                            FileMentionState.Idle, FileMentionState.Loading ->
                                stringResource(R.string.chat_mention_loading) to null
                            FileMentionState.Unavailable ->
                                stringResource(R.string.chat_mention_unavailable) to
                                    stringResource(R.string.chat_mention_unavailable_hint)
                            is FileMentionState.Error ->
                                stringResource(R.string.chat_mention_error) to null
                            is FileMentionState.Ready -> null
                        }
                        when {
                            mentionHint != null -> Column(
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                            ) {
                                Text(
                                    mentionHint.first,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                mentionHint.second?.let { hint ->
                                    Text(
                                        text = hint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                        modifier = Modifier.padding(top = Spacing.xs)
                                    )
                                }
                            }
                            mentionFiles.isEmpty() -> Text(
                                stringResource(R.string.chat_mention_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                            )
                            else -> mentionFiles.forEach { path ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(Radius.sm))
                                        .clickable { applyMention(path) }
                                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        path,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (queuedRequests.isNotEmpty()) {
                QueuedRequestPanel(
                    queuedRequests = queuedRequests,
                    sessionId = sessionId,
                    forceCollapse = forceCollapseDashboard,
                    onRemoveQueued = onRemoveQueued,
                    onMoveQueued = onMoveQueued,
                    onEditQueued = onEditQueued,
                    onInterjectQueued = onInterjectQueued
                )
            }

            if (todoItems.isNotEmpty()) {
                TodoDashboardBar(
                    items = todoItems,
                    sessionId = sessionId,
                    forceCollapse = forceCollapseDashboard,
                    onExpandedChange = onTodoExpandedChange
                )
            }

            if (activeProvider != null && activeProvider.dashboardScriptPath.isNotBlank()) {
                ProviderDashboardBar(
                    provider = activeProvider,
                    state = dashboardState,
                    onRefresh = onRefreshDashboard,
                    onRefreshByButton = onRefreshDashboardByButton,
                    onExpandedChange = onDashboardExpandedChange,
                    maxExpandedBodyHeight = dashboardBodyMaxHeight,
                    forceCollapse = forceCollapseDashboard
                )
            }

            currentGoal?.let { goal ->
                GoalBannerRow(
                    goal = goal,
                    onSetPaused = onSetGoalPaused,
                    onClear = onClearGoal
                )
            }

            val hyperOS = LocalHyperOSStyle.current
            // 插件覆盖：声明了 chatInput.cornerRadius / chatInput.shadow 时以插件值为准，
            // 否则回退现状（HyperOS 阴影档走 elevationMd，圆角走密度令牌 fieldCorner）。
            val inputCorner = chatInputOverride?.cornerRadius?.let(densityTokens::scale)
                ?: densityTokens.fieldCorner
            val inputShadow = chatInputOverride?.shadow?.let(densityTokens::scale)
                ?: if (hyperOS) densityTokens.elevationMd else 0.dp
            val inputShape = RoundedCornerShape(inputCorner)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (inputShadow > 0.dp) {
                            Modifier.shadow(
                                elevation = inputShadow,
                                shape = inputShape,
                                clip = false,
                                ambientColor = Color.Black.copy(alpha = densityTokens.ambientAlpha),
                                spotColor = Color.Black.copy(alpha = densityTokens.spotAlpha)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clip(inputShape)
                    .background(chatPanelGlassOr(MaterialTheme.semanticColors.surfaceGlass))
                    .border(
                        if (hyperOS) 0.5.dp else 1.dp,
                        if (hyperOS) MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.outlineVariant,
                        inputShape
                    )
                    .padding(
                        // 第一行内容统一 16dp 左内距；右侧对称。
                        horizontal = Spacing.lg,
                        vertical = if (hyperOS) 6.dp else Spacing.xs / 2
                    )
            ) {
                PendingAttachmentPreviewList(
                    attachments = pendingAttachments,
                    onRemoveAttachment = onRemoveAttachment,
                    onOpenInEditor = onOpenInEditor
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    TextField(
                        value = inputFieldValue,
                        onValueChange = { new ->
                            inputFieldValue = new
                            onValueChange(new.text)
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = LocalInputFontSizeSp.current.sp
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = inputFieldMinHeight, max = inputFieldMaxHeight),
                        placeholder = {
                            Text(
                                stringResource(if (isBusy) R.string.chat_queue_hint else R.string.chat_input_placeholder),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        enabled = true,
                        keyboardOptions = if (enterToSend) {
                            KeyboardOptions(imeAction = ImeAction.Send)
                        } else {
                            KeyboardOptions(imeAction = ImeAction.Default)
                        },
                        keyboardActions = if (enterToSend) {
                            KeyboardActions(onSend = { onSend() })
                        } else {
                            KeyboardActions.Default
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )

                    Column(
                        horizontalAlignment = Alignment.Start
                    ) {
                        IconButton(
                            onClick = { showFullScreenInput = true },
                            modifier = Modifier.size(densityTokens.scale(32))
                        ) {
                            Icon(
                                FeatherIcons.Maximize,
                                contentDescription = stringResource(R.string.chat_input_expand),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(densityTokens.scale(18))
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 模式名纯文字：底色容器已去掉，只保留文字色（AUTO 保留 error 安全语义）。
                        val modeTextColor = when (currentMode) {
                            AgentMode.AUTO -> MaterialTheme.colorScheme.error
                            AgentMode.BUILD,
                            AgentMode.PLAN,
                            AgentMode.EXPERT -> MaterialTheme.colorScheme.onSurface
                        }
                        val modeContext = LocalContext.current
                        // 模式选择菜单：点击按钮向上展开四个选项（自动/构建/计划/专家），选后才切换，不再循环切换。
                        var modeMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            // 点击高亮裁剪进按钮圆角，不再方块溢出容器。
                            // 色块去掉后文字热区会缩小，用 touchTarget 保底，避免触控目标低于推荐值。
                            PressableSurface(
                                onClick = { modeMenuExpanded = !modeMenuExpanded },
                                shape = RoundedCornerShape(Radius.xs),
                                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs),
                                modifier = Modifier.heightIn(min = densityTokens.touchTarget)
                            ) {
                                Text(
                                    text = stringResource(currentMode.labelRes),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = modeTextColor
                                    )
                                )
                            }
                            if (modeMenuExpanded) {
                                Popup(
                                    alignment = Alignment.BottomStart, // 向上展开
                                    onDismissRequest = { modeMenuExpanded = false },
                                    properties = PopupProperties(focusable = true, dismissOnBackPress = true)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 8.dp,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .width(IntrinsicSize.Min)
                                                .padding(vertical = 4.dp)
                                        ) {
                                            // 菜单顺序：自动 / 构建 / 计划 / 专家
                                            listOf(
                                                AgentMode.AUTO,
                                                AgentMode.BUILD,
                                                AgentMode.PLAN,
                                                AgentMode.EXPERT
                                            ).forEach { m ->
                                                val selected = m == currentMode
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            modeMenuExpanded = false
                                                            if (m != currentMode) {
                                                                onToggleMode(m)
                                                                // 切换后提示当前模式状态，避免用户不知道切到了什么。
                                                                Toast.makeText(
                                                                    modeContext,
                                                                    modeContext.getString(m.switchMessageRes),
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        }
                                                        .background(
                                                            if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                                                        )
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = stringResource(m.labelRes),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = if (selected) {
                                                            MaterialTheme.colorScheme.onSecondaryContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurface
                                                        }
                                                    )
                                                    if (selected) {
                                                        Spacer(Modifier.width(Spacing.xs))
                                                        Icon(
                                                            FeatherIcons.Check,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // 模式名与右侧工具条之间的竖直分割线，替代原纯色块间隔。
                        Box(
                            modifier = Modifier
                                .padding(horizontal = Spacing.xs)
                                .width(densityTokens.scale(1))
                                .height(densityTokens.scale(14))
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        val capability = reasoningCapabilityOf(activeProvider, activeProvider?.effectiveModel.orEmpty(), modelMetadata)
                        val hasReasoningControl = !capability.isKnown ||
                            capability.levels.isNotEmpty() || capability.canDisable
                        BoxWithConstraints(modifier = Modifier.weight(1f)) {
                            val density = LocalDensity.current
                            val maxWpx = with(density) { maxWidth.toPx() }
                            var itemWidths by remember { mutableStateOf<Map<InputBarQuickAction, Int>>(emptyMap()) }
                            // 溢出菜单打开弹层信号：每次点击溢出项时递增，对应弹层组件（隐形宿主）据此打开。
                            var modelOpenSignal by remember { mutableStateOf(0) }
                            var thinkingOpenSignal by remember { mutableStateOf(0) }
                            var folderOpenSignal by remember { mutableStateOf(0) }
                            // 快捷栏按钮顺序统一为设置页「快捷栏」排序（按使用频率，高频靠前）。
                            // 每个可开关项携带图标/中文名/触发逻辑，供溢出菜单以「图标+文字」项展示。
                            val quickSlots: List<QuickActionSlot> = buildList {
                                if (inputBarPrefs.showModel) add(QuickActionSlot(
                                    action = InputBarQuickAction.MODEL,
                                    icon = FeatherIcons.Cpu,
                                    labelRes = R.string.inputbar_action_model,
                                    onTrigger = { modelOpenSignal++ },
                                    buttonContent = @Composable {
                                        ModelIconButton(
                                            provider = activeProvider,
                                            providers = providers,
                                            modelMetadata = modelMetadata,
                                            onSelectModel = onSelectModel,
                                            forceOpenSheet = forceOpenModelSheet,
                                            onSelectSuccessInOnboarding = onSelectModelInOnboarding,
                                            onSheetDismiss = onModelSheetDismiss,
                                            onOpenProviders = onOpenProviders,
                                            runModelTest = onRunModelTest,
                                            openSignal = modelOpenSignal,
                                            modifier = Modifier.onboardingTarget(OnboardingStep.OPEN_MODEL_PICKER)
                                        )
                                    },
                                    hostContent = @Composable {
                                        ModelIconButton(
                                            provider = activeProvider,
                                            providers = providers,
                                            modelMetadata = modelMetadata,
                                            onSelectModel = onSelectModel,
                                            onOpenProviders = onOpenProviders,
                                            runModelTest = onRunModelTest,
                                            openSignal = modelOpenSignal,
                                            buttonVisible = false
                                        )
                                    }
                                ))
                                if (workspaceViewModel != null && inputBarPrefs.showFolder) {
                                    val ws = workspaceViewModel
                                    add(QuickActionSlot(
                                        action = InputBarQuickAction.FOLDER,
                                        icon = FeatherIcons.Folder,
                                        labelRes = R.string.inputbar_action_folder,
                                        onTrigger = { folderOpenSignal++ },
                                        buttonContent = @Composable {
                                            WorkspaceIconButton(
                                                viewModel = ws,
                                                hasRunningSessions = hasRunningSessions,
                                                onSwitchConfirmed = onSwitchWorkspaceConfirmed,
                                                openSignal = folderOpenSignal,
                                                modifier = Modifier.size(32.dp),
                                                iconSize = 20.dp
                                            )
                                        },
                                        hostContent = @Composable {
                                            WorkspaceIconButton(
                                                viewModel = ws,
                                                hasRunningSessions = hasRunningSessions,
                                                onSwitchConfirmed = onSwitchWorkspaceConfirmed,
                                                openSignal = folderOpenSignal,
                                                buttonVisible = false
                                            )
                                        }
                                    ))
                                }
                                if (inputBarPrefs.showMcp) add(QuickActionSlot(
                                    action = InputBarQuickAction.MCP,
                                    icon = FeatherIcons.Box,
                                    labelRes = R.string.inputbar_action_mcp,
                                    onTrigger = { onOpenMcp() },
                                    buttonContent = @Composable {
                                        IconButton(onClick = onOpenMcp, modifier = Modifier.size(32.dp)) {
                                            Icon(
                                                FeatherIcons.Box,
                                                contentDescription = stringResource(R.string.settings_mcp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    },
                                    hostContent = @Composable { }
                                ))
                                if (hasReasoningControl && inputBarPrefs.showThinking) add(QuickActionSlot(
                                    action = InputBarQuickAction.THINKING,
                                    icon = FeatherIcons.Zap,
                                    labelRes = R.string.inputbar_action_thinking,
                                    onTrigger = { thinkingOpenSignal++ },
                                    buttonContent = @Composable {
                                        ReasoningEffortSelector(
                                            effort = reasoningEffort,
                                            capability = capability,
                                            protocol = activeProvider?.let { reasoningProtocolOf(it) } ?: ReasoningProtocol.OPENAI_CHAT,
                                            model = activeProvider?.effectiveModel.orEmpty(),
                                            onChange = onReasoningEffortChange,
                                            enabled = !isBusy,
                                            openSignal = thinkingOpenSignal
                                        )
                                    },
                                    hostContent = @Composable {
                                        ReasoningEffortSelector(
                                            effort = reasoningEffort,
                                            capability = capability,
                                            protocol = activeProvider?.let { reasoningProtocolOf(it) } ?: ReasoningProtocol.OPENAI_CHAT,
                                            model = activeProvider?.effectiveModel.orEmpty(),
                                            onChange = onReasoningEffortChange,
                                            enabled = !isBusy,
                                            openSignal = thinkingOpenSignal,
                                            buttonVisible = false
                                        )
                                    }
                                ))
                                if (inputBarPrefs.showWebSearch) add(QuickActionSlot(
                                    action = InputBarQuickAction.WEB_SEARCH,
                                    icon = FeatherIcons.Globe,
                                    labelRes = R.string.inputbar_action_web_search,
                                    onTrigger = { onToggleWebSearch() },
                                    buttonContent = @Composable {
                                        // 单击切换联网搜索；长按跳「设置 → 搜索提供商」，与模型按钮长按跳供应商一致。
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .combinedClickable(
                                                    onClick = onToggleWebSearch,
                                                    onLongClick = onOpenSearchProviders
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                FeatherIcons.Globe,
                                                contentDescription = stringResource(R.string.chat_web_search_toggle),
                                                tint = if (webSearchEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    },
                                    hostContent = @Composable { }
                                ))
                            }.sortedBy { DEFAULT_QUICK_ACTION_ORDER.indexOf(it.action) }
                            val visibleItems = mutableListOf<QuickActionSlot>()
                            val overflowItems = mutableListOf<QuickActionSlot>()
                            // 首帧测量完成前 itemWidths 为空，用预估宽度分配，避免把全部按钮塞进单行被裁剪
                            // （表现为按钮「隐藏」且无溢出入口）。
                            val estimateW = with(density) { 32.dp.toPx() }.toInt()
                            var used = 0
                            for (slot in quickSlots) {
                                val w = itemWidths[slot.action] ?: estimateW
                                if (used + w <= maxWpx) {
                                    visibleItems.add(slot)
                                    used += w
                                } else {
                                    overflowItems.add(slot)
                                }
                            }
                            var overflowExpanded by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                visibleItems.forEach { slot ->
                                    Box(
                                        modifier = Modifier.onSizeChanged {
                                            itemWidths = itemWidths + (slot.action to it.width)
                                        }
                                    ) { slot.buttonContent() }
                                }
                                if (overflowItems.isNotEmpty()) {
                                    Box {
                                        // 溢出入口：MoreVert（···）图标，点开标准 DropdownMenu（图标+文字项）。
                                        IconButton(onClick = { overflowExpanded = !overflowExpanded }, modifier = Modifier.size(32.dp)) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = stringResource(R.string.chat_more_tools),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = overflowExpanded,
                                            onDismissRequest = { overflowExpanded = false }
                                        ) {
                                            overflowItems.forEach { slot ->
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(slot.labelRes)) },
                                                    leadingIcon = {
                                                        Icon(
                                                            slot.icon,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    },
                                                    onClick = {
                                                        overflowExpanded = false
                                                        slot.onTrigger()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // 隐形宿主：让溢出项中的弹层组件（model/thinking/folder）保持组合，
                            // 点击溢出菜单项递增 openSignal 时才能打开对应弹层（按钮本身隐藏）。
                            overflowItems.forEach { slot ->
                                Box(modifier = Modifier.size(0.dp)) {
                                    slot.hostContent()
                                }
                            }
                        }
                    }
                    if (inputBarPrefs.showOptimize) {
                        // 优化输入：单击把输入框内容交给模型润色后填回（空文本无反应）；长按跳默认模型配置页。
                        // 优化中：显示进度环，点击停止；非优化中显示 ✨，点击触发优化。
                        // 可撤销回填：按钮原地变成 ↺（还原态），单击直接还原原文，长按弹出还原/继续优化选项。
                        val isRestoreState = canUndoOptimize && !isOptimizing
                        val optimizeLabel = when {
                            isOptimizing -> stringResource(R.string.chat_optimize_stop)
                            isRestoreState -> stringResource(R.string.chat_optimize_undo)
                            else -> stringResource(R.string.chat_optimize_input)
                        }
                        var restoreMenuExpanded by remember { mutableStateOf(false) }
                        val progressAlpha by animateFloatAsState(
                            targetValue = if (isOptimizing) 1f else 0f,
                            animationSpec = tween(80),
                            label = "optimizeProgressAlpha"
                        )
                        val restoreRotation by animateFloatAsState(
                            targetValue = if (isRestoreState) 0f else -90f,
                            animationSpec = tween(160),
                            label = "optimizeRestoreRotation"
                        )
                        val restoreAlpha by animateFloatAsState(
                            targetValue = if (isRestoreState) 1f else 0f,
                            animationSpec = tween(160),
                            label = "optimizeRestoreAlpha"
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .semantics { contentDescription = optimizeLabel }
                                .combinedClickable(
                                    onClick = {
                                        when {
                                            isOptimizing -> onCancelOptimize()
                                            isRestoreState -> onUndoOptimize()
                                            value.isNotBlank() -> onOptimizeInput()
                                        }
                                    },
                                    onLongClick = {
                                        if (isRestoreState) {
                                            restoreMenuExpanded = true
                                        } else {
                                            onOpenOptimizeModelSettings()
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (progressAlpha > 0f) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .graphicsLayer { alpha = progressAlpha },
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (restoreAlpha > 0f) {
                                Icon(
                                    FeatherIcons.RotateCcw,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .graphicsLayer {
                                            alpha = restoreAlpha
                                            rotationZ = restoreRotation
                                        }
                                )
                            }
                            if (restoreAlpha < 1f) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .graphicsLayer { alpha = 1f - restoreAlpha }
                                )
                            }
                        }
                        if (restoreMenuExpanded) {
                            Popup(
                                alignment = Alignment.BottomCenter, // 锚定在按钮上方展开
                                onDismissRequest = { restoreMenuExpanded = false },
                                properties = PopupProperties(focusable = true, dismissOnBackPress = true)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 8.dp,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .height(IntrinsicSize.Min)
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .clickable {
                                                    restoreMenuExpanded = false
                                                    onUndoOptimize()
                                                }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                FeatherIcons.RotateCcw,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(Spacing.xs))
                                            Text(
                                                text = stringResource(R.string.chat_optimize_restore_original),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        VerticalDivider(
                                            modifier = Modifier.fillMaxHeight(),
                                            thickness = 1.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                        Row(
                                            modifier = Modifier
                                                .clickable(enabled = value.isNotBlank()) {
                                                    restoreMenuExpanded = false
                                                    onOptimizeInput()
                                                }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(Spacing.xs))
                                            Text(
                                                text = stringResource(R.string.chat_optimize_continue),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (value.isNotBlank()) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (inputBarPrefs.showPlus) {
                        UploadIconButton(
                            enabled = true,
                            icon = FeatherIcons.Plus,
                            contentDescription = stringResource(R.string.chat_add_attachment),
                            onClick = { showAttachmentSheet = true }
                        )
                    }
                    SendButton(canSend = canSend, hasContent = hasContent, isBusy = isBusy, onSend = onSend, onStop = onStop)
                }
            }
        }
        }
    }

    if (showAttachmentSheet) {
        AttachmentSheet(
            canUploadFiles = canUploadFiles,
            canUploadImages = canUploadImages,
            onUploadFile = {
                showAttachmentSheet = false
                onUploadFile()
            },
            onUploadApp = {
                showAttachmentSheet = false
                onUploadApp()
            },
            onUploadImage = {
                showAttachmentSheet = false
                onUploadImage()
            },
            onTakePhoto = {
                showAttachmentSheet = false
                onTakePhoto()
            },
            onDismiss = { showAttachmentSheet = false }
        )
    }

    if (showFullScreenInput) {
        FullScreenInputDialog(
            value = value,
            onValueChange = onValueChange,
            placeholder = stringResource(if (isBusy) R.string.chat_queue_hint else R.string.chat_input_placeholder),
            onDismiss = { showFullScreenInput = false }
        )
    }
}

/**
 * 全屏输入框：底部输入框被高度上限截断、框内滚动时，点右上角「展开」进入这里，
 * 用整屏编辑同一份草稿（内容实时回写，收起后输入框里就是最新文本）。
 */
@Composable
private fun FullScreenInputDialog(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            // 不设则 dialog window 是 WRAP_CONTENT，内容铺不满屏
            usePlatformDefaultWidth = false,
            // 不设则内容被系统栏 inset 挤进安全区，底色在状态栏处断开、露出后面的聊天界面
            decorFitsSystemWindows = false
        )
    ) {
        // dialog 有自己的 window：系统栏图标色要单独按当前主题设置，否则浅色主题下状态栏图标压在浅底上看不见。
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        val lightBackground = MaterialTheme.colorScheme.background.luminance() > 0.5f
        SideEffect {
            dialogWindow?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = lightBackground
                    isAppearanceLightNavigationBars = lightBackground
                }
            }
        }

        val imeInset = rememberImeBottomInset()
        val focusRequester = remember { FocusRequester() }
        // 打开即聚焦，键盘不停，可以接着往下写。
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(bottom = imeInset)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.chat_input_fullscreen_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            FeatherIcons.Minimize,
                            contentDescription = stringResource(R.string.common_collapse_action),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            placeholder,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}

@Composable
internal fun UploadIconButton(
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun SendButton(
    canSend: Boolean,
    hasContent: Boolean,
    isBusy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val densityTokens = LocalUIDensityTokens.current
    // AI 工作中：输入框为空显示停止按钮；有内容显示橙色发送（消息排队发送）；空闲时按常规发送按钮。
    // 可发送态用主题 primary 而非固定色，自定义主题包下随之推导变化；前景色按态取语义 on* 色。
    val showStop = isBusy && !hasContent
    val clickable = showStop || canSend
    val (buttonColor, iconTint) = when {
        showStop -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        isBusy -> Brand.Orange to MaterialTheme.semanticColors.onWarning
        canSend -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(densityTokens.scale(36))
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(densityTokens.scale(36))
                .clip(CircleShape)
                .background(buttonColor)
                .clickable(enabled = clickable, onClick = if (showStop) onStop else onSend),
            contentAlignment = Alignment.Center
        ) {
            if (showStop) {
                Icon(
                    FeatherIcons.Square,
                    contentDescription = stringResource(R.string.chat_stop),
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    FeatherIcons.ArrowUp,
                    contentDescription = stringResource(R.string.chat_send),
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
internal fun ToolPermissionPanel(
    request: PendingToolPermission,
    onChoice: (PermissionChoice) -> Unit,
    modifier: Modifier = Modifier,
    sessionTitle: String = "",
    forceCollapse: Boolean = false,
    isScrolling: Boolean = false
) {
    var expanded by remember { mutableStateOf(true) }
    // 余额面板展开时同帧收起本面板，避免叠加
    val effectiveExpanded = expanded && !forceCollapse

    // 滚动弱化：用户上下滑动列表时，悬浮授权弹窗跟随淡化为 40%，避免遮挡用户查看背后消息；停止滑动后平滑恢复
    val panelAlpha by animateFloatAsState(
        targetValue = if (isScrolling) 0.4f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "permission-panel-alpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            // 内嵌于消息列表（列表 contentPadding 已提供水平留白），此处仅保留垂直间距，
            // 避免与列表 padding 叠加导致面板比消息气泡/输入框窄。
            .padding(vertical = Spacing.xs)
            .graphicsLayer { alpha = panelAlpha },
        color = chatPanelSurface(),
        shape = RoundedCornerShape(Radius.md),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = FeatherIcons.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Text(
                            text = request.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = request.toolName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (request.origin == PendingToolPermission.ORIGIN_PLUGIN_PERMISSION) {
                        Text(
                            text = stringResource(R.string.chat_perm_plugin_source, request.pluginName.orEmpty(), request.pluginId.orEmpty()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val permissionSessionTitle = sessionTitle.ifBlank {
                        if (request.origin == PendingToolPermission.ORIGIN_PLUGIN_PERMISSION) request.sessionId else ""
                    }
                    if (permissionSessionTitle.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.chat_perm_session_label, permissionSessionTitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(28.dp)
                ) {
                    ExpandableChevronIcon(
                        expanded = effectiveExpanded,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 18.dp
                    )
                }
            }

            AnimatedVisibility(
                visible = effectiveExpanded,
                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180))
            ) {
                Column {
                    Spacer(Modifier.height(Spacing.sm))
                    Surface(
                        shape = RoundedCornerShape(Radius.sm),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                                    .heightIn(max = 160.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = request.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (request.details.isNotBlank()) {
                                    Spacer(Modifier.height(Spacing.xs))
                                    Text(
                                        text = request.details,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    val canRemember = request.rememberablePatterns.isNotEmpty()
                    val rememberLabel = when {
                        !canRemember -> request.rememberDisabledReason ?: stringResource(R.string.chat_perm_single_use_desc)
                        request.rememberablePatterns == listOf("*") -> stringResource(R.string.chat_perm_always_tool_desc)
                        else -> stringResource(R.string.chat_perm_always_prefix) + request.rememberablePatterns.joinToString("、")
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = rememberLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(Spacing.sm))
                    // 左对齐胶囊按钮行：不再各 weight(1f) 平分整行，改为按内容自适应宽度，
                    // 避免三个等宽大按钮占满整行、按钮偏大。
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.Start)) {
                        AgentActionButton(
                            text = stringResource(R.string.chat_perm_deny),
                            onClick = { onChoice(PermissionChoice.REJECT) },
                            tone = AgentActionTone.Danger,
                            pill = true
                        )
                        // 插件主动授权请求不产生永久授权：仅插件来源隐藏「始终允许」。
                        // 常规工具沿用 pill 行（本地已重构为内容自适应，与上游 weight 行不同）。
                        if (request.origin != PendingToolPermission.ORIGIN_PLUGIN_PERMISSION) AgentActionButton(
                            text = stringResource(R.string.chat_perm_always_allow),
                            onClick = { onChoice(PermissionChoice.ALWAYS) },
                            enabled = canRemember,
                            tone = AgentActionTone.Neutral,
                            pill = true
                        )
                        AgentActionButton(
                            text = stringResource(R.string.common_allow),
                            onClick = { onChoice(PermissionChoice.ONCE) },
                            tone = AgentActionTone.Success,
                            pill = true
                        )
                    }
                }
            }
        }
    }
}

/**
 * 失败错误气泡：风格对齐 ToolPermissionPanel / AskUserQuestionPanel（内联面板，与消息同宽）。
 * 默认折叠为一行标题（请求失败），点击展开完整错误详情；右上角可一键复制、可关闭。
 * Issue #55：作为失败回复下方的一条消息随消息流滚动，而非常驻输入框上方；折叠态收紧留白。
 */
@Composable
internal fun ErrorBubble(
    message: String,
    onDismiss: (() -> Unit)? = null
) {
    var expanded by remember(message) { mutableStateOf(false) }
    var copied by remember(message) { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val copyScope = rememberCoroutineScope()
    val t = LocalUIDensityTokens.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        color = chatPanelGlassOr(MaterialTheme.semanticColors.surfaceGlass),
        shape = RoundedCornerShape(Radius.md),
        border = BorderStroke(t.scale(1), glassBorderOr(MaterialTheme.semanticColors.surfaceGlassBorder))
    ) {
        // 折叠态收紧留白：上下内边距用 sm 而非 md，行内按钮收紧到 30dp，整卡高度贴近单行文字。
        Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    FeatherIcons.AlertCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(t.scale(16))
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = stringResource(R.string.chat_error_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                ExpandableChevronIcon(
                    expanded = expanded,
                    contentDescription = if (expanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
                    tint = Brand.IconGray,
                    size = 18.dp
                )
                Spacer(Modifier.width(Spacing.md))
                IconButton(
                    onClick = {
                        copyScope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("error", message)))
                            copied = true
                        }
                    },
                    modifier = Modifier.size(t.scale(30))
                ) {
                    Icon(
                        if (copied) FeatherIcons.Check else FeatherIcons.Copy,
                        contentDescription = if (copied) stringResource(R.string.chat_copied) else stringResource(R.string.chat_copy),
                        tint = if (copied) MaterialTheme.colorScheme.primary else Brand.IconGray,
                        modifier = Modifier.size(t.scale(16))
                    )
                }
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(t.scale(30))
                    ) {
                        Icon(
                            FeatherIcons.X,
                            contentDescription = stringResource(R.string.common_close),
                            tint = Brand.IconGray,
                            modifier = Modifier.size(t.scale(16))
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180))
            ) {
                Column {
                    Spacer(Modifier.height(Spacing.sm))
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .heightIn(max = t.scale(160))
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 计划审查面板：AI 从 PLAN 模式切回 BUILD 时弹出，展示计划摘要供用户批准或继续反馈。
 * 风格与 ToolPermissionPanel / AskUserQuestionPanel 一致。
 */
@Composable
internal fun PlanApprovalPanel(
    state: PlanApprovalRequest,
    onApprove: () -> Unit,
    onRefine: () -> Unit,
    forceCollapse: Boolean = false,
    sessionTitle: String = ""
) {
    var expanded by remember { mutableStateOf(true) }
    // 余额面板展开时同帧收起本面板，避免叠加顶开输入框
    val effectiveExpanded = expanded && !forceCollapse

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        color = chatPanelSurface(),
        shape = RoundedCornerShape(Radius.md),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = stringResource(R.string.chat_plan_completed),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                ExpandableChevronIcon(
                    expanded = effectiveExpanded,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 18.dp
                )
            }

            AnimatedVisibility(
                visible = effectiveExpanded,
                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180))
            ) {
                Column {
                    // 多会话并行时标注这份计划属于哪个会话，避免用户批错对象
                    if (sessionTitle.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.chat_perm_session_label, sessionTitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(Spacing.xs))
                    }
                    if (state.reason.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.xs))
                        SelectionContainer {
                            Column(
                                modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = state.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(Spacing.md))
                    // 左对齐胶囊按钮行：去掉 weight(1f) 平分整行，改为按内容自适应宽度，与权限/提问面板一致。
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.Start)) {
                        AgentActionButton(
                            text = stringResource(R.string.chat_continue_feedback),
                            onClick = onRefine,
                            tone = AgentActionTone.Neutral,
                            pill = true
                        )
                        AgentActionButton(
                            text = stringResource(R.string.chat_approve_and_implement),
                            onClick = onApprove,
                            tone = AgentActionTone.Success,
                            pill = true
                        )
                    }
                }
            }
        }
    }
}

/**
 * 会话持续目标（goal）的常驻卡片：置于输入框上方，显示目标文本、状态与暂停/恢复、清除操作。
 * 样式与输入框卡片一致（圆角 + 描边）。
 */
@Composable
private fun GoalBannerRow(
    goal: SessionGoalUi,
    onSetPaused: (Boolean) -> Unit,
    onClear: () -> Unit
) {
    val statusLabel = when (goal.status) {
        GoalStatus.ACTIVE -> stringResource(R.string.chat_goal_status_active)
        GoalStatus.PAUSED -> stringResource(R.string.chat_goal_status_paused)
        GoalStatus.COMPLETED -> stringResource(R.string.chat_goal_status_completed)
        GoalStatus.BLOCKED -> stringResource(R.string.chat_goal_status_blocked)
        GoalStatus.EXHAUSTED -> stringResource(R.string.chat_goal_status_exhausted)
    }
    val accent = when (goal.status) {
        GoalStatus.ACTIVE -> MaterialTheme.colorScheme.primary
        GoalStatus.COMPLETED -> MaterialTheme.semanticColors.success
        GoalStatus.BLOCKED, GoalStatus.EXHAUSTED -> MaterialTheme.colorScheme.error
        GoalStatus.PAUSED -> MaterialTheme.semanticColors.subtleText
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.sm),
        shape = RoundedCornerShape(Radius.lg),
        color = chatPanelSurface(),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.sm, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = FeatherIcons.Target,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.chat_goal_label, statusLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1
                )
                Text(
                    text = goal.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (goal.status == GoalStatus.ACTIVE || goal.status == GoalStatus.PAUSED) {
                IconButton(
                    onClick = { onSetPaused(goal.status == GoalStatus.ACTIVE) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (goal.status == GoalStatus.ACTIVE) FeatherIcons.Pause else FeatherIcons.Play,
                        contentDescription = stringResource(
                            if (goal.status == GoalStatus.ACTIVE) R.string.chat_goal_pause else R.string.chat_goal_resume
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            IconButton(onClick = onClear, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = FeatherIcons.X,
                    contentDescription = stringResource(R.string.chat_goal_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}