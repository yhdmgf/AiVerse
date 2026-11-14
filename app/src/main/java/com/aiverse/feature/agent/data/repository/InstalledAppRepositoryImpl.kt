package com.aiverse.feature.agent.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.aiverse.feature.agent.domain.model.InstalledApp
import com.aiverse.feature.agent.domain.repository.InstalledAppRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 PackageManager 的已安装应用查询。
 * 包可见性靠 Manifest `<queries>` 声明 LAUNCHER intent-filter（不申请 QUERY_ALL_PACKAGES）；
 * 查询后自行排除系统应用与无 base APK 的无入口残包。
 */
class InstalledAppRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : InstalledAppRepository {

    override suspend fun listInstalledApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = runCatching {
            pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())

        resolveInfos.asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filter { appInfo ->
                appInfo.sourceDir != null &&
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            }
            .distinctBy { it.packageName }
            .map { appInfo -> appInfo.toInstalledApp(pm) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    override suspend fun loadIcon(packageName: String): Drawable? = withContext(Dispatchers.IO) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationInfo(packageName, 0).loadIcon(pm)
        }.getOrNull()
    }

    private fun ApplicationInfo.toInstalledApp(pm: PackageManager): InstalledApp {
        val splits = splitSourceDirs?.filterNotNull().orEmpty()
        val totalBytes = (listOfNotNull(sourceDir) + splits).sumOf { path ->
            runCatching { File(path).length() }.getOrDefault(0L)
        }
        return InstalledApp(
            packageName = packageName,
            label = runCatching { loadLabel(pm)?.toString().orEmpty() }.getOrDefault("")
                .ifBlank { packageName },
            sizeBytes = totalBytes,
            hasSplits = splits.isNotEmpty(),
            sourceDir = sourceDir,
            splitSourceDirs = splits
        )
    }
}
