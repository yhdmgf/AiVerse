package com.aiverse.feature.backup.presentation

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import com.aiverse.core.theme.AIEditorTheme
import com.aiverse.core.util.FileLogger
import com.aiverse.feature.backup.data.HandoffHttpServer
import com.aiverse.feature.backup.domain.BackupCrypto
import com.aiverse.feature.backup.domain.BackupManager
import com.aiverse.feature.backup.domain.BackupOptions
import com.aiverse.feature.backup.domain.HandoffChannel
import com.aiverse.feature.backup.domain.HandoffProtocol
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * beta 通过协议（`aiverse://auth/grant-backup`）打开正式版：校验调用方签名后弹授权页，
 * 用户放行即按所选通道把加密备份交给 beta，并等 beta 回传确认后再清理临时文件。
 *
 * 隐式 intent 下 [android.app.Activity.getCallingPackage] 为 null，故按 [HandoffProtocol.BETA_PACKAGE]
 * 的签名与自己的签名比对来识别 beta。
 */
@AndroidEntryPoint
class AuthGrantActivity : ComponentActivity() {

    @Inject lateinit var backupManager: BackupManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val server = HandoffHttpServer(deleteFileOnStop = true)
    private var handoffFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val data = intent?.data ?: run { FileLogger.w(TAG, "缺少 aiverse://auth data"); finish(); return }
        when (data.path) {
            "/confirmed" -> { onConfirmed(); return }
            else -> Unit
        }
        if (!isFromBeta()) { FileLogger.w(TAG, "调用方签名与 beta 不一致，拒绝授权"); finish(); return }
        // 注意：不要调用 setContentView(null)。ComponentActivity + Compose 的 setContent {}
        // 会自建并安装 ComposeView，先 setContentView(null) 会把 null 传给 PhoneWindow.addView，
        // 直接抛 "Cannot add a null child view to a ViewGroup"（beta 拉起正式版授权页时崩在这里）。
        setContent { AIEditorTheme { AuthGrantScreen(onAllow = ::onAllow, onDeny = ::onDeny, onOpenExistingBackup = ::onOpenExistingBackup) } }
    }

    /** 校验调用方是签名一致的 beta 包；隐式 intent 下不能靠 getCallingPackage()。 */
    private fun isFromBeta(): Boolean {
        val pm = packageManager
        val beta = runCatching {
            if (Build.VERSION.SDK_INT >= 28) pm.getPackageInfo(HandoffProtocol.BETA_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES)
            else @Suppress("DEPRECATION") pm.getPackageInfo(HandoffProtocol.BETA_PACKAGE, PackageManager.GET_SIGNATURES)
        }.getOrNull() ?: return false
        val betaSigs = if (Build.VERSION.SDK_INT >= 28) beta.signingInfo?.apkContentsSigners?.toList().orEmpty()
        else @Suppress("DEPRECATION") beta.signatures?.toList().orEmpty()
        val self = runCatching {
            if (Build.VERSION.SDK_INT >= 28) pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.toList().orEmpty()
            else @Suppress("DEPRECATION") pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures?.toList().orEmpty()
        }.getOrDefault(emptyList())
        return betaSigs.isNotEmpty() && betaSigs.any { it in self }
    }

    /** 用户选通道后：生成加密备份 → 按通道回传给 beta。 */
    private fun onAllow(channel: HandoffChannel) {
        scope.launch {
            val requestId = intent?.getStringExtra(HandoffProtocol.EXTRA_REQUEST_ID) ?: ""
            val file = runCatching { exportEncrypted(requestId) }.getOrNull()
            if (file == null) { FileLogger.e(TAG, "导出加密备份失败"); finish(); return@launch }
            handoffFile = file
            runCatching { when (channel) {
                HandoffChannel.FileProviderUri -> deliverByFileProvider(file, requestId)
                HandoffChannel.HttpServer -> deliverByHttp(file, requestId)
            } }
            .onFailure { FileLogger.e(TAG, "按 $channel 交付备份失败", it) }
        }
    }

    private suspend fun exportEncrypted(requestId: String): File? = withContext(Dispatchers.IO) {
        val password = HandoffProtocol.newKeyPassword()
        try {
            val plain = File(cacheDir, "handoff-$requestId-plain.tar.gz")
            val file = File(cacheDir, "handoff-$requestId.tar.gz")
            // 全部用户数据 + git 凭据（跨包迁移免重填）。workspaceFiles 默认 false，避免大工作区拖慢传输。
            backupManager.export(null, BackupOptions(gitCredentials = true), plain.outputStream().buffered())
            file.outputStream().buffered().use { out ->
                HandoffProtocol.writeKeyHeader(out, password)
                plain.inputStream().use { BackupCrypto.encryptStream(it, out, password) }
            }
            plain.delete()
            file
        } finally { password.fill('\u0000') }
    }

    private fun deliverByFileProvider(file: File, requestId: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("aiverse://backup/import"))
            .setDataAndType(uri, "application/gzip")
            .setPackage(HandoffProtocol.BETA_PACKAGE)
            .putExtra(HandoffProtocol.EXTRA_REQUEST_ID, requestId)
            .putExtra(HandoffProtocol.EXTRA_HANDOFF_URI, uri.toString())
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun deliverByHttp(file: File, requestId: String) {
        val port = server.start(file)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("aiverse://backup/import"))
            .setPackage(HandoffProtocol.BETA_PACKAGE)
            .putExtra(HandoffProtocol.EXTRA_REQUEST_ID, requestId)
            .putExtra(HandoffProtocol.EXTRA_HANDOFF_PORT, port)
            .putExtra(HandoffProtocol.EXTRA_HANDOFF_FILE, file.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    /** beta 导入完成后回传：清理临时文件与 HTTP 服务并退出。 */
    private fun onConfirmed() {
        runCatching { server.stop() }
        handoffFile?.delete()
        FileLogger.i(TAG, "beta 已确认收到备份，已清理临时文件")
        finish()
    }

    private fun onDeny() { finish() }

    private fun onOpenExistingBackup() {
        runCatching { server.stop() }
        handoffFile?.delete()
        startActivity(Intent(this, com.aiverse.MainActivity::class.java).setAction("backup"))
        finish()
    }

    override fun onDestroy() {
        runCatching { server.stop() }
        handoffFile?.delete()
        super.onDestroy()
    }

    private companion object { const val TAG = "AuthGrant" }
}