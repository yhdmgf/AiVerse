package com.aiverse.feature.agent.domain.subagent

import com.aiverse.feature.workspace.domain.AgentConfigKind
import com.aiverse.feature.workspace.domain.AgentConfigParseCache
import com.aiverse.feature.workspace.domain.FileAccessProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局子代理定义来源：`~/.aiverse/agents`，跨项目共享。以容器路径经 [FileAccessProvider] 访问，
 * 跟随当前执行环境——本地模式落 App 私有 `filesDir/aiverse/agents`（容器内 `/root/.aiverse/agents`），
 * 远程模式经 SSH 落服务器用户 home 下的 `.aiverse/agents`。远程是独立执行环境，读写均以远端为准。
 *
 * 远程加速：有远程配置快照时一次批量读取全部顶层 `*.md`，并按快照指纹缓存解析结果；
 * 无快照（本地模式 / 远程后端未就绪）时回退到逐文件扫描。
 */
@Singleton
class GlobalDirectoryAgentSource @Inject constructor(
    private val fileAccess: FileAccessProvider
) : AgentDefinitionSource {

    val agentsRoot: String = "~/.aiverse/agents"

    /** 解析缓存：按快照身份+修订号复用已解析的定义列表，避免每次重读重解析。 */
    private val snapshotCache = AgentConfigParseCache<List<AgentDefinition>>()

    override fun listDefinitions(): List<AgentDefinition> {
        val snapshot = runCatching { fileAccess.agentConfigSnapshot(agentsRoot, AgentConfigKind.AGENTS) }
            .getOrNull() ?: return AgentDefinitionDirectoryScanner.scan(fileAccess, agentsRoot)
        return snapshotCache.get(snapshot) { files -> AgentDefinitionDirectoryScanner.scan(agentsRoot, files) }
    }
}
