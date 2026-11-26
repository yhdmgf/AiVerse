package com.aiverse.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import com.aiverse.R
import com.aiverse.core.theme.AppThemePreset
import com.aiverse.core.theme.ChatBubbleStyle
import com.aiverse.core.theme.FileNameDisplay
import com.aiverse.core.theme.FileTreeDisplayPrefs
import com.aiverse.core.theme.FileTreeIndentDensity
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.UIDensity
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.ui.SegmentedTabs
import com.aiverse.core.theme.isDynamicColorSupported
import com.aiverse.core.theme.semanticColors
import com.aiverse.core.ui.AppSwitch
import com.aiverse.feature.settings.data.repository.InputBarPrefs
import com.aiverse.feature.settings.data.repository.AppThemeMode
import com.aiverse.feature.settings.data.repository.DEFAULT_QUICK_ACTION_ORDER
import com.aiverse.feature.settings.data.repository.InputBarQuickAction
import com.aiverse.feature.settings.data.repository.BackgroundMode
import com.aiverse.feature.settings.data.repository.BackgroundSettingsRepository
import compose.icons.FeatherIcons
import compose.icons.feathericons.Box
import compose.icons.feathericons.Code
import compose.icons.feathericons.Cpu
import compose.icons.feathericons.Download
import compose.icons.feathericons.Eye
import compose.icons.feathericons.Folder
import compose.icons.feathericons.Globe
import compose.icons.feathericons.Image
import compose.icons.feathericons.MessageSquare
import compose.icons.feathericons.Minimize
import compose.icons.feathericons.Moon
import compose.icons.feathericons.RotateCcw
import compose.icons.feathericons.Share
import compose.icons.feathericons.Sliders
import compose.icons.feathericons.Type
import compose.icons.feathericons.Zap
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Star
import kotlin.math.roundToInt
import java.io.File

/** 背景模式 -> 标签文案资源（设置项尾部与弹窗共用）。 */
internal fun BackgroundMode.labelRes(): Int = when (this) {
    BackgroundMode.COVER_CROP -> R.string.settings_background_mode_cover_crop
    BackgroundMode.COVER_FIT -> R.string.settings_background_mode_cover_fit
    BackgroundMode.PORTRAIT -> R.string.settings_background_mode_portrait
}

/**
 * 外观设置二级页：把原来平铺在通用设置里的「主题 / 聊天气泡 / 背景图」收进来，
 * 并新增三档字体大小（聊天、输入框、界面缩放），均实时持久化。
 */
