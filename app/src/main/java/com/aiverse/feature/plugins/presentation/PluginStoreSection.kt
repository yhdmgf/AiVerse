package com.aiverse.feature.plugins.presentation

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Radius
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.semanticColors
import com.aiverse.core.ui.AppTextField
import com.aiverse.core.ui.dialogTextFieldColors
import com.aiverse.core.ui.listBottomContentPadding
import com.aiverse.feature.plugins.domain.PluginManifestParser
import com.aiverse.feature.settings.presentation.component.LazyGroupItem
import com.aiverse.feature.settings.presentation.component.McpPill
import com.aiverse.feature.settings.presentation.component.ModelSearchField
import com.aiverse.feature.settings.presentation.component.SettingsDivider
import com.aiverse.feature.settings.presentation.component.SettingsEmptyState
import com.aiverse.feature.settings.presentation.component.SettingsGroup
import com.aiverse.feature.settings.presentation.component.SettingsGroupHeader
import com.aiverse.feature.settings.presentation.component.SettingsRow
import com.aiverse.feature.settings.presentation.component.openUrl
import compose.icons.FeatherIcons
import compose.icons.feathericons.Book
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.CloudOff
import compose.icons.feathericons.Github
import compose.icons.feathericons.Globe
import compose.icons.feathericons.Package
import compose.icons.feathericons.Server
import compose.icons.feathericons.ShoppingBag
import compose.icons.feathericons.Tag
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import compose.icons.feathericons.Upload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.aiverse.feature.plugins.domain.ManifestParseResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 插件商店列表：按来源拉取索引，支持下拉刷新、搜索，点击进入独立详情页。
 *
 * 语言标签以 AiVerse 基线 `Locale.getDefault().toLanguageTag()` 取（同包 PluginsSection.kt
 * 也提供 `currentLanguageTag()`，如需与设置页语言偏好一致可改用之）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PluginStoreSection(
    onShowPreview: (PluginStorePreview) -> Unit,
    onOpenDetail: (PluginStoreItem) -> Unit,
    viewModel: PluginStoreViewModel = hiltViewModel()
) {
    val t = LocalUIDensityTokens.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val languageTag = Locale.getDefault().toLanguageTag()
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // 商店上传：从 zip 文件选择器拿到 Uri 后，匿名 POST 到官方商店暂存（/api/upload，无需登录，审核后上架）。
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    // 上传冲突弹窗状态：非空表示当前有一次待确认的同 id 上传。
    var uploadConflict by remember { mutableStateOf<UploadConflictState?>(null) }
    // 作者名：首次上传时弹窗填写并持久化到 SharedPreferences，之后固定复用（作为 uploader 上报后端）。
    val uploadPrefs = remember { context.getSharedPreferences("plugin_upload_prefs", Context.MODE_PRIVATE) }
    var authorName by remember { mutableStateOf(uploadPrefs.getString("author_name", "") ?: "") }
    var showAuthorDialog by remember { mutableStateOf(false) }
    // 作者名弹窗打开期间暂存的 zip 字节，确认作者名后接着走原上传流程。
    var pendingZipBytes by remember { mutableStateOf<ByteArray?>(null) }

    suspend fun performUpload(zipBytes: ByteArray, uploader: String) {
        uploading = true
        val err = uploadPluginZipBytes(zipBytes, uploader)
        uploading = false
        val msg = if (err == null) {
            context.getString(R.string.plugins_store_upload_success)
        } else {
            context.getString(R.string.plugins_store_upload_failed, err)
        }
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val pkgName = queryDisplayName(context, uri) ?: ""
        val pkgSize = queryFileSize(context, uri) ?: 0L
        if (!pkgName.endsWith(".zip", ignoreCase = true)) {
            Toast.makeText(context, context.getString(R.string.plugins_store_upload_invalid_type), Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        if (pkgSize > 10L * 1024 * 1024) {
            Toast.makeText(context, context.getString(R.string.plugins_store_upload_too_large), Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            // 先在 IO 线程把 zip 一次性读进内存并解析 plugin.json 的 id：
            // 这样既能在弹窗前判断 id 冲突，也能避免 Uri 在弹窗交互期间失效。
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) {
                Toast.makeText(context, context.getString(R.string.plugins_store_upload_read_failed), Toast.LENGTH_LONG).show()
                return@launch
            }
            // 首次上传且未填写作者名：先弹窗收集作者名（必填、不可取消），确认后继续上传。
            if (authorName.isBlank()) {
                pendingZipBytes = bytes
                showAuthorDialog = true
                return@launch
            }
            val parsedId = withContext(Dispatchers.IO) { readPluginIdFromZip(bytes) }
            if (parsedId != null && state.items.any { it.plugin.id == parsedId }) {
                // 已有同 id 插件：先弹窗让用户选「覆盖更新」或「换个 ID」，取消则不上传。
                uploadConflict = UploadConflictState(zipBytes = bytes, existingId = parsedId)
                return@launch
            }
            performUpload(bytes, authorName)
        }
    }

    // 作者名输入弹窗：首次上传时弹出，必填且不可取消；确认后写入 SharedPreferences 并接着上传。
    if (showAuthorDialog) {
        var inputAuthor by remember { mutableStateOf(authorName) }
        val trimmedAuthor = inputAuthor.trim()
        AlertDialog(
            onDismissRequest = { /* 必填，不允许点击外部取消 */ },
            title = { Text(stringResource(R.string.plugins_store_upload_author_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = stringResource(R.string.plugins_store_upload_author_dialog_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    AppTextField(
                        value = inputAuthor,
                        onValueChange = { inputAuthor = it },
                        label = stringResource(R.string.plugins_store_upload_author_label),
                        singleLine = true,
                        isError = trimmedAuthor.isEmpty(),
                        supportingText = {
                            if (trimmedAuthor.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.plugins_store_upload_author_required),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        colors = dialogTextFieldColors()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (trimmedAuthor.isNotEmpty()) {
                            authorName = trimmedAuthor
                            uploadPrefs.edit().putString("author_name", authorName).apply()
                            showAuthorDialog = false
                            val bytes = pendingZipBytes
                            pendingZipBytes = null
                            if (bytes != null) {
                                scope.launch {
                                    val parsedId = withContext(Dispatchers.IO) { readPluginIdFromZip(bytes) }
                                    if (parsedId != null && state.items.any { it.plugin.id == parsedId }) {
                                        uploadConflict = UploadConflictState(zipBytes = bytes, existingId = parsedId)
                                    } else {
                                        performUpload(bytes, authorName)
                                    }
                                }
                            }
                        }
                    },
                    enabled = trimmedAuthor.isNotEmpty()
                ) {
                    Text(stringResource(R.string.plugins_store_upload_author_confirm))
                }
            },
            dismissButton = {}
        )
    }

    LaunchedEffect(state.preview) {
        state.preview?.let { preview ->
            onShowPreview(preview)
            viewModel.consumePreview()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.refreshResult.collect { failed ->
            val message = if (failed > 0) {
                context.getString(R.string.plugins_store_refresh_done_failed, failed)
            } else {
                context.getString(R.string.plugins_store_refresh_done)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val filteredItems = remember(state.items, searchQuery, languageTag) {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) {
            state.items
        } else {
            state.items.filter { item ->
                item.plugin.name.resolve(languageTag).lowercase().contains(query) ||
                    item.plugin.id.lowercase().contains(query) ||
                    item.plugin.author.lowercase().contains(query) ||
                    item.plugin.description.resolve(languageTag).lowercase().contains(query)
            }
        }
    }
    // 安装/下载错误归属具体插件，只在该插件详情页展示；整页错误只处理索引错误。
    val indexError = state.error?.takeIf { it.pluginId == null }

    uploadConflict?.let { conflict ->
        UploadIdExistsDialog(
            existingId = conflict.existingId,
            uploading = uploading,
            onDismiss = {
                if (!uploading) uploadConflict = null
            },
            onOverwrite = {
                val bytes = conflict.zipBytes
                uploadConflict = null
                scope.launch { performUpload(bytes, authorName) }
            },
            onConfirmNewId = { newId ->
                val bytes = conflict.zipBytes
                uploadConflict = null
                scope.launch {
                    uploading = true
                    val repacked = withContext(Dispatchers.IO) {
                        runCatching { repackZipWithPluginId(bytes, newId) }.getOrNull()
                    }
                    if (repacked == null) {
                        uploading = false
                        Toast.makeText(
                            context,
                            context.getString(R.string.plugins_store_upload_failed, "repack"),
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    performUpload(repacked, authorName)
                }
            }
        )
    }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                // 商店页与已安装页共用同一个底部悬浮 Tab 栏，底部留白取「常规列表留白」与
                // 「Tab 栏完整预留高度」的较大值，避免最后一项被 Tab 栏 / 渐变遮罩压住。
                bottom = maxOf(listBottomContentPadding(), t.pluginTabBarReserve)
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item(key = "upload", contentType = "upload") {
                StoreUploadCard(
                    uploading = uploading,
                    onPick = {
                        uploadPicker.launch(
                            arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")
                        )
                    }
                )
            }
            when {
                state.loading && state.items.isEmpty() -> item(key = "loading", contentType = "loading") {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(t.scale(28)),
                            strokeWidth = t.scale(2)
                        )
                    }
                }
                indexError != null && state.items.isEmpty() -> item(key = "error", contentType = "empty") {
                    StoreErrorState(
                        error = indexError,
                        onRetry = viewModel::refresh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.xl, vertical = t.scale(48))
                    )
                }
                state.items.isEmpty() -> item(key = "empty", contentType = "empty") {
                    SettingsEmptyState(
                        icon = FeatherIcons.ShoppingBag,
                        title = stringResource(R.string.plugins_store_empty_title),
                        hint = stringResource(R.string.plugins_store_empty_hint),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.xl, vertical = t.scale(48))
                    )
                }
                else -> {
                    if (state.items.size >= 3) {
                        item(key = "search", contentType = "search") {
                            ModelSearchField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                placeholder = stringResource(R.string.plugins_search_placeholder),
                                modifier = Modifier.padding(bottom = Spacing.xs)
                            )
                        }
                    }
                    if (filteredItems.isEmpty()) {
                        item(key = "search-empty", contentType = "empty") {
                            // PluginSearchEmptyState 由同包 PluginsSection.kt 提供。
                            PluginSearchEmptyState()
                        }
                    } else {
                        itemsIndexed(
                            items = filteredItems,
                            key = { _, item -> "${item.source.id}:${item.plugin.id}" },
                            contentType = { _, _ -> "store-row" }
                        ) { index, item ->
                            LazyGroupItem(index = index, lastIndex = filteredItems.lastIndex) {
                                StorePluginRow(
                                    item = item,
                                    languageTag = languageTag,
                                    onClick = { onOpenDetail(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun pluginVersionText(version: String?): String =
    version?.let { "v$it" } ?: stringResource(R.string.plugins_store_version_latest)

/**
 * 商店列表与已安装列表同版式：图标 + 名称 + 描述；商店不需要开关，点击进入独立详情页。
 *
 * TODO: 原 AiCode 用固定黄色徽标（0xFFFFE082 / 0xFF5C4300）区分非官方插件；改为 AiVerse
 * 语义色 warningContainer / onWarningContainer，避免硬编码颜色。
 */
/**
 * 商店卡片右上角官方/非官方角标。
 * 官方插件显示蓝色「官方」标签，非官方显示警示色「非官方」标签。
 */
@Composable
private fun OfficialBadge(official: Boolean) {
    McpPill(
        text = stringResource(
            if (official) R.string.plugins_store_badge_official
            else R.string.plugins_store_badge_unofficial
        ),
        textColor = if (official) {
            MaterialTheme.semanticColors.onInfoContainer
        } else {
            MaterialTheme.semanticColors.onWarningContainer
        },
        backgroundColor = if (official) {
            MaterialTheme.semanticColors.infoContainer
        } else {
            MaterialTheme.semanticColors.warningContainer
        }
    )
}

@Composable
private fun StorePluginRow(item: PluginStoreItem, languageTag: String, onClick: () -> Unit) {
    val t = LocalUIDensityTokens.current
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(t.scale(40))
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(Radius.sm)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = FeatherIcons.Package,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(t.scale(22))
                )
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.plugin.name.resolve(languageTag),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(t.scale(6)))
                Text(
                    text = item.plugin.description.resolve(languageTag),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            // 官方/非官方徽标内联排在右箭头左侧，避免角标浮在右上角与箭头重叠。
            OfficialBadge(official = item.official)

            Spacer(modifier = Modifier.width(Spacing.sm))

            Icon(
                imageVector = FeatherIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.semanticColors.subtleText,
                modifier = Modifier.size(t.scale(18))
            )
        }
    }
}

/**
 * 商店详情页：头部卡片 + 信息分组 + 安装按钮。
 *
 * 字段来自 P3a 索引模型（`minAppVersion` / `tags` / `license` / `homepage` 等，已核对一致）。
 */
@Composable
internal fun PluginStoreDetailSection(
    item: PluginStoreItem,
    onShowPreview: (PluginStorePreview) -> Unit,
    viewModel: PluginStoreViewModel = hiltViewModel()
) {
    val t = LocalUIDensityTokens.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val latest = state.items.firstOrNull {
        it.source.id == item.source.id && it.plugin.id == item.plugin.id
    } ?: item
    val context = LocalContext.current
    val languageTag = Locale.getDefault().toLanguageTag()
    val installing = state.installingPluginId == latest.plugin.id

    LaunchedEffect(state.preview) {
        // 预览是全局单槽状态；只弹出当前插件的预览，避免 A 的安装结果覆盖到 B 的详情页。
        val preview = state.preview?.takeIf { it.preview.manifest.id == latest.plugin.id }
            ?: return@LaunchedEffect
        onShowPreview(preview)
        viewModel.consumePreview()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(top = Spacing.md, bottom = Spacing.xl)
    ) {
        StoreHeaderCard(item = latest, languageTag = languageTag)

        SettingsGroupHeader(text = stringResource(R.string.plugins_section_info))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Tag,
                title = stringResource(R.string.plugins_store_detail_version),
                trailing = { StoreInfoValue(pluginVersionText(latest.plugin.version)) }
            )
            if (latest.plugin.tags.isNotEmpty()) {
                SettingsDivider()
                SettingsRow(
                    icon = FeatherIcons.Package,
                    title = stringResource(R.string.plugins_store_detail_tags),
                    trailing = { StoreInfoValue(latest.plugin.tags.joinToString(" · ")) }
                )
            }
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Server,
                title = stringResource(R.string.plugins_store_detail_index_source),
                trailing = { StoreInfoValue(latest.source.name) }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Github,
                title = stringResource(R.string.plugins_store_detail_source),
                onClick = { openUrl(context, latest.plugin.repository) }
            )
            latest.plugin.license?.let { license ->
                SettingsDivider()
                SettingsRow(
                    icon = FeatherIcons.Book,
                    title = stringResource(R.string.plugins_store_detail_license),
                    trailing = { StoreInfoValue(license) }
                )
            }
            latest.plugin.homepage?.let { homepage ->
                SettingsDivider()
                SettingsRow(
                    icon = FeatherIcons.Globe,
                    title = stringResource(R.string.plugins_store_detail_homepage),
                    onClick = { openUrl(context, homepage) }
                )
            }
        }

        Button(
            onClick = { viewModel.install(latest) },
            enabled = latest.compatible && !installing &&
                (latest.installedVersion == null || latest.updateAvailable),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.lg)
        ) {
            if (installing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(t.scale(18)),
                    strokeWidth = t.scale(2),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
            }
            Text(
                when {
                    installing -> stringResource(R.string.plugins_installing)
                    !latest.compatible -> latest.plugin.minAppVersion?.let { minimum ->
                        stringResource(R.string.plugins_store_requires_min_version, minimum)
                    } ?: stringResource(R.string.plugins_store_incompatible)
                    latest.installedVersion != null && !latest.updateAvailable ->
                        stringResource(R.string.plugins_store_installed)
                    latest.updateAvailable -> stringResource(R.string.plugins_store_update)
                    else -> stringResource(R.string.plugins_store_install)
                }
            )
        }
        val installError = state.error?.takeIf {
            it.pluginId == latest.plugin.id &&
                (it.code == PluginStoreErrorCode.DOWNLOAD_FAILED ||
                    it.code == PluginStoreErrorCode.NO_COMPATIBLE_RELEASE ||
                    it.code == PluginStoreErrorCode.PREVIEW_FAILED)
        }
        if (installError != null) {
            Text(
                text = storeErrorMessage(installError),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.sm, start = Spacing.xs, end = Spacing.xs)
            )
        }
    }
}

/**
 * 详情页头部卡片：图标 + 名称 + 作者 + 描述。
 *
 * TODO: 圆角沿用 AiVerse [Radius.lg]（= 原 14.dp），表面用语义色 cardSurface；
 * AiVerse 磨砂玻璃卡片（glassOr）待统一风格时再对齐。
 */
@Composable
private fun StoreHeaderCard(item: PluginStoreItem, languageTag: String) {
    val t = LocalUIDensityTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.semanticColors.cardSurface
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(t.scale(48))
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(Radius.mdLarge)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = FeatherIcons.Package,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(t.scale(26))
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.lg))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.plugin.name.resolve(languageTag),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.plugin.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val description = item.plugin.description.resolve(languageTag)
                    if (description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = Spacing.xl)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.sm)
            ) {
                OfficialBadge(official = item.official)
            }
        }
    }
}

