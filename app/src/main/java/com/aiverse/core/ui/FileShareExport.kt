package com.aiverse.core.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.aiverse.core.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 文件（zip）分享 / 导出的公共入口：把「分享文本 / 分享 zip / 导出 zip」三套 Intent 逻辑收敛到一处，
 * 供技能、脚本插件等需要打包分享/导出的功能共用（无任何业务语义，纯通用，跨 feature 可复用）。
 *
 * - [shareText]：分享纯文本，走系统分享面板。
 * - [shareZip]：把 zip 字节流写进 cacheDir（FileProvider 已声明 cache-path），授权后拉系统分享。
 * - [exportZip]：用 SAF CreateDocument 让用户选保存位置，再把 zip 字节流写入所选 URI。
 *
 * 由 [rememberFileShareExport] 创建；不要在多个 Composable 里各自 new，应经 remember 复用同一实例。
 */
class FileShareExport(
    private val context: Context,
    private val scope: CoroutineScope,
    private val shareLauncher: ActivityResultLauncher<Intent>,
    private val exportLauncher: ActivityResultLauncher<String>
) {
    // SAF CreateDocument 是异步回调：先暂存待写字节与结果回调，用户选完位置再落盘。
    private var pendingExportBytes: ByteArray? = null
    private var pendingExportCallback: ((Boolean) -> Unit)? = null

    /** 分享纯文本，走系统分享面板；[chooserTitle] 为面板标题。 */
    fun shareText(text: String, chooserTitle: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        shareLauncher.launch(Intent.createChooser(send, chooserTitle))
    }

    /**
     * 分享 zip 字节流：写进 cacheDir（FileProvider 的 cache-path 已覆盖），授权后拉系统分享。
     * [fileName] 仅作文件名展示，落盘前加时间戳避免同名互相覆盖。
     */
    fun shareZip(bytes: ByteArray, fileName: String, chooserTitle: String, onResult: (Boolean) -> Unit) {
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    val zipFile = File(context.cacheDir, "share_${System.currentTimeMillis()}_$fileName")
                    zipFile.writeBytes(bytes)
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
                }.getOrNull()
            }
            if (uri == null) {
                onResult(false)
                return@launch
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            shareLauncher.launch(Intent.createChooser(send, chooserTitle))
            onResult(true)
        }
    }

    /** 用 SAF CreateDocument 导出 zip 字节流到用户选择的位置；[onResult] 报告是否成功。 */
    fun exportZip(bytes: ByteArray, suggestedName: String, onResult: (Boolean) -> Unit) {
        pendingExportBytes = bytes
        pendingExportCallback = onResult
        exportLauncher.launch(suggestedName)
    }

    /** 由 [rememberFileShareExport] 的 CreateDocument 回调调用：把暂存字节写入用户选中的 URI。 */
    fun onExportResult(uri: Uri?) {
        val bytes = pendingExportBytes ?: return
        val cb = pendingExportCallback
        pendingExportBytes = null
        pendingExportCallback = null
        if (uri == null) {
            cb?.invoke(false)
            return
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
                }.getOrDefault(false)
            }
            cb?.invoke(ok)
        }
    }
}

/**
 * 通用「⋯」菜单项的统一内容：图标 + 文字（遵循项目「溢出收纳 ⋯ 菜单」约定）。
 * 技能、脚本插件等各项 ⋯ 菜单共用，保证菜单项样式一致。
 */
@Composable
fun FileMenuRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(text)
    }
}

/**
 * 创建并记住一个 [FileShareExport]。
 *
 * SAF 的 CreateDocument 回调在首次组合后才会触发（用户操作之后），用长度为 1 的数组占位引用
 * 解「回调先于 helper 创建」的环：helper 一旦创建立即写入数组，回调经数组取实例。
 */
@Composable
fun rememberFileShareExport(): FileShareExport {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val holder = remember { arrayOfNulls<FileShareExport>(1) }
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> holder[0]?.onExportResult(uri) }
    val helper = remember(context, scope, shareLauncher, exportLauncher) {
        FileShareExport(context, scope, shareLauncher, exportLauncher)
    }
    holder[0] = helper
    return helper
}