@Composable
internal fun AppearanceSettingsSection(
    themeMode: AppThemeMode,
    themePresetId: String?,
    dynamicColorEnabled: Boolean,
    userBubbleStyle: ChatBubbleStyle,
    assistantBubbleStyle: ChatBubbleStyle,
    backgroundImagePath: String?,
    backgroundMask: Float,
    backgroundMode: BackgroundMode,
    chatFontSizeSp: Int,
    inputFontSizeSp: Int,
    uiFontScale: Float,
    uiDensity: UIDensity,
    uiFontPath: String,
    fileTreePrefs: FileTreeDisplayPrefs,
    onSetFileNameDisplay: (FileNameDisplay) -> Unit,
    onSetShowHiddenFiles: (Boolean) -> Unit,
    onSetFileIndentDensity: (FileTreeIndentDensity) -> Unit,
    onSetFileMutedColoring: (Boolean) -> Unit,
    onOpenThemeSheet: () -> Unit,
    onOpenBackgroundSheet: () -> Unit,
    onOpenUserBubbleStyleSheet: () -> Unit,
    onOpenAssistantBubbleStyleSheet: () -> Unit,
    onSetChatFontSizeSp: (Int) -> Unit,
    onSetInputFontSizeSp: (Int) -> Unit,
    onSetUiFontScale: (Float) -> Unit,
    onSetUiDensity: (UIDensity) -> Unit,
    onOpenDensity: () -> Unit,
    onSetCornerStyle: (Int) -> Unit,
    onOpenUiFont: () -> Unit,
    onRestoreDefaults: () -> Unit,
    onExportRequest: () -> Unit,
    onImportThemePackage: (String) -> Unit,
    exportedThemeJson: String?,
    inputBarPrefs: InputBarPrefs,
    onInputBarPrefsChange: (InputBarPrefs) -> Unit,
    codeFontSizeSp: Int,
    codeWrapEnabled: Boolean,
    codeAutoFoldEnabled: Boolean,
    codeShowLineNumber: Boolean,
    cornerStyleIndex: Int,
    onSetCodeFontSizeSp: (Int) -> Unit,
    onSetCodeWrapEnabled: (Boolean) -> Unit,
    onSetCodeAutoFoldEnabled: (Boolean) -> Unit,
    onSetCodeShowLineNumber: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // 导出：拿到 JSON 后弹分享；没有能接手的应用时回退到复制剪贴板。
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    // 导入：从文件选择器读 JSON 文本后交给上层套用。
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            Toast.makeText(
                context,
                context.getString(R.string.settings_appearance_theme_package_parse_failed),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            onImportThemePackage(text)
        }
    }

    // 监听导出结果：exportedThemeJson 仅在导出成功且非空时被上层更新（失败由上层 Toast）。
    // 拿到非空 JSON 后弹系统分享；没有任何应用接手时回退到复制剪贴板。
    LaunchedEffect(exportedThemeJson) {
        val json = exportedThemeJson ?: return@LaunchedEffect
        val clipLabel = context.getString(R.string.settings_appearance_theme_package)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, json)
        }
        val share = Intent.createChooser(send, clipLabel)
        val launchedOk = runCatching { exportLauncher.launch(share) }.isSuccess
        if (!launchedOk) {
            clipboard.setText(AnnotatedString(json))
            Toast.makeText(
                context,
                context.getString(R.string.settings_appearance_theme_package_copied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val t = LocalUIDensityTokens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(t.sm)
    ) {
        SettingsGroupHeader(text = stringResource(R.string.settings_appearance_general))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Moon,
                title = stringResource(R.string.settings_theme_title),
                onClick = onOpenThemeSheet,
                trailing = {
                    val colorLabel = if (dynamicColorEnabled && isDynamicColorSupported) {
                        stringResource(R.string.theme_dynamic_color)
                    } else {
                        stringResource(AppThemePreset.findById(themePresetId).nameRes)
                    }
                    Text(
                        text = "${stringResource(themeMode.labelRes)} · $colorLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.semanticColors.subtleText
                    )
                }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.MessageSquare,
                title = stringResource(R.string.bubble_user),
                subtitle = stringResource(userBubbleStyle.labelRes),
                onClick = onOpenUserBubbleStyleSheet
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Image,
                title = stringResource(R.string.settings_background_image),
                onClick = onOpenBackgroundSheet,
                trailing = {
                    Text(
                        text = if (backgroundImagePath != null) {
                            "${stringResource(backgroundMode.labelRes())} · " +
                                "${BackgroundSettingsRepository.maskToSlider(backgroundMask).toInt()}%"
                        } else {
                            stringResource(R.string.settings_background_image_none)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.semanticColors.subtleText
                    )
                }
            )
        }

        SettingsGroupHeader(text = stringResource(R.string.settings_appearance_fonts))
        SettingsGroup {
            FontSizeSliderRow(
                title = stringResource(R.string.settings_appearance_chat_font),
                value = chatFontSizeSp,
                min = AppearanceFontRanges.CHAT_MIN,
                max = AppearanceFontRanges.CHAT_MAX,
                onSet = onSetChatFontSizeSp
            )
            SettingsDivider()
            FontSizeSliderRow(
                title = stringResource(R.string.settings_appearance_input_font),
                value = inputFontSizeSp,
                min = AppearanceFontRanges.INPUT_MIN,
                max = AppearanceFontRanges.INPUT_MAX,
                onSet = onSetInputFontSizeSp
            )
            SettingsDivider()
            FontSizeSliderRow(
                title = stringResource(R.string.settings_appearance_code_font),
                value = codeFontSizeSp,
                min = AppearanceFontRanges.CODE_MIN,
                max = AppearanceFontRanges.CODE_MAX,
                onSet = onSetCodeFontSizeSp
            )
            SettingsDivider()
            ScaleSliderRow(
                title = stringResource(R.string.settings_appearance_ui_scale),
                value = uiFontScale,
                onSet = onSetUiFontScale
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Sliders,
                title = stringResource(R.string.settings_appearance_density),
                onClick = onOpenDensity,
                trailing = {
                    Text(
                        text = stringResource(
                            when (uiDensity) {
                                UIDensity.COMPACT -> R.string.density_compact
                                UIDensity.STANDARD -> R.string.density_standard
                                UIDensity.COMFORTABLE -> R.string.density_comfortable
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.semanticColors.subtleText
                    )
                }
            )
            SettingsDivider()
            CornerStyleRow(
                selected = cornerStyleIndex,
                onSelect = onSetCornerStyle
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Type,
                title = stringResource(R.string.settings_appearance_ui_font),
                onClick = onOpenUiFont,
                trailing = {
                    Text(
                        text = if (uiFontPath.isBlank()) {
                            stringResource(R.string.settings_appearance_ui_font_default)
                        } else {
                            File(uiFontPath).nameWithoutExtension
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.semanticColors.subtleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }

        SettingsGroupHeader(text = stringResource(R.string.settings_appearance_code_display))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Code,
                title = stringResource(R.string.settings_appearance_code_wrap),
                trailing = { AppSwitch(checked = codeWrapEnabled, onCheckedChange = onSetCodeWrapEnabled) }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Minimize,
                title = stringResource(R.string.settings_appearance_code_auto_fold),
                trailing = { AppSwitch(checked = codeAutoFoldEnabled, onCheckedChange = onSetCodeAutoFoldEnabled) }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Eye,
                title = stringResource(R.string.settings_appearance_code_line_number),
                trailing = { AppSwitch(checked = codeShowLineNumber, onCheckedChange = onSetCodeShowLineNumber) }
            )
        }

        SettingsGroupHeader(text = stringResource(R.string.settings_appearance_files))
        SettingsGroup {
            FileNameDisplayRow(
                selected = fileTreePrefs.fileNameDisplay,
                onSelect = onSetFileNameDisplay
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Eye,
                title = stringResource(R.string.file_tree_show_hidden),
                trailing = { AppSwitch(checked = fileTreePrefs.showHiddenFiles, onCheckedChange = onSetShowHiddenFiles) }
            )
            SettingsDivider()
            FileIndentDensityRow(
                selected = fileTreePrefs.indentDensity,
                onSelect = onSetFileIndentDensity
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Moon,
                title = stringResource(R.string.file_tree_muted_coloring),
                trailing = { AppSwitch(checked = fileTreePrefs.mutedColoring, onCheckedChange = onSetFileMutedColoring) }
            )
        }

        SettingsGroupHeader(text = stringResource(R.string.settings_appearance_input_bar))
        SettingsGroup {
            // 顺序与输入框快捷栏一致（共用 DEFAULT_QUICK_ACTION_ORDER，按使用频率）。
            DEFAULT_QUICK_ACTION_ORDER.forEachIndexed { index, action ->
                val spec = inputBarActionRowSpec(action, inputBarPrefs, onInputBarPrefsChange)
                SettingsRow(
                    icon = spec.icon,
                    title = stringResource(spec.titleRes),
                    trailing = { AppSwitch(checked = spec.checked, onCheckedChange = spec.onChecked) }
                )
                if (index < DEFAULT_QUICK_ACTION_ORDER.lastIndex) SettingsDivider()
            }
        }

        SettingsGroupHeader(text = stringResource(R.string.settings_appearance_theme_package))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Share,
                title = stringResource(R.string.settings_appearance_theme_package_export),
                subtitle = stringResource(R.string.settings_appearance_theme_package_export_desc),
                onClick = { onExportRequest() }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Download,
                title = stringResource(R.string.settings_appearance_theme_package_import),
                subtitle = stringResource(R.string.settings_appearance_theme_package_import_desc),
                onClick = { importLauncher.launch("*/*") }
            )
        }

        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.RotateCcw,
                title = stringResource(R.string.settings_appearance_reset),
                onClick = onRestoreDefaults
            )
        }
    }
}