@Composable
private fun StoreInfoValue(text: String) {
    val t = LocalUIDensityTokens.current
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.semanticColors.subtleText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = t.scale(190))
    )
}

/** 索引读取失败时的整页占位，与商店空态用同一套版式。 */
@Composable
private fun StoreErrorState(
    error: PluginStoreError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsEmptyState(
        icon = FeatherIcons.CloudOff,
        title = storeErrorMessage(error),
        hint = error.detail?.takeIf { it.isNotBlank() },
        actions = {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.plugins_store_retry))
            }
        },
        modifier = modifier
    )
}

@Composable
private fun storeErrorMessage(error: PluginStoreError): String = when (error.code) {
    PluginStoreErrorCode.NETWORK -> stringResource(R.string.plugins_store_error_network)
    PluginStoreErrorCode.INVALID_INDEX -> stringResource(R.string.plugins_store_error_invalid)
    PluginStoreErrorCode.NO_COMPATIBLE_RELEASE -> stringResource(R.string.plugins_store_error_no_release)
    PluginStoreErrorCode.DOWNLOAD_FAILED -> stringResource(R.string.plugins_store_error_download)
    PluginStoreErrorCode.PREVIEW_FAILED -> error.pluginError?.let { pluginErrorMessage(it) }
        ?: stringResource(R.string.plugins_store_error_preview)
}

