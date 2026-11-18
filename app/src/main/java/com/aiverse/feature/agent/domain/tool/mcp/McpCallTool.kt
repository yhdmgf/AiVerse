package com.aiverse.feature.agent.domain.tool.mcp

import com.aiverse.core.util.FileLogger
import com.aiverse.feature.agent.domain.mcp.McpManager
import com.aiverse.feature.agent.domain.mcp.McpTool
import com.aiverse.feature.agent.domain.tool.AgentTool
import com.aiverse.feature.agent.domain.tool.ParameterType
import com.aiverse.feature.agent.domain.tool.PendingToolPermission
import com.aiverse.feature.agent.domain.tool.ToolCapability
import com.aiverse.feature.agent.domain.tool.ToolParameter
import com.aiverse.feature.agent.domain.tool.ToolPermissionPolicy
import com.aiverse.feature.agent.domain.tool.ToolResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/**
 * MCP 工具按需加载（实验）开启时的元工具：按名调用已连接 MCP server 上的工具。
 *
 * `name` 必须是 [McpSearchTool] 返回的完整命名空间名（`mcp__server__tool`），
 * `arguments` 按该工具 `inputSchema` 传入的对象。内部通过 [McpManager] 反查
 * 「name → (client, descriptor)」后复用 [McpTool.execute]，与开关关闭时的调用路径完全一致。
 * 与 [McpSearchTool] 一样始终暴露、顺序固定，开关关闭时也注册。
 */
class McpCallTool @Inject constructor(
    // Lazy 断开 McpManager → ToolRegistry → 本工具 → McpManager 的依赖环：
    // 本工具只在 execute() 运行时才访问 McpManager，无需构造期注入。
    private val mcpManager: dagger.Lazy<McpManager>
) : AgentTool() {

    private companion object {
        const val TAG = "McpCallTool"
    }

    override val name = "mcp_call"
    override val description =
        "调用一个 Model Context Protocol (MCP) 工具。name 用 mcp_search 返回的完整名（如 mcp__server__tool），" +
            "arguments 按该工具 inputSchema 描述的对象传参（无参数时传空对象 {}）。不存在的 name 会返回错误。" +
            "MCP 工具较多且开启「按需加载」时，用 mcp_search 先查再到本工具调用。"
    override val permissionPolicy = ToolPermissionPolicy.AUTO_APPROVE
    override val capabilities = setOf(ToolCapability.EXTERNAL_TOOL)

    override val parameters: Map<String, ToolParameter> = mapOf(
        "name" to ToolParameter(
            name = "name",
            type = ParameterType.STRING,
            description = "要调用的 MCP 工具完整名，来自 mcp_search 返回的 name 字段，形如 mcp__server__tool"
        ),
        "arguments" to ToolParameter(
            name = "arguments",
            type = ParameterType.OBJECT,
            description = "调用参数对象，字段与取值按目标工具的 inputSchema 传；工具无参数时传 {}",
            required = false
        )
    )

    override fun buildPermissionRequest(
        callId: String,
        args: Map<String, JsonElement>,
        argsPreview: String
    ): PendingToolPermission {
        val target = args["name"]?.jsonPrimitive?.contentOrNull ?: "未指定"
        return PendingToolPermission(
            id = callId,
            toolName = name,
            title = "确认调用 MCP 工具",
            summary = "AI 请求通过 mcp_call 调用 MCP 工具「$target」",
            details = "MCP 工具：$target\n参数：$argsPreview\n与直接调用该 MCP 工具的授权策略一致（默认自动放行，可在工具授权里加 DENY 规则）。",
            argsPreview = argsPreview
        )
    }

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val toolName = args["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (toolName.isEmpty()) return ToolResult.Error("缺少必需参数：name")
        val callArgs = args["arguments"]?.jsonObject ?: JsonObject(emptyMap())

        return try {
            val resolved = mcpManager.get().resolveConnectedTool(toolName)
                ?: return ToolResult.Error("未找到 MCP 工具：$toolName（先用 mcp_search 查询已连接服务器提供的工具名）")
            val (client, descriptor) = resolved
            // 复用 McpTool 的执行与错误处理路径：命名空间名、原始 schema、tools/call 一致。
            val result = McpTool(client, descriptor).execute(callArgs)
            if (result is ToolResult.Error) {
                FileLogger.e(TAG, "mcp_call 调用失败: $toolName -> ${result.message}")
            }
            result
        } catch (e: Exception) {
            FileLogger.e(TAG, "mcp_call 执行异常: $toolName", e)
            ToolResult.Error("mcp_call 执行异常：${e.message}")
        }
    }
}
