package com.aiverse.feature.plugins.data

import com.aiverse.R
import android.content.Context
import com.aiverse.feature.plugins.domain.model.PluginManifest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** 已安装插件的运行时视图。 */
data class InstalledPlugin(
    val manifest: PluginManifest,
    val dir: File,
    val enabled: Boolean
)

/**
 * 脚本插件存储：zip 安装解压到应用私有目录 `files/openx/plugins/<id>/`、
 * 扫描已装列表、卸载、启用状态（SharedPreferences）。
 */
@Singleton
class PluginRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val pluginsDir = File(context.filesDir, "openx/plugins")
    private val prefs = context.getSharedPreferences("plugin_state", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /** 安装 zip 插件包，返回解析后的清单。校验失败抛异常。 */
    fun install(zipFile: File): PluginManifest {
        val tmp = File(context.cacheDir, "plugin_extract_${System.currentTimeMillis()}")
        try {
            extractZip(zipFile, tmp)
            val pluginJson = tmp.walkTopDown().find { it.name == "plugin.json" }
                ?: throw IllegalStateException(context.getString(R.string.plugin_install_no_plugin_json))
            val manifest = json.decodeFromString<PluginManifest>(pluginJson.readText())
            if (manifest.packageName.isBlank()) throw IllegalStateException(context.getString(R.string.plugin_install_missing_package))
            if (manifest.name.isBlank()) throw IllegalStateException(context.getString(R.string.plugin_install_missing_name))
            if (manifest.main.isBlank()) throw IllegalStateException(context.getString(R.string.plugin_install_missing_main))

            val pluginRoot = pluginJson.parentFile ?: throw IllegalStateException(context.getString(R.string.plugin_install_bad_dir))
            val target = File(pluginsDir, manifest.id)
            if (target.exists()) target.deleteRecursively()
            pluginsDir.mkdirs()
            if (!pluginRoot.renameTo(target)) {
                pluginRoot.copyRecursively(target, overwrite = true)
            }
            return manifest
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun extractZip(zipFile: File, dest: File) {
        dest.mkdirs()
        val base = dest.canonicalFile
        ZipArchiveInputStream(zipFile.inputStream()).use { zin ->
            var entry: ZipArchiveEntry? = zin.nextEntry
            while (entry != null) {
                val e = entry
                val target = File(dest, e.name).canonicalFile
                if (!target.path.startsWith(base.path + File.separator)) {
                    throw SecurityException("压缩包路径越界: ${e.name}")
                }
                if (e.isDirectory) target.mkdirs()
                else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { out -> zin.copyTo(out) }
                }
                entry = zin.nextEntry
            }
        }
    }

    /** 列出已安装插件（含启用状态），按名称排序。 */
    fun listInstalled(): List<InstalledPlugin> {
        val enabled = enabledIds()
        return pluginsDir.listFiles { f -> f.isDirectory }
            ?.mapNotNull { dir ->
                val pf = File(dir, "plugin.json")
                if (!pf.exists()) return@mapNotNull null
                val manifest = try {
                    json.decodeFromString<PluginManifest>(pf.readText())
                } catch (e: Exception) {
                    return@mapNotNull null
                }
                InstalledPlugin(manifest, dir, manifest.id in enabled)
            }
            ?.sortedBy { it.manifest.name }
            ?: emptyList()
    }

    fun getManifest(id: String): PluginManifest? {
        val pf = File(pluginsDir, "$id/plugin.json")
        if (!pf.exists()) return null
        return try {
            json.decodeFromString<PluginManifest>(pf.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun pluginDir(id: String): File = File(pluginsDir, id)

    /**
     * 收集插件目录下全部文件的（相对插件根路径, 字节）映射，用于分享/导出打包。
     * zip 内路径为插件根内相对路径（plugin.json 在 zip 根），这样导出的 zip 可直接再安装。
     * 目录不存在或读取失败返回空映射（调用方据此提示失败）。
     */
    fun collectPluginFiles(id: String): Map<String, ByteArray> {
        val root = pluginDir(id)
        if (!root.isDirectory) return emptyMap()
        return root.walkTopDown()
            .filter { it.isFile }
            .mapNotNull { file ->
                val rel = file.relativeTo(root).path
                runCatching { rel to file.readBytes() }.getOrNull()
            }
            .toMap()
    }

    fun uninstall(id: String) {
        val dir = File(pluginsDir, id)
        if (dir.exists()) dir.deleteRecursively()
        setEnabled(id, false)
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val set = enabledIds().toMutableSet()
        if (enabled) set.add(id) else set.remove(id)
        prefs.edit().putStringSet("enabled_plugins", set).apply()
    }

    fun enabledIds(): Set<String> =
        prefs.getStringSet("enabled_plugins", emptySet())?.toSet() ?: emptySet()

    /** 运行时已授权（用户「始终允许」）的插件权限，补充 manifest.permissions。 */
    fun approvedPermissions(id: String): Set<String> =
        prefs.getStringSet("approved_permissions_$id", emptySet())?.toSet() ?: emptySet()

    /** 记录插件权限为已授权（持久化，之后不再自动申请）。 */
    fun grantPermission(id: String, perm: String) {
        val set = approvedPermissions(id).toMutableSet().apply { add(perm) }
        prefs.edit().putStringSet("approved_permissions_$id", set).apply()
    }
}
