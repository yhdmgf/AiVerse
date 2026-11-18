package com.aiverse.feature.phonecontrol.domain

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.aiverse.core.util.FileLogger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 界面节点的不可变快照（复刻 Muse ScreenSnapshot）。
 *
 * 只保留定位所需字段（文本/描述/类名/边界/可点击/可输入），节点本身不跨线程保留，避免
 * AccessibilityNodeInfo 失效带来的崩溃。
 */
data class PhoneNodeSnapshot(
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val bounds: Rect,
    val clickable: Boolean,
    val editable: Boolean
) {
    /** 命中给定文本或描述（包含匹配、忽略大小写）。 */
    fun matches(query: String): Boolean =
        text?.contains(query, ignoreCase = true) == true ||
            contentDescription?.contains(query, ignoreCase = true) == true
}

/**
 * 常驻的界面快照。
 *
 * 无障碍事件到达时就地重建，点击时直接查这份快照——不用每次从 root 重新遍历整棵树，
 * 打开应用后立刻就能点。快照只存不可变数据（字符串、Rect），不持有 AccessibilityNodeInfo。
 * 纯 object 无外部依赖；服务实例由调用方作为参数传入，不与「instance 还是 WeakReference」耦合。
 */
object PhoneScreenSnapshot {

    private const val TAG = "PhoneScreenSnapshot"

    /** 重建节流：事件可能连续几十条，太频繁重建反而抢主线程。 */
    private const val REBUILD_INTERVAL_MS = 250L

    private const val MAX_NODES = 800

    private const val MAX_DEPTH = 30

    private const val MAX_TEXT_LENGTH = 120

    @Volatile
    private var nodes: List<PhoneNodeSnapshot> = emptyList()

    @Volatile
    var packageName: String? = null
        private set

    @Volatile
    var updatedAt: Long = 0L
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val pending = AtomicBoolean(false)
    private var lastRebuild = 0L

    /** 当前快照里的节点（只读）。 */
    fun nodes(): List<PhoneNodeSnapshot> = nodes

    fun isEmpty(): Boolean = nodes.isEmpty()

    /** 事件驱动的重建请求，带节流。 */
    fun requestRebuild(service: AccessibilityService) {
        val now = System.currentTimeMillis()
        if (now - lastRebuild < REBUILD_INTERVAL_MS) return
        if (!pending.compareAndSet(false, true)) return
        handler.postDelayed({
            pending.set(false)
            rebuild(service)
        }, REBUILD_INTERVAL_MS)
    }

    /** 立即重建（点击未命中、读屏时调用）。 */
    fun rebuildNow(service: AccessibilityService) {
        rebuild(service)
    }

    private fun rebuild(service: AccessibilityService) {
        val root = runCatching { service.rootInActiveWindow }.getOrNull() ?: return
        val collected = ArrayList<PhoneNodeSnapshot>(128)
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.add(root to 0)
        var visited = 0
        try {
            while (stack.isNotEmpty() && visited < MAX_NODES) {
                val (node, depth) = stack.removeLast()
                visited++
                val text = node.text?.toString()?.take(MAX_TEXT_LENGTH)
                val desc = node.contentDescription?.toString()?.take(MAX_TEXT_LENGTH)
                val clickable = node.isClickable
                val editable = node.isEditable
                if ((!text.isNullOrBlank() || !desc.isNullOrBlank() || clickable || editable) &&
                    node.isVisibleToUser
                ) {
                    val bounds = Rect().also { node.getBoundsInScreen(it) }
                    if (!bounds.isEmpty) {
                        collected.add(
                            PhoneNodeSnapshot(
                                text = text,
                                contentDescription = desc,
                                className = node.className?.toString(),
                                bounds = bounds,
                                clickable = clickable,
                                editable = editable
                            )
                        )
                    }
                }
                if (depth < MAX_DEPTH) {
                    for (i in node.childCount - 1 downTo 0) {
                        node.getChild(i)?.let { stack.add(it to depth + 1) }
                    }
                }
            }
            nodes = collected
            packageName = root.packageName?.toString()
            updatedAt = System.currentTimeMillis()
        } catch (e: Exception) {
            FileLogger.w(TAG, "重建界面快照失败", e)
        } finally {
            runCatching { root.recycle() }
        }
    }

    /** 按文本或描述查快照：先找完全相等（最准），再退回包含匹配。 */
    fun find(query: String): PhoneNodeSnapshot? {
        val current = nodes
        current.firstOrNull {
            it.text.equals(query, ignoreCase = true) ||
                it.contentDescription.equals(query, ignoreCase = true)
        }?.let { return it }
        return current.firstOrNull { it.matches(query) }
    }

    /** 按文本、描述或二者之一查快照（任一非空即参与匹配）。 */
    fun find(text: String?, contentDescription: String?): PhoneNodeSnapshot? {
        val query = text?.takeIf { it.isNotBlank() } ?: contentDescription?.takeIf { it.isNotBlank() }
        return query?.let { find(it) }
    }

    fun findEditable(): PhoneNodeSnapshot? = nodes.firstOrNull { it.editable }
}
