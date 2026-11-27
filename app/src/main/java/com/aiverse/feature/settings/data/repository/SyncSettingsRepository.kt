package com.aiverse.feature.settings.data.repository

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)

    private val _ignoredPatterns = MutableStateFlow(
        prefs.getString("ignored_patterns", ".git,node_modules,build,dist,.gradle,.idea,.cxx,.vscode,tmp")!!
    )
    val ignoredPatterns: StateFlow<String> = _ignoredPatterns.asStateFlow()

    private val _useGitIgnore = MutableStateFlow(
        prefs.getBoolean("use_gitignore", true)
    )
    val useGitIgnore: StateFlow<Boolean> = _useGitIgnore.asStateFlow()

    private val _maxSyncBatchSize = MutableStateFlow(
        prefs.getInt("max_sync_batch_size", 5)
    )
    val maxSyncBatchSize: StateFlow<Int> = _maxSyncBatchSize.asStateFlow()

    private val _sshGuideSeen = MutableStateFlow(
        prefs.getBoolean("ssh_guide_seen", false)
    )
    val sshGuideSeen: StateFlow<Boolean> = _sshGuideSeen.asStateFlow()

    /** 勾选「在文件树显示」的 SFTP 连接 id 集合；默认不显示，由连接与同步页逐条开启。 */
    private val _fileTreeConnectionIds = MutableStateFlow(
        prefs.getStringSet("file_tree_connection_ids", emptySet()).orEmpty().toSet()
    )
    val fileTreeConnectionIds: StateFlow<Set<String>> = _fileTreeConnectionIds.asStateFlow()

    fun setFileTreeVisible(connectionId: String, visible: Boolean) {
        val updated = if (visible) {
            _fileTreeConnectionIds.value + connectionId
        } else {
            _fileTreeConnectionIds.value - connectionId
        }
        prefs.edit { putStringSet("file_tree_connection_ids", updated) }
        _fileTreeConnectionIds.value = updated
    }

    fun setIgnoredPatterns(patterns: String) {
        prefs.edit { putString("ignored_patterns", patterns) }
        _ignoredPatterns.value = patterns
    }

    fun setUseGitIgnore(use: Boolean) {
        prefs.edit { putBoolean("use_gitignore", use) }
        _useGitIgnore.value = use
    }

    fun setMaxSyncBatchSize(size: Int) {
        prefs.edit { putInt("max_sync_batch_size", size) }
        _maxSyncBatchSize.value = size
    }

    fun setSshGuideSeen(seen: Boolean) {
        prefs.edit { putBoolean("ssh_guide_seen", seen) }
        _sshGuideSeen.value = seen
    }

    /** 备份快照：同步偏好三键。 */
    fun snapshot(): SyncSettingsSnapshot = SyncSettingsSnapshot(
        ignoredPatterns = _ignoredPatterns.value,
        useGitIgnore = _useGitIgnore.value,
        maxSyncBatchSize = _maxSyncBatchSize.value
    )

    /** 从备份还原同步偏好。 */
    fun restore(snapshot: SyncSettingsSnapshot) {
        setIgnoredPatterns(snapshot.ignoredPatterns)
        setUseGitIgnore(snapshot.useGitIgnore)
        setMaxSyncBatchSize(snapshot.maxSyncBatchSize)
    }
}

/** 同步偏好的可序列化快照。 */
@kotlinx.serialization.Serializable
data class SyncSettingsSnapshot(
    val ignoredPatterns: String,
    val useGitIgnore: Boolean,
    val maxSyncBatchSize: Int
)