private object AppearanceFontRanges {
    const val CHAT_MIN = 12
    const val CHAT_MAX = 22
    const val INPUT_MIN = 12
    const val INPUT_MAX = 22
    const val CODE_MIN = 10
    const val CODE_MAX = 22
}

/** 聊天 / 输入框字号滑块行：拖动实时预览，松手才落盘。 */
@Composable
private fun FontSizeSliderRow(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    onSet: (Int) -> Unit
) {
    var local by remember(value) { mutableStateOf(value) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${local}sp",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.semanticColors.subtleText
            )
        }
        Slider(
            value = local.toFloat(),
            onValueChange = { local = it.roundToInt() },
            onValueChangeFinished = { onSet(local) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min - 1)
        )
    }
}

/** 文件树文件名显示三档选择行：单行横向滚动 / 最多两行 / 单行省略号，实时持久化。 */
@Composable
private fun FileNameDisplayRow(
    selected: FileNameDisplay,
    onSelect: (FileNameDisplay) -> Unit
) {
    val options = FileNameDisplay.entries
    val labels = listOf(
        stringResource(R.string.file_name_display_scroll),
        stringResource(R.string.file_name_display_two_lines),
        stringResource(R.string.file_name_display_ellipsis)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Text(
            text = stringResource(R.string.file_tree_filename_display),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = Spacing.sm)
        )
        SegmentedTabs(
            selected = options.indexOf(selected).coerceAtLeast(0),
            labels = labels,
            onSelect = { index -> onSelect(options[index]) }
        )
    }
}

