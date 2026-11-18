package com.aiverse.core.ui

import android.util.Log
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import com.aiverse.core.util.FileLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * 渲染兜底：把「测量 / 绘制」阶段抛出的异常收在当前子树里，不让它穿透到 Choreographer 帧回调
 * 把整个进程带崩。
 *
 * 背景：聊天页出现过 release 包（R8 混淆）在 ViewRootImpl.performTraversals → dispatchDraw
 * 期间抛 NoSuchElementException: List is empty. 的闪退，debug 包不复现。这类异常发生在
 * Compose 的 layout / draw 阶段，组合期的 try/catch 拦不住，只能在这一层拦。
 *
 * 命中时不会静默：异常连同完整堆栈写入 [FileLogger]（即 crash 报告里提到的 logs 目录），
 * 便于用同一次构建的 mapping.txt retrace 到源码行。
 */
object RenderErrorGuard {

    private const val TAG = "RenderErrorGuard"

    /** 同一 tag + 同一异常位置的重复上报去重，避免每帧刷屏。 */
    private val reported = ConcurrentHashMap<String, Int>()

    fun report(tag: String, throwable: Throwable) {
        val frame = throwable.stackTrace.firstOrNull()
            ?.let { "${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
            .orEmpty()
        val key = "$tag|$frame"
        val hits = reported.merge(key, 1) { a, b -> a + b } ?: 1
        if (hits > MAX_REPORTS_PER_SITE) return
        Log.e(TAG, "render failed at $tag: $frame", throwable)
        FileLogger.e(TAG, "render failed at $tag (hits=$hits): $frame", throwable)
    }

    private const val MAX_REPORTS_PER_SITE = 5
}

/**
 * 给子树加上「测量 + 绘制」双重异常兜底。
 *
 * 用法：`Modifier.guardRender("chat-markdown")`。建议只加在消息正文、卡片这类
 * 「内容不可控」的容器上，不要加在整屏根节点上——根节点一旦吞异常，页面会静默变空白。
 *
 * 注意：测量失败时该子树的尺寸按 0 处理（等价于这一块暂时不显示），其余 UI 正常。
 */
fun Modifier.guardRender(tag: String): Modifier = this
    .layout { measurable, constraints ->
        val placeable = try {
            measurable.measure(constraints)
        } catch (t: Throwable) {
            RenderErrorGuard.report("$tag/measure", t)
            null
        }
        if (placeable == null) {
            layout(0, 0) {}
        } else {
            layout(placeable.width, placeable.height) {
                placeable.place(0, 0)
            }
        }
    }
    .drawWithContent {
        try {
            drawContent()
        } catch (t: Throwable) {
            RenderErrorGuard.report("$tag/draw", t)
        }
    }
