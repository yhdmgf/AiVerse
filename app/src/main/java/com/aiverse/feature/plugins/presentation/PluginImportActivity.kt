package com.aiverse.feature.plugins.presentation

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aiverse.R
import com.aiverse.core.theme.AIEditorTheme
import com.aiverse.core.theme.Spacing
import com.aiverse.core.util.FileLogger
import com.aiverse.feature.plugins.domain.PluginInstallResult
import com.aiverse.feature.plugins.domain.PluginManager
import com.aiverse.feature.plugins.domain.PluginPreview
import com.aiverse.feature.plugins.domain.PluginPreviewResult
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 通过协议 `aiverse://plugin?url=<插件 zip 直链>`（也兼容 `aiverse://?url=`）直接导入插件（测试）。
 *
 * 流程：下载 zip 到 cache → [PluginManager.preview] 出预览 → 用户确认 → [PluginManager.install]。
 * 与文件导入共用同一套预览/安装域逻辑，只是入口从本地文件换成了远程直链。
 */
@AndroidEntryPoint
class PluginImportActivity : ComponentActivity() {

    @Inject lateinit var pluginManager: PluginManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var uiState by mutableStateOf<ImportUi>(ImportUi.Loading)
    private var downloaded: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val url = intent?.data?.getQueryParameter("url")?.trim().orEmpty()
        if (url.isEmpty()) {
            FileLogger.w(TAG, "缺少 url 参数")
            toast(R.string.plugin_import_link_invalid)
            finish()
            return
        }
        setContent {
            AIEditorTheme {
                PluginImportScreen(state = uiState, onConfirm = ::confirmInstall, onDismiss = ::finish)
            }
        }
        scope.launch { downloadAndPreview(url) }
    }

    override fun onDestroy() {
        scope.cancel()
        downloaded?.delete()
        downloaded = null
        super.onDestroy()
    }

    private suspend fun downloadAndPreview(url: String) {
        uiState = ImportUi.Loading
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val file = downloadToCache(url)
                downloaded = file
                file.inputStream().use { input ->
                    when (val read = pluginManager.preview(input)) {
                        is PluginPreviewResult.Ready -> read.preview
                        is PluginPreviewResult.Failed -> error("preview rejected: ${read.errors.size} errors")
                    }
                }
            }
        }
        uiState = result.fold(
            onSuccess = { ImportUi.Ready(it) },
            onFailure = { e ->
                FileLogger.w(TAG, "下载或解析插件包失败", e)
                ImportUi.Failed(R.string.plugin_import_download_failed)
            }
        )
    }

    /** 把远程 zip 下到 cacheDir/import/；跟随一次重定向，够测试用。 */
    private fun downloadToCache(url: String): File {
        val dir = File(cacheDir, "import").apply { mkdirs() }
        val out = File(dir, "plugin-import-${System.currentTimeMillis()}.zip")
        var connection: HttpURLConnection? = null
        try {
            var target = url
            repeat(2) {
                val conn = URL(target).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 10_000
                conn.readTimeout = 30_000
                connection = conn
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: error("重定向缺少 Location")
                    target = location
                    conn.disconnect()
                    return@repeat
                }
                if (code !in 200..299) error("下载失败 HTTP $code")
                conn.inputStream.use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                return out
            }
            error("重定向次数过多")
        } finally {
            connection?.disconnect()
        }
    }

    private fun confirmInstall() {
        val ready = uiState as? ImportUi.Ready ?: return
        uiState = ImportUi.Loading
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                pluginManager.install(ready.preview.token)
            }
            when (result) {
                is PluginInstallResult.Installed -> {
                    toast(R.string.plugin_import_success)
                    downloaded = null
                    finish()
                }
                is PluginInstallResult.Failed -> {
                    FileLogger.w(TAG, "安装插件失败: ${result.error.code}")
                    uiState = ImportUi.Failed(R.string.plugin_import_failed)
                }
            }
        }
    }

    private fun toast(res: Int) {
        Toast.makeText(this, res, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val TAG = "PluginImportActivity"
    }
}

private sealed interface ImportUi {
    data object Loading : ImportUi
    data class Ready(val preview: PluginPreview) : ImportUi
    data class Failed(val messageRes: Int) : ImportUi
}

@Composable
private fun PluginImportScreen(
    state: ImportUi,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏：与插件详情页一致的 PluginBackBar —— 左返回/取消，右上角安装。
        PluginBackBar(
            onBack = onDismiss,
            trailing = {
                if (state is ImportUi.Ready) {
                    Button(onClick = onConfirm) {
                        Text(text = stringResource(R.string.plugin_import_confirm))
                    }
                }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.plugin_import_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            when (state) {
                ImportUi.Loading -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Text(
                        text = stringResource(R.string.plugin_import_downloading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ImportUi.Ready -> {
                    val manifest = state.preview.manifest
                    Text(
                        text = "${manifest.id} ${manifest.version}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                is ImportUi.Failed -> {
                    Text(
                        text = stringResource(state.messageRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
