package com.aiverse.feature.settings.presentation.component

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import com.aiverse.core.ui.AppTextField
import com.aiverse.core.ui.ImagePickerField
import com.aiverse.core.ui.RoleAvatar
import com.aiverse.core.util.truncateToUtf8Bytes
import com.aiverse.feature.agent.domain.model.AvatarKind
import com.aiverse.feature.agent.domain.model.Role
import com.aiverse.feature.agent.domain.prompt.PromptFragment
import com.aiverse.feature.settings.presentation.RoleEditorViewModel
import com.aiverse.feature.settings.presentation.RoleSaveState
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.ChevronDown
import compose.icons.feathericons.UploadCloud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 角色新建/编辑页：自带顶栏（返回 + 右上角保存），与 [PromptEditorScreen] 一致，不能嵌进设置页的 Scaffold。
 *
 * 名称按 UTF-8 字节数限制（[Role.NAME_MAX_BYTES]，约中文 8 字 / 英文 24 字），顶栏胶囊自带省略；
 * 身份 Prompt 可导入 .md/.txt（正文写入身份，前导 `<!-- 描述 -->` 提取为简介）；头像分
 * Emoji / 图片 / 无三种，Emoji 存字符、图片可手填 URL/路径或从相册选择（存本地路径）。
 * 保存走 [RoleEditorViewModel] 落库。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoleEditorScreen(
    initial: Role?,
    viewModel: RoleEditorViewModel,
    onSaved: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    // 新建时生成一次稳定 id（编辑保留原 id），保存成功后据此定位。
    val editingId = rememberSaveable { initial?.id ?: RoleEditorViewModel.newRoleId() }
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var description by rememberSaveable { mutableStateOf(initial?.description ?: "") }
    var identityPrompt by rememberSaveable { mutableStateOf(initial?.identityPrompt ?: "") }
    var tagsText by rememberSaveable { mutableStateOf(initial?.tags?.joinToString(", ") ?: "") }
    var avatarKindName by rememberSaveable { mutableStateOf(initial?.avatarKind?.name ?: AvatarKind.NONE.name) }
    var avatarValue by rememberSaveable { mutableStateOf(initial?.avatarValue ?: "") }
    var selectedWorkspaceId by rememberSaveable { mutableStateOf(initial?.workspaceId ?: "") }
    var workspaceDropdownExpanded by rememberSaveable { mutableStateOf(false) }

    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()

    // 绑定的工作区可能已在工作区管理里删除：加载完列表后若找不到对应项，回退为「不绑定」，
    // 避免下拉框继续显示失效名称、保存时又把失效绑带回去（issue #300）。
    LaunchedEffect(workspaces) {
        if (workspaces.isNotEmpty() && selectedWorkspaceId.isNotBlank() &&
            workspaces.none { it.name == selectedWorkspaceId }
        ) {
            selectedWorkspaceId = ""
        }
    }

    val avatarKind = runCatching { AvatarKind.valueOf(avatarKindName) }.getOrDefault(AvatarKind.NONE)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 从 md/txt 文件导入：正文写入身份，前导描述注释提取为简介，标题默认用文件名（去扩展名）。
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = context.contentResolver
                    val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    val fileName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    text to fileName
                }
            }.getOrNull()
            val text = imported?.first
            val fileName = imported?.second
            if (text.isNullOrBlank()) {
                Toast.makeText(context, R.string.roles_import_failed, Toast.LENGTH_SHORT).show()
            } else {
                if (name.isBlank()) {
                    name = (fileName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotBlank() })
                        ?.truncateToUtf8Bytes(Role.NAME_MAX_BYTES) ?: name
                }
                if (description.isBlank()) {
                    description = PromptFragment.extractDescription(text)
                }
                identityPrompt = PromptFragment.bodyOf(text)
            }
        }
    }

    val canSave = name.isNotBlank()
    val saving = saveState is RoleSaveState.Saving

    // 保存成功：回调导航；失败：清态允许重试并提示。
    LaunchedEffect(saveState) {
        when (saveState) {
            is RoleSaveState.Saved -> onSaved(editingId)
            is RoleSaveState.Failed -> {
                Toast.makeText(context, R.string.roles_save_failed, Toast.LENGTH_SHORT).show()
                viewModel.clearSaveState()
            }
            else -> Unit
        }
    }

    BackHandler { onNavigateBack() }

    Scaffold(
        containerColor = settingsPageBackground(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (initial == null) R.string.roles_editor_new else R.string.roles_editor_edit
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = settingsPageBackground(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                        TextButton(
                        enabled = canSave && !saving,
                        onClick = {
                            viewModel.save(
                                existing = initial,
                                draftId = editingId,
                                name = name.trim(),
                                description = description.trim(),
                                identityPrompt = identityPrompt,
                                tags = tagsText.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                                avatarKind = avatarKind,
                                avatarValue = avatarValue.trim(),
                                workspaceId = selectedWorkspaceId.takeIf { it.isNotBlank() }
                            )
                        }
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            )
        }
    ) { padding ->
        val t = LocalUIDensityTokens.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = t.lg)
                .padding(bottom = t.xl),
            verticalArrangement = Arrangement.spacedBy(t.sm)
        ) {
            SettingsGroupHeader(text = stringResource(R.string.roles_field_basic))
            SettingsGroup {
                Column(
                    modifier = Modifier.padding(horizontal = t.lg, vertical = t.md),
                    verticalArrangement = Arrangement.spacedBy(t.md)
                ) {
                    AppTextField(
                        value = name,
                        onValueChange = { name = it.truncateToUtf8Bytes(Role.NAME_MAX_BYTES) },
                        label = stringResource(R.string.roles_field_name),
                        placeholder = stringResource(R.string.roles_field_name_placeholder),
                        singleLine = true
                    )
                    AppTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = stringResource(R.string.roles_field_description),
                        placeholder = stringResource(R.string.roles_field_description_placeholder),
                        singleLine = true
                    )
                }
            }

            if (avatarKind != AvatarKind.NONE) {
                RoleAvatarPreviewCard(
                    avatarKind = avatarKind,
                    avatarValue = avatarValue,
                    name = name
                )
            }

            SettingsGroupHeader(text = stringResource(R.string.roles_field_identity))
            SettingsGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = t.lg, end = t.lg, top = t.sm),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            importLauncher.launch(arrayOf("text/markdown", "text/plain", "application/octet-stream"))
                        }
                    ) {
                        Icon(
                            imageVector = FeatherIcons.UploadCloud,
                            contentDescription = null,
                            modifier = Modifier.size(t.scale(18))
                        )
                        Spacer(modifier = Modifier.width(t.xs))
                        Text(stringResource(R.string.roles_import))
                    }
                }
                AppTextField(
                    value = identityPrompt,
                    onValueChange = { identityPrompt = it },
                    placeholder = stringResource(R.string.roles_field_identity_placeholder),
                    singleLine = false,
                    minLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = t.lg, vertical = t.md)
                )
            }

            SettingsGroupHeader(text = stringResource(R.string.roles_field_tags))
            SettingsGroup {
                AppTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    placeholder = stringResource(R.string.roles_field_tags_placeholder),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = t.lg, vertical = t.md)
                )
            }

            SettingsGroupHeader(text = stringResource(R.string.roles_field_avatar))
            SettingsGroup {
                Column(
                    modifier = Modifier.padding(horizontal = t.lg, vertical = t.md),
                    verticalArrangement = Arrangement.spacedBy(t.md)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(t.sm)
                    ) {
                        AvatarKindChip(
                            label = stringResource(R.string.roles_avatar_none),
                            selected = avatarKind == AvatarKind.NONE,
                            onClick = { avatarKindName = AvatarKind.NONE.name }
                        )
                        AvatarKindChip(
                            label = stringResource(R.string.roles_avatar_emoji),
                            selected = avatarKind == AvatarKind.EMOJI,
                            onClick = { avatarKindName = AvatarKind.EMOJI.name }
                        )
                        AvatarKindChip(
                            label = stringResource(R.string.roles_avatar_image),
                            selected = avatarKind == AvatarKind.IMAGE,
                            onClick = { avatarKindName = AvatarKind.IMAGE.name }
                        )
                    }
                    if (avatarKind == AvatarKind.EMOJI) {
                        AppTextField(
                            value = avatarValue,
                            onValueChange = { avatarValue = it },
                            label = stringResource(R.string.roles_field_avatar_value),
                            placeholder = stringResource(R.string.roles_field_avatar_emoji_placeholder),
                            singleLine = true
                        )
                    } else if (avatarKind == AvatarKind.IMAGE) {
                        ImagePickerField(
                            path = avatarValue,
                            onPathChange = { avatarValue = it },
                            onSaveImage = { uri, onResult -> viewModel.saveAvatarImage(uri, onResult) },
                            saveErrorRes = R.string.common_image_picker_save_failed
                        )
                    }
                }
            }

            SettingsGroupHeader(text = stringResource(R.string.roles_field_workspace))
            SettingsGroup {
                Column(
                    modifier = Modifier.padding(horizontal = t.lg, vertical = t.md),
                    verticalArrangement = Arrangement.spacedBy(t.sm)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { workspaceDropdownExpanded = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val selectedWs = workspaces.find { it.name == selectedWorkspaceId }
                        Text(
                            text = selectedWs?.displayLabel
                                ?: selectedWorkspaceId.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.roles_field_workspace_none),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selectedWorkspaceId.isBlank())
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = FeatherIcons.ChevronDown,
                            contentDescription = stringResource(R.string.roles_field_workspace_placeholder),
                            modifier = Modifier.size(t.scale(18))
                        )
                    }
                    DropdownMenu(
                        expanded = workspaceDropdownExpanded,
                        onDismissRequest = { workspaceDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.roles_field_workspace_none)) },
                            onClick = {
                                selectedWorkspaceId = ""
                                workspaceDropdownExpanded = false
                            }
                        )
                        workspaces.forEach { ws ->
                            DropdownMenuItem(
                                text = { Text(ws.displayLabel) },
                                onClick = {
                                    selectedWorkspaceId = ws.name
                                    workspaceDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 头像类型选择 chip。 */
@Composable
private fun AvatarKindChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

/**
 * 编辑页头像预览卡片：头像 + 名称（随表单实时联动），与快捷入口编辑弹层的预览卡片同一形态。
 * 预览槽复用 [RoleAvatar]，避免各页各造一份头像渲染。
 */
@Composable
private fun RoleAvatarPreviewCard(
    avatarKind: AvatarKind,
    avatarValue: String,
    name: String
) {
    val t = LocalUIDensityTokens.current
    Surface(
        shape = RoundedCornerShape(Radius.mdLarge),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoleAvatar(
                role = Role(
                    id = "",
                    name = name,
                    avatarKind = avatarKind,
                    avatarValue = avatarValue
                ),
                size = t.scale(48)
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = name.ifBlank { stringResource(R.string.roles_avatar_preview_name_placeholder) },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
