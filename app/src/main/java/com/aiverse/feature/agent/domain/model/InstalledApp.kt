package com.aiverse.feature.agent.domain.model

/**
 * 设备上已安装（含 LAUNCHER 入口）的应用快照，供「上传应用」附件菜单展示与导出。
 *
 * @param packageName 包名，唯一标识。
 * @param label 展示名（应用名），已做非空兜底。
 * @param sizeBytes 导出体积：单 APK 为 base 体积；含分包时为 base + 全部 split 之和。
 * @param hasSplits 是否含 split APK；为 true 时导出为 `.apks`(zip) 完整包。
 * @param sourceDir base APK 宿主路径；为 null 表示无可用入口残包（查询时已排除）。
 * @param splitSourceDirs 全部分包路径（不含 base）。
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val sizeBytes: Long,
    val hasSplits: Boolean,
    val sourceDir: String?,
    val splitSourceDirs: List<String>
)
