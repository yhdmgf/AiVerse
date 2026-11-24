package com.aiverse.core.util

import java.net.URLConnection

/** 归一成 `text/plain` 的纯文本 / 代码扩展名（发送给模型、编辑器打开都用这一档）。 */
val TEXT_EXTS = setOf(
    "txt", "md", "kt", "java", "js", "ts", "json", "xml", "html", "css", "py", "sh", "gradle", "kts"
)

/**
 * 扩展名 → MIME 统一映射（跨 feature 共享）。
 *
 * 只覆盖「扩展名本身就决定类型」的那几类：图片、PDF、Office 文档、压缩包、安装包。
 * 纯文本类（[TEXT_EXTS]）刻意不收：同一后缀在不同调用点想要的目标不同
 * （发送给模型时归成 `text/plain`，交给系统应用打开时用 `text/plain` 反而打不开），
 * 由调用方按自己的语义决定。
 */
private val EXT_MIME_TYPES = mapOf(
    // 图片
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "png" to "image/png",
    "webp" to "image/webp",
    "gif" to "image/gif",
    "bmp" to "image/bmp",
    "svg" to "image/svg+xml",
    // 文档
    "pdf" to "application/pdf",
    "doc" to "application/msword",
    "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "xls" to "application/vnd.ms-excel",
    "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "ppt" to "application/vnd.ms-powerpoint",
    "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    // 压缩包 / 安装包
    "zip" to "application/zip",
    "apk" to "application/vnd.android.package-archive"
)

/**
 * 按扩展名查 MIME；查不到回退 [URLConnection.guessContentTypeFromName]，仍拿不到返回 null
 * （调用方按自身语义兜底，如 `application/octet-stream` 或通配类型）。
 */
fun extToMimeType(fileName: String): String? {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return EXT_MIME_TYPES[ext] ?: runCatching { URLConnection.guessContentTypeFromName(fileName) }.getOrNull()
}

/**
 * 对外分享 / 系统打开有意义的 MIME：空串与 `application/octet-stream` 都是「不知道」的占位值，
 * 拿它们请求系统应用几乎必然打不开，按扩展名再查一次更有用。
 */
fun shareableMimeType(mimeType: String, fileName: String): String =
    mimeType.takeIf { it.isNotBlank() && !it.equals("application/octet-stream", ignoreCase = true) }
        ?: extToMimeType(fileName)
        ?: "application/octet-stream"
