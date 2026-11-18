package com.aiverse.feature.agent.domain.tool.mcp

import com.aiverse.feature.agent.domain.mcp.McpManager
import com.aiverse.feature.agent.domain.tool.AgentTool
import com.aiverse.feature.agent.domain.tool.ParameterType
import com.aiverse.feature.agent.domain.tool.PendingToolPermission
import com.aiverse.feature.agent.domain.tool.ToolCapability
import com.aiverse.feature.agent.domain.tool.ToolParameter
import com.aiverse.feature.agent.domain.tool.ToolPermissionPolicy
import com.aiverse.feature.agent.domain.tool.ToolResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject

/**
 * MCP 工具按需加载（实验）开启时的元工具：搜索已连接 MCP server 提供的工具。
 *
 * 开启按需加载后，MCP 工具不再逐个注册进 [com.aiverse.feature.agent.domain.tool.ToolRegistry]，
 * 模型侧只看到 [McpSearchTool] / [McpCallTool] 两个入口。先用本工具按关键字找到目标工具
 * （返回 `name` / `description` / `inputSchema`），再交给 [McpCallTool] 调用。
 * 与 [McpCallTool] 一样始终暴露、顺序固定，开关关闭时也注册（关闭时 MCP 工具本身全量注册，
 * 这两个入口只是冗余但无害）。
 */
class McpSearchTool @Inject constructor(
    // Lazy 断开 McpManager → ToolRegistry → 本工具 → McpManager 的依赖环：
    // 本工具只在 execute() 运行时才访问 McpManager，无需构造期注入。
    private val mcpManager: dagger.Lazy<McpManager>
) : AgentTool() {

    override val name = "mcp_search"
    override val description =
        "搜索已连接的 Model Context Protocol (MCP) 服务器提供的工具，返回每个工具的 name、description 与 inputSchema。" +
            "配合 mcp_call 使用：先用本工具拿到工具的完整名（如 mcp__server__tool）与参数 schema，再用 mcp_call 调用。" +
            "query 留空时列出全部可用 MCP 工具。"
    override val permissionPolicy = ToolPermissionPolicy.AUTO_APPROVE
    override val capabilities = setOf(ToolCapability.READ_AGENT_CONFIG)

    override val parameters: Map<String, ToolParameter> = mapOf(
        "query" to ToolParameter(
            name = "query",
            type = ParameterType.STRING,
            description = "搜索关键字，对工具名与描述做不区分大小写的子串匹配；留空或省略时列出全部已连接 MCP 工具",
            required = false
        )
    )

    override fun buildPermissionRequest(
        callId: String,
        args: Map<String, JsonElement>,
        argsPreview: String
    ): PendingToolPermission {
        val query = args["query"]?.jsonPrimitive?.contentOrNull
        return PendingToolPermission(
            id = callId,
            toolName = name,
            title = "确认搜索 MCP 工具",
            summary = if (query.isNullOrBlank()) "列出全部已连接 MCP 工具" else "搜索 MCP 工具：$query",
            details = "该操作只读取已连接 MCP 服务器的工具清单，不会调用任何工具、不修改配置。",
            argsPreview = argsPreview
        )
    }

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val query = args["query"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        return try {
            val tools = mcpManager.get().searchConnectedTools(query)
            val result = buildJsonObject {
                put("count", tools.size)
                if (query.isNotEmpty()) put("query", query)
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        add(
                            buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("inputSchema", tool.inputSchema)
                            }
                        )
                    }
                }
            }
            ToolResult.Success(result)
        } catch (e: Exception) {
            ToolResult.Error("搜索 MCP 工具失败：${e.message}")
        }
    }
}