/** 文件树缩进密度两档选择行：紧凑 / 标准，实时持久化。 */
@Composable
private fun FileIndentDensityRow(
    selected: FileTreeIndentDensity,
    onSelect: (FileTreeIndentDensity) -> Unit
) {
    val options = FileTreeIndentDensity.entries
    val labels = listOf(
        stringResource(R.string.indent_compact),
        stringResource(R.string.indent_standard)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Text(
            text = stringResource(R.string.file_tree_indent_density),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = Spacing.sm)
        )
        SegmentedTabs(
            selected = options.indexOf(selected).coerceAtLeast(0),
            labels = labels,
            onSelect = { index -> onSelect(options[index]) }
        )
    }
}

/** 界面缩放滑块行（0.85~1.30），拖动实时预览，松手才落盘。 */
@Composable
private fun ScaleSliderRow(
    title: String,
    value: Float,
    onSet: (Float) -> Unit
) {
    var local by remember(value) { mutableStateOf(value) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(local * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.semanticColors.subtleText
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onSet(local) },
            valueRange = 0.85f..1.30f
        )
    }
}

/**
 * 圆角风格三档选择行：紧凑 / 标准 / 舒适，一键套用到卡片、按钮、输入框、气泡、图片。
 * 分项圆角滑块已折叠为基础档；更细的分项圆角交给外观插件深度自定义。
 */
@Composable
private fun CornerStyleRow(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    val labels = listOf(
        stringResource(R.string.corner_style_compact),
        stringResource(R.string.corner_style_standard),
        stringResource(R.string.corner_style_comfortable)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Text(
            text = stringResource(R.string.settings_appearance_corner),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = Spacing.xs)
        )
        Text(
            text = stringResource(R.string.settings_appearance_corner_plugin_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.semanticColors.subtleText,
            modifier = Modifier.padding(bottom = Spacing.sm)
        )
        SegmentedTabs(
            selected = selected.coerceIn(0, labels.lastIndex),
            labels = labels,
            onSelect = onSelect
        )
    }
}

/** 设置页快捷栏开关行的展示规格（图标/标题/开关状态/回调）。 */
private class InputBarRowSpec(
    val icon: ImageVector,
    val titleRes: Int,
    val checked: Boolean,
    val onChecked: (Boolean) -> Unit
)

/** 快捷栏开关行按 [InputBarQuickAction] 映射：与输入框快捷栏共用同一默认顺序。 */
private fun inputBarActionRowSpec(
    action: InputBarQuickAction,
    prefs: InputBarPrefs,
    onChange: (InputBarPrefs) -> Unit
): InputBarRowSpec = when (action) {
    InputBarQuickAction.MODEL ->
        InputBarRowSpec(FeatherIcons.Cpu, R.string.settings_inputbar_model, prefs.showModel) { onChange(prefs.copy(showModel = it)) }
    InputBarQuickAction.THINKING ->
        InputBarRowSpec(FeatherIcons.Zap, R.string.settings_inputbar_thinking, prefs.showThinking) { onChange(prefs.copy(showThinking = it)) }
    InputBarQuickAction.WEB_SEARCH ->
        InputBarRowSpec(FeatherIcons.Globe, R.string.settings_inputbar_web_search, prefs.showWebSearch) { onChange(prefs.copy(showWebSearch = it)) }
    InputBarQuickAction.FOLDER ->
        InputBarRowSpec(FeatherIcons.Folder, R.string.settings_inputbar_folder, prefs.showFolder) { onChange(prefs.copy(showFolder = it)) }
    InputBarQuickAction.MCP ->
        InputBarRowSpec(FeatherIcons.Box, R.string.settings_inputbar_mcp, prefs.showMcp) { onChange(prefs.copy(showMcp = it)) }
    InputBarQuickAction.OPTIMIZE ->
        InputBarRowSpec(FeatherIcons.Star, R.string.settings_inputbar_optimize, prefs.showOptimize) { onChange(prefs.copy(showOptimize = it)) }
    InputBarQuickAction.PLUS ->
        InputBarRowSpec(FeatherIcons.Plus, R.string.settings_inputbar_plus, prefs.showPlus) { onChange(prefs.copy(showPlus = it)) }
}