/**
 * 商店顶部上传入口：点击选择 zip 包，匿名提交到官方商店暂存，审核通过后上架。
 */
@Composable
private fun StoreUploadCard(
    uploading: Boolean,
    onPick: () -> Unit
) {
    val t = LocalUIDensityTokens.current
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !uploading, onClick = onPick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = FeatherIcons.Upload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(t.scale(20))
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.plugins_store_upload_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.plugins_store_upload_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.semanticColors.subtleText
                )
            }
            if (uploading) {
                CircularProgressIndicator(modifier = Modifier.size(t.scale(16)), strokeWidth = t.scale(2))
            } else {
                Icon(
                    imageVector = FeatherIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.semanticColors.subtleText,
                    modifier = Modifier.size(t.scale(18))
                )
            }
        }
    }
}

/**
 * 上传 zip 到官方商店暂存接口（`/api/upload`，匿名，无需登录）。返回 null 表示成功，否则返回错误信息。
 */
private suspend fun uploadPluginZipBytes(zipBytes: ByteArray, uploader: String): String? = withContext(Dispatchers.IO) {
    try {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "plugin.zip", zipBytes.toRequestBody("application/zip".toMediaType()))
            .addFormDataPart("uploader", uploader)
            .build()
        val request = Request.Builder()
            .url("https://api.aivapp.cn/upload")
            .post(body)
            .build()
        OkHttpClient().newCall(request).execute().use { resp ->
            if (resp.isSuccessful) return@withContext null
            // 失败时服务端返回 {"ok":false,"error":"具体原因"}，提取展示，避免只给状态码难排查。
            val bodyStr = resp.body?.string().orEmpty()
            val detail = extractServerError(bodyStr)
            "HTTP ${resp.code}" + (detail?.let { ": $it" } ?: "")
        }
    } catch (e: Exception) {
        e.message ?: e.javaClass.simpleName
    }
}

