package com.aiverse.feature.agent.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aiverse.feature.agent.data.local.dao.AgentMessageDao
import com.aiverse.feature.agent.data.local.dao.ChatSessionDao
import com.aiverse.feature.agent.data.local.dao.CheckpointDao
import com.aiverse.feature.agent.data.local.dao.LlmCallRecordDao
import com.aiverse.feature.agent.data.local.dao.TodoItemDao
import com.aiverse.feature.agent.data.local.entity.AgentMessageEntity
import com.aiverse.feature.agent.data.local.entity.ChatSessionEntity
import com.aiverse.feature.agent.data.local.entity.CheckpointEntity
import com.aiverse.feature.agent.data.local.entity.CheckpointFileSnapshotEntity
import com.aiverse.feature.agent.data.local.entity.LlmCallRecordEntity
import com.aiverse.feature.agent.data.local.entity.TodoItemEntity
import com.aiverse.feature.backup.data.local.dao.BackupTargetDao
import com.aiverse.feature.backup.data.local.entity.BackupTargetEntity
import com.aiverse.feature.settings.data.local.dao.AIProviderDao
import com.aiverse.feature.settings.data.local.entity.AIProviderEntity
import com.aiverse.feature.workspace.data.local.dao.RemoteConnectionDao
import com.aiverse.feature.workspace.data.local.entity.RemoteConnectionEntity
import com.aiverse.feature.workspace.data.local.entity.RemoteMountEntity

@Database(
    entities = [AgentMessageEntity::class, ChatSessionEntity::class, AIProviderEntity::class, RemoteConnectionEntity::class, RemoteMountEntity::class, TodoItemEntity::class, CheckpointEntity::class, CheckpointFileSnapshotEntity::class, LlmCallRecordEntity::class, BackupTargetEntity::class],
    version = AgentDatabase.SCHEMA_VERSION,
    exportSchema = true
)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun agentMessageDao(): AgentMessageDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun aiProviderDao(): AIProviderDao
    abstract fun remoteConnectionDao(): RemoteConnectionDao
    abstract fun todoItemDao(): TodoItemDao
    abstract fun checkpointDao(): CheckpointDao
    abstract fun llmCallRecordDao(): LlmCallRecordDao
    abstract fun backupTargetDao(): BackupTargetDao

    companion object {
        const val SCHEMA_VERSION = 63

        /** 数据库文件名（落在 `databases/` 下，另有 Room 默认 WAL 模式产生的 `-wal`/`-shm`）。 */
        const val DATABASE_NAME = "aiverse_agent_db"
    }
}
