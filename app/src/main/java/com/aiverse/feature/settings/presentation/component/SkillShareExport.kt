package com.aiverse.feature.settings.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import com.aiverse.core.ui.FileMenuRow
import com.aiverse.core.ui.FileShareExport
import com.aiverse.core.ui.rememberFileShareExport

/**
 * 技能分享 / 导出的公共入口（薄壳）。
 *
 * 通用 zip 分享/导出逻辑已下沉到 [FileShareExport]（core/ui，无技能语义），本类仅委托它，
 * 保留原有对外方法签名不变（shareText / shareZip / exportZip），使 settings 侧
 * [SettingsScreen] / [SkillsSection] 的既有引用点无需改动。
 *
 * 由 [rememberSkillShareExport] 创建；不要在多个 Composable 里各自 new，应经 remember 复用同一实例。
 */
internal class SkillShareExport(
    private val delegate: FileShareExport
) {
    /** 分享纯文本（单个技能 markdown），走系统分享面板；[chooserTitle] 为面板标题。 */
    fun shareText(text: String, chooserTitle: String) = delegate.shareText(text, chooserTitle)

    /**
     * 分享 zip 字节流：写进 cacheDir（FileProvider 的 cache-path 已覆盖），授权后拉系统分享。
     * [fileName] 仅作文件名展示，落盘前加时间戳避免同名互相覆盖。
     */
    fun shareZip(bytes: ByteArray, fileName: String, chooserTitle: String, onResult: (Boolean) -> Unit) =
        delegate.shareZip(bytes, fileName, chooserTitle, onResult)

    /** 用 SAF CreateDocument 导出 zip 字节流到用户选择的位置；[onResult] 报告是否成功。 */
    fun exportZip(bytes: ByteArray, suggestedName: String, onResult: (Boolean) -> Unit) =
        delegate.exportZip(bytes, suggestedName, onResult)
}

/**
 * 技能「⋯」菜单项的统一内容：图标 + 文字（遵循项目「溢出收纳 ⋯ 菜单」约定）。
 * 顶栏详情菜单与批量底部栏 moreActions 共用，保证两处菜单项样式一致。
 */
@Composable
internal fun SkillMenuRow(icon: ImageVector, text: String) {
    FileMenuRow(icon, text)
}

/**
 * 创建并记住一个 [SkillShareExport]（委托共享的 [rememberFileShareExport]，保持实例稳定）。
 */
@Composable
internal fun rememberSkillShareExport(): SkillShareExport {
    val delegate = rememberFileShareExport()
    return remember(delegate) { SkillShareExport(delegate) }
}
