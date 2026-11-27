package com.aiverse.feature.web

import com.aiverse.feature.agent.presentation.run.AgentRunManager
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Web 发消息桥：优先把网页端 POST 的消息转发给「当前注册的 AIAgentViewModel」，
 * 没有注册者（Activity 已销毁 / App 在后台只剩进程）时直接投给进程级 [AgentRunManager]。
 *
 * AIAgentViewModel 是 Activity 作用域的 Hilt ViewModel，WebServerManager 是 Singleton，
 * 两者生命周期不同。MainActivity 在 ViewModel 就绪后通过 [registerSender] 注册发送回调，
 * 这样网页端能复用完整 Agent 链路（斜杠命令、插件会话、落库、工具权限、流式输出）。
 *
 * 但网页服务器的定位是「关掉手机界面也能用」：用户从电脑浏览器发消息时，Activity 很可能
 * 已经被系统回收，不能因此返回 503。此时直接调用进程级运行器，由它自行落库、执行工具并
 * 把流式结果写入同一份 StateFlow，SSE 仍能推给网页端；唯一少掉的是 UI 层专属的斜杠命令/
 *
 * 插件会话分支，普通聊天消息不受影响。
 */
@Singleton
class WebMessageBridge @Inject constructor(
    private val agentRunManager: AgentRunManager
) {

    /** 注册的发送回调列表。参数：目标会话 id、消息内容；返回是否已受理。 */
    private val senders = CopyOnWriteArrayList<(sessionId: String, content: String) -> Boolean>()

    /**
     * 注册发送回调（MainActivity 在 ViewModel 就绪后调用）。
     * 返回一个解绑句柄，Activity 销毁时调用。
     */
    fun registerSender(sender: (sessionId: String, content: String) -> Boolean): () -> Unit {
        senders.add(sender)
        return { senders.remove(sender) }
    }

    /**
     * Web 路由调用：先投给最近注册的发送者；没有发送者或全都拒绝时，回退到进程级运行器。
     * @return true 已受理；false 连进程级运行器都投递失败。
     */
    fun submit(sessionId: String, content: String): Boolean {
        // 只投给最后一个注册者（通常只有 MainActivity 一个）；若它拒绝则尝试更早的。
        for (i in senders.indices.reversed()) {
            val accepted = runCatching { senders[i](sessionId, content) }.getOrDefault(false)
            if (accepted) return true
        }
        return runCatching {
            agentRunManager.start()
            agentRunManager.enqueueAgentRequest(
                request = content,
                targetSessionId = sessionId
            )
            true
        }.getOrDefault(false)
    }
}
