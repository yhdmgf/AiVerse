package com.aiverse.feature.phonecontrol.domain

import com.aiverse.R
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.text.InputType
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aiverse.core.util.FileLogger
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 手机控制无障碍服务：读取当前屏幕（窗口树→文本）并模拟点击/滑动/输入。
 *
 * 取代原「截图 + 模拟点击」方式；读屏用窗口树（无需截图权限），点击用 [dispatchGesture]。
 * 服务开关状态由 [A11yKeeper] 管理；本类只在服务已开启时被实例化（系统绑定）。
 */
class PhoneAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: PhoneAccessibilityService? = null

        /** 当前运行中的服务实例；未开启时返回 null。 */
        fun current(): PhoneAccessibilityService? = instance

        /** 服务是否在运行。 */
        val isRunning: Boolean get() = instance != null

        /** 最近一次无障碍事件上报的前台应用包名（供打开应用后等待前台）。 */
        @Volatile
        var foregroundPackage: String? = null

        private const val TAG = "PhoneA11yService"

        /** 快照最大可信时长：超过则先重建再查，避免按过期坐标点到别的控件。 */
        private const val SNAPSHOT_MAX_AGE_MS = 2_000L

        /** 实时遍历找节点的最大访问节点数。 */
        private const val MAX_SEARCH_NODES = 600

        /** 向上回溯可点击祖先的最大层数。 */
        private const val CLICK_ANCESTOR_MAX_DEPTH = 6
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // 运行时再叠加一次检索标志，双保险：即便 xml 缺 flag 也能取到交互窗口与不重要视图。
        runCatching {
            serviceInfo = serviceInfo.apply {
                flags = flags or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
        }
        PhoneScreenSnapshot.rebuildNow(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 记录前台应用并在界面变化时刷新常驻快照，点击时直接查快照，不用每次重遍历整棵树。
        val evt = event ?: return
        evt.packageName?.toString()?.takeIf { it.isNotBlank() }?.let { foregroundPackage = it }
        when (evt.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> PhoneScreenSnapshot.requestRebuild(this)
        }
    }

    override fun onInterrupt() {
        // 无持续动作，无需处理。
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** 屏幕上的可交互元素（仿 dsh：带 id 供 AI 按文本定位后无障碍点击，不靠截图坐标）。 */
    data class ScreenElement(
        val id: Int,
        val role: String,
        val label: String,
        val clickable: Boolean
    )

    /** 读取当前屏幕的可交互元素列表（基于常驻快照；id 按快照遍历顺序编号，label 敏感信息已打码）。 */
    fun readScreenElements(): List<ScreenElement> {
        rebuildSnapshotIfStale()
        return PhoneScreenSnapshot.nodes().mapIndexed { index, node ->
            ScreenElement(
                id = index + 1,
                role = roleOf(node.className, node.editable, node.clickable),
                label = maskSensitive(labelOf(node.text, node.contentDescription, node.className)),
                clickable = node.clickable
            )
        }
    }

    /** 按元素 id 点击：先查快照边界发中心点手势，失败退回实时节点无障碍 ACTION_CLICK。 */
    suspend fun clickElement(id: Int): Boolean {
        rebuildSnapshotIfStale()
        val hit = PhoneScreenSnapshot.nodes().getOrNull(id - 1)
        if (hit != null && hit.clickable) {
            val cx = hit.bounds.centerX().toFloat()
            val cy = hit.bounds.centerY().toFloat()
            if (runCatching { tap(cx, cy) }.getOrDefault(false)) return true
        }
        // 手势没成（或快照里无该编号、节点不可点）：按遍历顺序实时遍历一次，回溯可点击祖先发 ACTION_CLICK。
        val root = rootInActiveWindow ?: return false
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectElements(root, nodes)
        val node = nodes.getOrNull(id - 1) ?: return false
        return clickNodeOrAncestor(node)
    }

    /**
     * 按文本/描述点击（复刻 Muse click 三段式）。
     *
     * ① 查常驻快照，命中且边界未过期则发中心点手势；② 实时遍历找节点，向上回溯可点击祖先发
     * ACTION_CLICK；③ 都不成再对实时节点发中心点手势兜底。三条路都失败返回 false。
     */
    suspend fun clickByText(text: String?, contentDescription: String?): Boolean {
        if (text.isNullOrBlank() && contentDescription.isNullOrBlank()) return false
        val query = text?.takeIf { it.isNotBlank() } ?: contentDescription.orEmpty()
        rebuildSnapshotIfStale()

        // ① 快照命中：无障碍事件到达时已建好，这里只是一次线性扫描，打开应用后能立刻点。
        val snapshotHit = PhoneScreenSnapshot.find(query)
        if (snapshotHit != null && snapshotHit.bounds.width() > 0) {
            val cx = snapshotHit.bounds.centerX().toFloat()
            val cy = snapshotHit.bounds.centerY().toFloat()
            if (tap(cx, cy)) return true
        }

        // ② 快照没有或点不动：实时遍历一次（界面刚变化、快照还没跟上时走这里）。
        val root = rootInActiveWindow ?: return false
        val node = findNodeBfs(root, text, contentDescription) ?: return false
        if (clickNodeOrAncestor(node)) return true

        // ③ 坐标兜底：祖先不可点时，对实时节点自身发中心点手势。
        val bounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }
        if (bounds.isEmpty) return false
        return tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    /** 快照过期（>2s 未更新）或为空时立即重建，避免按过期坐标点到别的控件。 */
    private fun rebuildSnapshotIfStale() {
        if (PhoneScreenSnapshot.isEmpty() ||
            System.currentTimeMillis() - PhoneScreenSnapshot.updatedAt > SNAPSHOT_MAX_AGE_MS
        ) {
            PhoneScreenSnapshot.rebuildNow(this)
        }
    }

    /** 点击节点；节点本身不可点时向上找可点击祖先（很多列表项的可点击区在父层）。 */
    private fun clickNodeOrAncestor(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < CLICK_ANCESTOR_MAX_DEPTH) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            depth++
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findNodeBfs(
        root: AccessibilityNodeInfo,
        text: String?,
        contentDescription: String?
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        try {
            while (queue.isNotEmpty() && visited < MAX_SEARCH_NODES) {
                val node = queue.removeFirst()
                visited++
                val nodeText = node.text?.toString()
                val nodeDesc = node.contentDescription?.toString()
                val textHit = !text.isNullOrBlank() && nodeText != null &&
                    nodeText.contains(text, ignoreCase = true)
                val descHit = !contentDescription.isNullOrBlank() && nodeDesc != null &&
                    nodeDesc.contains(contentDescription, ignoreCase = true)
                if ((textHit || descHit) && node.isVisibleToUser) return node
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
        } catch (e: Exception) {
            FileLogger.w(TAG, "按文本查找节点失败", e)
        }
        return null
    }

    private fun collectElements(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.isClickable || labelOf(node).isNotBlank()) out += node
        for (i in 0 until node.childCount) collectElements(node.getChild(i), out)
    }

    private fun labelOf(node: AccessibilityNodeInfo): String {
        if (node.isPassword) return ""
        return labelOf(
            node.text?.toString(),
            node.contentDescription?.toString(),
            node.className?.toString()
        ).ifEmpty { node.viewIdResourceName?.substringAfterLast('/') ?: "" }
    }

    private fun labelOf(text: String?, desc: String?, className: String?): String {
        text?.takeIf { it.isNotBlank() }?.let { return it }
        desc?.takeIf { it.isNotBlank() }?.let { return it }
        return className?.substringAfterLast('.') ?: ""
    }

    private fun roleOf(className: String?, editable: Boolean, clickable: Boolean): String {
        val cls = className?.substringAfterLast('.')?.lowercase() ?: ""
        return when {
            editable -> getString(R.string.phone_node_editable)
            clickable -> getString(R.string.phone_node_clickable)
            else -> cls.ifEmpty { getString(R.string.phone_node_default) }
        }
    }

    /** 读取当前屏幕文本（窗口树递归，密码框跳过，手机号/银行卡打码）。 */
    fun readScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val builder = StringBuilder()
        collectText(root, builder)
        return maskSensitive(builder.toString().trim())
    }

    /** 读取屏幕文本，并返回窗口树节点数（供「试一下」展示）。 */
    fun readScreenTextWithNodeCount(): Pair<String, Int> {
        val root = rootInActiveWindow ?: return "" to 0
        var count = 0
        val builder = StringBuilder()
        collectText(root, builder, onNode = { count++ })
        return maskSensitive(builder.toString().trim()) to count
    }

    private fun collectText(
        node: AccessibilityNodeInfo?,
        out: StringBuilder,
        onNode: () -> Unit = {}
    ) {
        if (node == null) return
        onNode()
        val isPassword = (node.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 ||
            (node.inputType and InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) != 0 ||
            node.isPassword
        if (!isPassword) {
            val text = node.text?.toString()
            val desc = node.contentDescription?.toString()
            when {
                !text.isNullOrBlank() -> out.append(text).append('\n')
                !desc.isNullOrBlank() -> out.append(desc).append('\n')
            }
        }
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), out, onNode)
        }
    }

    /** 手机号/银行卡等敏感信息打码（避免完整读给模型）。 */
    private fun maskSensitive(text: String): String {
        val maskedPhone = Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)").replace(text) { m ->
            m.value.take(3) + "****" + m.value.takeLast(4)
        }
        return Regex("(?<!\\d)\\d{16,19}(?!\\d)").replace(maskedPhone) { m ->
            m.value.take(4) + "**********" + m.value.takeLast(4)
        }
    }

    /** 点击屏幕坐标（无障碍手势）。 */
    suspend fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        return dispatch(GestureDescription.Builder().addStroke(stroke).build())
    }

    /** 滑动。 */
    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return dispatch(GestureDescription.Builder().addStroke(stroke).build())
    }

    /** 长按。 */
    suspend fun longPress(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 700)
        return dispatch(GestureDescription.Builder().addStroke(stroke).build())
    }

    /** 对焦点输入框设置文本。 */
    suspend fun typeText(text: String): Boolean {
        val focused = findFocusedNode() ?: return false
        val bundle = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
    }

    private fun findFocusedNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        return focused ?: findEditable(root)
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            findEditable(node.getChild(i))?.let { return it }
        }
        return null
    }

    /** 全局动作：返回 / 主页 / 最近任务 / 通知栏。 */
    fun performGlobal(action: Int): Boolean = performGlobalAction(action)

    /** 打开应用（在无障碍服务上下文用启动 Intent；服务需开启）。 */
    fun openApp(packageName: String): Boolean {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(launch)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 点击一个节点（按文本/描述匹配的第一个可见节点）。 */
    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findNode(root, text)
    }

    private fun findNode(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString() == text || node.contentDescription?.toString() == text) return node
        for (i in 0 until node.childCount) {
            findNode(node.getChild(i), text)?.let { return it }
        }
        return null
    }

    /** 对指定节点执行点击动作（无障碍 action）。 */
    fun clickNode(node: AccessibilityNodeInfo?): Boolean =
        node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false

    private suspend fun dispatch(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { continuation ->
            val ok = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
                null
            )
            if (!ok && continuation.isActive) continuation.resume(false)
        }
}
