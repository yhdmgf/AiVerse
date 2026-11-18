package com.aiverse.feature.agent.domain.subagent

import com.aiverse.feature.workspace.domain.AgentConfigKind
import com.aiverse.feature.workspace.domain.AgentConfigParseCache
import com.aiverse.feature.workspace.domain.FileAccessProvider
import com.aiverse.feature.workspace.domain.WorkspacePathMapper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 项目级子代理定义来源：`<projectRoot>/.aiverse/agents/`，随工作区走，可 git 追踪。
 * 以容器路径经 [FileAccessProvider] 访问：本地映射到宿主工作区，远程经 SSH 落到远程工作区。
 *
 * 远程加速：有远程配置快照时一次批量读取全部顶层 `*.md`，并按快照指纹缓存解析结果；
 * 无快照（本地模式 / 远程后端未就绪）时回退到逐文件扫描。
 */
@Singleton
class ProjectDirectoryAgentSource @Inject constructor(
    private val fileAccess: FileAccessProvider
) : AgentDefinitionSource {

    val agentsRoot: String = "${WorkspacePathMapper.CONTAINER_ROOT}/.aiverse/agents"

    /** 解析缓存：按快照身份+修订号复用已解析的定义列表，避免每次重读重解析。 */
    private val snapshotCache = AgentConfigParseCache<List<AgentDefinition>>()

    override fun listDefinitions(): List<AgentDefinition> {
        val snapshot = runCatching { fileAccess.agentConfigSnapshot(agentsRoot, AgentConfigKind.AGENTS) }
            .getOrNull() ?: return AgentDefinitionDirectoryScanner.scan(fileAccess, agentsRoot)
        return snapshotCache.get(snapshot) { files -> AgentDefinitionDirectoryScanner.scan(agentsRoot, files) }
    }
}