/**
 * 上传冲突弹窗状态：携带待上传的 zip 原始字节与已存在的插件 id。
 */
private data class UploadConflictState(
    val zipBytes: ByteArray,
    val existingId: String
)

/**
 * 遍历 zip 内所有条目，找到 `plugin.json` 并解析出插件 id。
 * 找不到或解析失败均返回 null，由调用方回退到「直接上传」行为。
 */
private fun readPluginIdFromZip(zipBytes: ByteArray): String? {
    return try {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name == "plugin.json") {
                    val text = zis.readBytes().toString(Charsets.UTF_8)
                    val result = PluginManifestParser.parse(text)
                    return (result as? ManifestParseResult.Success)?.manifest?.id
                }
                entry = zis.nextEntry
            }
            null
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * 把 zip 内 `plugin.json` 的 `id` 字段改成 `newId`，并同步 `publisher` 为 `newId` 第一段，
 * 其余字段原样保留，重新打包 zip。
 *
 * 解析器要求 `publisher` 必须等于 `id` 的第一段，换 ID 时不同步会导致重打包后的清单解析失败。
 *
 * 用 `org.json.JSONObject` 读改写以保留其他字段；用 `ZipOutputStream` 拷贝原包所有条目，
 * 仅替换 `plugin.json` 条目内容。重打包必须在 IO 协程调用（大文件耗时）。
 */
