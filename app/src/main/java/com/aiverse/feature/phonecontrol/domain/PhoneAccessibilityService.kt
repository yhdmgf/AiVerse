package com.aiverse.feature.phonecontrol.domain

import com.aiverse.R
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.text.InputType
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 服务不主动采集；读屏由调用方按需触发。
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

    /** 读取当前屏幕的可交互元素列表（id 按遍历顺序编号，label 敏感信息已打码）。 */
    fun readScreenElements(): List<ScreenElement> {
        val root = rootInActiveWindow ?: return emptyList()
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectElements(root, nodes)
        return nodes.mapIndexed { index, node ->
            ScreenElement(
                id = index + 1,
                role = roleOf(node),
                label = maskSensitive(labelOf(node)),
                clickable = node.isClickable
            )
        }
    }

    /** 按元素 id 无障碍点击（与 [readScreenElements] 相同的遍历顺序编号）。 */
    fun clickElement(id: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collectElements(root, nodes)
        val node = nodes.getOrNull(id - 1) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun collectElements(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.isClickable || labelOf(node).isNotBlank()) out += node
        for (i in 0 until node.childCount) collectElements(node.getChild(i), out)
    }

    private fun labelOf(node: AccessibilityNodeInfo): String {
        if (node.isPassword) return ""
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        return node.viewIdResourceName?.substringAfterLast('/') ?: ""
    }

    private fun roleOf(node: AccessibilityNodeInfo): String {
        val cls = node.className?.toString()?.substringAfterLast('.')?.lowercase() ?: ""
        return when {
            node.isEditable -> getString(R.string.phone_node_editable)
            node.isScrollable -> getString(R.string.phone_node_scrollable)
            node.isClickable -> getString(R.string.phone_node_clickable)
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
