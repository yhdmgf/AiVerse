package com.aiverse.feature.agent.domain.repository

import android.graphics.drawable.Drawable
import com.aiverse.feature.agent.domain.model.InstalledApp

/**
 * 已安装应用的查询与图标加载。图标按需懒加载，避免列表一次性解码全部图标。
 */
interface InstalledAppRepository {

    /** 查询所有含 LAUNCHER 入口且 base APK 可用的应用，按展示名排序。 */
    suspend fun listInstalledApps(): List<InstalledApp>

    /** 懒加载某应用的图标；失败或无图标返回 null（调用方预留占位）。 */
    suspend fun loadIcon(packageName: String): Drawable?
}