private fun repackZipWithPluginId(zipBytes: ByteArray, newId: String): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zos ->
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val data = zis.readBytes()
                val isManifest = !entry.isDirectory && entry.name == "plugin.json"
                val outBytes = if (isManifest) {
                    val obj = org.json.JSONObject(data.toString(Charsets.UTF_8))
                    obj.put("id", newId)
                    // 解析器要求 publisher 必须等于 id 第一段，换 ID 时必须联动，否则重打包后清单解析失败。
                    obj.put("publisher", newId.substringBefore('.'))
                    obj.toString().toByteArray(Charsets.UTF_8)
                } else {
                    data
                }
                // 用 DEFATED 重新写，避免保留原 entry 的 size/crc/method 与新内容不匹配。
                zos.putNextEntry(ZipEntry(entry.name).apply { method = ZipEntry.DEFLATED })
                zos.write(outBytes)
                zos.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
    return output.toByteArray()
}

/**
 * 同 id 上传确认弹窗：提供「覆盖更新」与「换个 ID」两条路径。
 * 点空白/取消不调 onDismissRequest 以外的任何动作 —— 不上传。
 */
@Composable
private fun UploadIdExistsDialog(
    existingId: String,
    uploading: Boolean,
    onDismiss: () -> Unit,
    onOverwrite: () -> Unit,
    onConfirmNewId: (String) -> Unit
) {
    var editingId by remember { mutableStateOf(false) }
    var newId by remember { mutableStateOf("") }
    val trimmedNewId = newId.trim()
    val newIdEmpty = trimmedNewId.isEmpty()
    // 新 ID 必须满足 xx.yy 格式，否则重打包后的清单仍无法通过解析。
    val newIdInvalid = newIdEmpty || !PluginManifestParser.isValidPluginId(trimmedNewId)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plugins_store_upload_id_exists_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (!editingId) {
                    Text(
                        text = stringResource(R.string.plugins_store_upload_id_exists_body, existingId),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = stringResource(R.string.plugins_store_upload_new_id_hint, existingId),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    AppTextField(
                        value = newId,
                        onValueChange = { newId = it },
                        label = stringResource(R.string.plugins_store_upload_new_id_label),
                        singleLine = true,
                        enabled = !uploading,
                        isError = newIdInvalid,
                        colors = dialogTextFieldColors()
                    )
                    if (!newIdEmpty && !PluginManifestParser.isValidPluginId(trimmedNewId)) {
                        Text(
                            text = stringResource(R.string.plugins_store_upload_new_id_invalid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!editingId) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    TextButton(onClick = onOverwrite, enabled = !uploading) {
                        Text(stringResource(R.string.plugins_store_upload_overwrite))
                    }
                    TextButton(
                        onClick = { editingId = true; newId = "" },
                        enabled = !uploading
                    ) {
                        Text(stringResource(R.string.plugins_store_upload_change_id))
                    }
                }
            } else {
                TextButton(
                    onClick = { onConfirmNewId(newId.trim()) },
                    enabled = !uploading && !newIdInvalid
                ) {
                    Text(stringResource(R.string.plugins_store_upload_new_id_confirm))
                }
            }
        },
        dismissButton = {
            if (editingId) {
                TextButton(onClick = { editingId = false; newId = "" }, enabled = !uploading) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        }
    )
}

/**
 * 从商店后端错误响应体（`{"ok":false,"error":"..."}`）提取可读的错误原因。
 * 解析失败返回 null，由调用方回退到仅状态码。
 */
private fun extractServerError(body: String): String? {
    if (body.isBlank()) return null
    return try {
        val obj = org.json.JSONObject(body)
        if (obj.optBoolean("ok", true)) null else obj.optString("error").takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

/**
 * 查询 Uri 的显示文件名（用于校验 .zip 扩展名）。
 */
private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) return c.getString(0)
    }
    return null
}

/**
 * 查询 Uri 的文件大小（字节，用于校验 ≤10MB 上限）。
 */
private fun queryFileSize(context: android.content.Context, uri: Uri): Long? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
    }
    return null
}