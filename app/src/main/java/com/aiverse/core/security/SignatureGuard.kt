package com.aiverse.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.aiverse.BuildConfig
import java.security.MessageDigest

/**
 * 启动签名校验：合并构建期注入的随机分片（BuildConfig）得到 MD5 + SHA-256 期望指纹，
 * 与当前 APK 签名证书指纹比对，不一致说明包被替换/篡改。
 * 分片随机打乱（每版本不同）且不完整出现，源码/BuildConfig 中无完整哈希。
 */
object SignatureGuard {

    private val md5Segments: Array<String> = BuildConfig.SIG_MD5_SEGMENTS
    private val sha256Segments: Array<String> = BuildConfig.SIG_SHA256_SEGMENTS

    // 分片形如 "原索引:段值"（如 "7:a1"），按索引还原顺序再拼接出完整指纹。
    private fun merge(segments: Array<String>): String = segments
        .map { it.substringBefore(":").toInt() to it.substringAfter(":") }
        .sortedBy { it.first }
        .joinToString("") { it.second }

    /**
     * @return true 表示签名匹配（或 debug / 未注入 keystore 的构建，跳过校验）。
     */
    fun verify(context: Context): Boolean {
        if (BuildConfig.DEBUG) return true
        if (md5Segments.isEmpty() || sha256Segments.isEmpty()) return true
        val expectedMd5 = merge(md5Segments)
        val expectedSha = merge(sha256Segments)
        val actual = computeFingerprints(context) ?: return true
        return expectedMd5.equals(actual.first, ignoreCase = true) &&
            expectedSha.equals(actual.second, ignoreCase = true)
    }

    private fun computeFingerprints(context: Context): Pair<String, String>? = try {
        val pm = context.packageManager
        val cert: ByteArray? = if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                .signatures?.firstOrNull()?.toByteArray()
        } ?: return null
        val toHex = { bytes: ByteArray -> bytes.joinToString("") { "%02x".format(it) } }
        val md5 = toHex(MessageDigest.getInstance("MD5").digest(cert))
        val sha = toHex(MessageDigest.getInstance("SHA-256").digest(cert))
        md5 to sha
    } catch (e: Exception) {
        null
    }
}
