package com.aiverse.feature.agent.presentation.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Package
import com.aiverse.R
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Spacing
import com.aiverse.core.ui.AdaptiveModalBottomSheet
import com.aiverse.feature.agent.domain.model.InstalledApp
import com.aiverse.feature.agent.domain.repository.InstalledAppRepository
import com.aiverse.feature.settings.presentation.component.ModelSearchField

/** 「上传应用」弹层：顶部搜索（按应用名/包名实时过滤），下方应用列表（图标 + 名称/包名 + 右侧体积）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppUploadSheet(
    repository: InstalledAppRepository,
    onDismiss: () -> Unit,
    onExport: (InstalledApp) -> Unit
) {
    val t = LocalUIDensityTokens.current
    val sheetState = rememberModalBottomSheetState()
    var query by remember { mutableStateOf("") }
    // null = 仍在加载；非 null = 加载完成（可能为空）。
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }

    LaunchedEffect(Unit) {
        apps = runCatching { repository.listInstalledApps() }.getOrDefault(emptyList())
    }

    val filtered = remember(apps, query) {
        val list = apps.orEmpty()
        if (query.isBlank()) list
        else list.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
        ) {
            Text(
                text = stringResource(R.string.chat_upload_app_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = Spacing.sm)
            )
            ModelSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.chat_upload_app_search_hint),
                modifier = Modifier.padding(bottom = Spacing.sm)
            )
            val list = apps
            when {
                list == null -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.lg),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(t.scale(24))
                        )
                    }
                }
                filtered.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.chat_upload_app_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.lg),
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.65f)
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            AppRow(
                                app = app,
                                repository = repository,
                                iconSize = t.scale(40),
                                onClick = { onExport(app) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    repository: InstalledAppRepository,
    iconSize: Dp,
    onClick: () -> Unit
) {
    val t = LocalUIDensityTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = t.sm, vertical = t.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(
            packageName = app.packageName,
            repository = repository,
            size = iconSize
        )
        Spacer(Modifier.width(t.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(t.sm))
        Text(
            text = formatBytes(app.sizeBytes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AppIcon(
    packageName: String,
    repository: InstalledAppRepository,
    size: Dp
) {
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = repository.loadIcon(packageName)?.let { drawable ->
            runCatching { drawableToImageBitmap(drawable) }.getOrNull()
        }
    }
    val current = icon
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit
        )
    } else {
        Icon(
            FeatherIcons.Package,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size)
        )
    }
}

private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
    val width = drawable.intrinsicWidth.coerceAtLeast(1)
    val height = drawable.intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}
