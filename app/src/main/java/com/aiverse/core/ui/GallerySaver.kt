package com.aiverse.core.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把图片保存到系统相册（`Pictures/AiVerse`）。
 *
 * 只用 Android 10+（API 29）的 [MediaStore] + [MediaStore.Images.Media.IS_PENDING] 路径：
 * 通过 `contentResolver.insert` 先建一条 IS_PENDING=1 的占位记录，拷贝完置 0 才会在相册可见，
 * 全程无需 `WRITE_EXTERNAL_STORAGE` 权限。应用刻意锁定 `targetSdk=28`，故 API 26~28（Android 8~9）
 * 没有可用的免权限写入路径，直接返回 false，由调用方降级提示（不做权限请求的复杂度）。
 *
 * @param source 宿主本地图片文件；调用方应保证文件存在且非空（失效时先经文件访问抽象重取）。
 * @param mimeType 图片 MIME（如 `image/jpeg`）；空串按 `image/jpeg` 兜底。
 * @return 是否写入成功；false 表示不支持（API < 29）或写入失败。
 */
suspend fun saveImageToGallery(context: Context, source: File, mimeType: String): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    return withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, source.name)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType.ifBlank { "image/jpeg" })
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AiVerse")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        try {
            val written = resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { it.copyTo(output) }
                true
            } ?: false
            if (written) {
                // 写完再把 IS_PENDING 置 0，相册才刷出这张图；失败则下面删除占位记录。
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            written
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }
}
