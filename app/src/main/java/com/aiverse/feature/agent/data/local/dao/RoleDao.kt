package com.aiverse.feature.agent.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aiverse.feature.agent.data.local.entity.RoleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(role: RoleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(roles: List<RoleEntity>)

    @Update
    suspend fun update(role: RoleEntity)

    @Delete
    suspend fun delete(role: RoleEntity)

    @Query("DELETE FROM roles WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 批量删除（内置角色由调用方过滤，DAO 层不做限制）。 */
    @Query("DELETE FROM roles WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT * FROM roles ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<RoleEntity>>

    @Query("SELECT * FROM roles ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAllOnce(): List<RoleEntity>

    @Query("SELECT * FROM roles WHERE enabled = 1 ORDER BY sortOrder ASC, createdAt ASC")
    fun observeEnabled(): Flow<List<RoleEntity>>

    @Query("SELECT * FROM roles WHERE enabled = 1 ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getEnabledOnce(): List<RoleEntity>

    @Query("SELECT * FROM roles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RoleEntity?

    @Query("SELECT * FROM roles WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<RoleEntity?>

    /** 批量启停。 */
    @Query("UPDATE roles SET enabled = :enabled, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun setEnabled(ids: List<String>, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE roles SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabledById(id: String, enabled: Boolean, updatedAt: Long)

    /** 工作区被删除时清掉所有角色对它的绑定，回退为「跟随当前工作区」。 */
    @Query("UPDATE roles SET workspaceId = NULL, updatedAt = :updatedAt WHERE workspaceId = :workspaceName")
    suspend fun clearWorkspaceBinding(workspaceName: String, updatedAt: Long)
}
