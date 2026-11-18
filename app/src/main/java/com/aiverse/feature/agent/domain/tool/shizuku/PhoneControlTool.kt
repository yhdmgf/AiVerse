package com.aiverse.feature.agent.domain.tool.shizuku

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.ApplicationInfo
import com.aiverse.R
import com.aiverse.core.util.FileLogger
import com.aiverse.feature.agent.domain.tool.AgentTool
import com.aiverse.feature.agent.domain.tool.ParameterType
import com.aiverse.feature.agent.domain.tool.PendingToolPermission
import com.aiverse.feature.agent.domain.tool.ToolCapability
import com.aiverse.feature.agent.domain.tool.ToolParameter
import com.aiverse.feature.agent.domain.tool.ToolPermissionPolicy
import com.aiverse.feature.agent.domain.tool.ToolResult
import com.aiverse.feature.phonecontrol.domain.PhoneAccessibilityService
import com.aiverse.feature.phonecontrol.domain.PhoneControlStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 通过无障碍服务控制宿主手机的工具：读屏、打开应用、点击/滑动/输入。
 *
 * 取代原「Shizuku 截图 + 模拟点击」工具链：读屏用无障碍窗口树（文本），点击用
 * dispatchGesture，打开应用在无障碍服务上下文 launchIntent。需先开启手机控制的无障碍
 * 服务（PhoneAccessibilityService），无需 Shizuku 授权。
 *
 * 属超高危险指令：即使授权页开启了「禁用安全拦截」或「一键允许所有工具」，权限引擎仍
 * 强制逐次确认（见 ToolPermissionPolicyEngine）。
 */
class PhoneControlTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val status: PhoneControlStatus
) : AgentTool() {
    private companion object {
        const val TAG = "PhoneControlTool"
        const val ACTION_OPEN_APP = "open_app"
        const val ACTION_READ_SCREEN = "read_screen"
        const val ACTION_CLICK_ELEMENT = "click_element"
        const val ACTION_CLICK_TEXT = "click_text"
        const val ACTION_TAP = "tap"
        const val ACTION_SWIPE = "swipe"
        const val ACTION_INPUT_TEXT = "input_text"
        const val ACTION_KEY_EVENT = "key_event"
        const val ACTION_LONG_PRESS = "long_press"
        const val ACTION_LIST_APPS = "list_apps"

        /** 应用包名白名单字符：防注入。 */
        private val PACKAGE_NAME_REGEX = Regex("[a-zA-Z0-9._]+")

        /** input_text 文本中允许的字符。 */
        private val SAFE_TEXT_REGEX = Regex("[^;&|`$\\\\]+")

        /** key_event 支持的按键 → 无障碍全局动作。 */
        private val GLOBAL_ACTIONS = mapOf(
            "BACK" to AccessibilityService.GLOBAL_ACTION_BACK,
            "HOME" to AccessibilityService.GLOBAL_ACTION_HOME,
            "RECENT" to AccessibilityService.GLOBAL_ACTION_RECENTS,
            "NOTIFICATIONS" to AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
            "QUICK_SETTINGS" to AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        )

        /** open_app 后等目标应用到前台的最长时长。 */
        private const val OPEN_APP_WAIT_MS = 2_000L

        /** 等前台的轮询间隔。 */
        private const val FOREGROUND_POLL_MS = 50L
    }

    override val name = "PhoneControl"

    override val description =
        "通过无障碍服务控制宿主手机：读屏（read_screen，返回当前屏幕可交互元素列表，如 `[1] 按钮 点赞`，AI 按文本定位元素 id）、点击元素（click_element，按 id 点击，不靠截图坐标）、按文本点击（click_text，按可见文本或 content_description 模糊定位后点击，图标按钮常只有描述）、打开应用（open_app，会等目标应用真正到前台再返回）、点击坐标（tap）、滑动（swipe）、长按（long_press）、输入文字（input_text）、按键（key_event）、列出已安装应用（list_apps）。" +
            "需先开启手机控制的无障碍服务，无需 Shizuku 授权。超高危险操作，每次执行都需用户确认。"

    override val permissionPolicy = ToolPermissionPolicy.ASK
    override val capabilities = setOf(ToolCapability.EXECUTE_COMMANDS)

    override val parameters: Map<String, ToolParameter> = mapOf(
        "action" to ToolParameter(
            name = "action",
            type = ParameterType.STRING,
            description = "操作类型：open_app 打开应用（会等前台） / read_screen 读屏（返回可交互元素列表） / click_element 点击元素（按 id） / click_text 按文本或描述点击 / tap 点击坐标 / swipe 滑动 / long_press 长按 / input_text 输入文字 / key_event 按键 / list_apps 列出已安装应用",
            required = true,
            enum = listOf(ACTION_OPEN_APP, ACTION_READ_SCREEN, ACTION_CLICK_ELEMENT, ACTION_CLICK_TEXT, ACTION_TAP, ACTION_SWIPE, ACTION_LONG_PRESS, ACTION_INPUT_TEXT, ACTION_KEY_EVENT, ACTION_LIST_APPS)
        ),
        "element_id" to ToolParameter(
            name = "element_id",
            type = ParameterType.INTEGER,
            description = "click_element 时必填：元素 id（来自 read_screen 返回的 [id]）",
            required = false
        ),
        "package_name" to ToolParameter(
            name = "package_name",
            type = ParameterType.STRING,
            description = "open_app 时必填：要打开的应用包名（如 com.tencent.mm）",
            required = false
        ),
        "x" to ToolParameter(
            name = "x",
            type = ParameterType.INTEGER,
            description = "tap/long_press 时必填：屏幕 x 坐标；swipe 时为起点 x",
            required = false
        ),
        "y" to ToolParameter(
            name = "y",
            type = ParameterType.INTEGER,
            description = "tap/long_press 时必填：屏幕 y 坐标；swipe 时为起点 y",
            required = false
        ),
        "x2" to ToolParameter(
            name = "x2",
            type = ParameterType.INTEGER,
            description = "swipe 时必填：终点 x 坐标",
            required = false
        ),
        "y2" to ToolParameter(
            name = "y2",
            type = ParameterType.INTEGER,
            description = "swipe 时必填：终点 y 坐标",
            required = false
        ),
        "duration" to ToolParameter(
            name = "duration",
            type = ParameterType.INTEGER,
            description = "swipe 滑动时长（毫秒），默认 300；long_press 长按时长（毫秒），默认 1000",
            required = false
        ),
        "text" to ToolParameter(
            name = "text",
            type = ParameterType.STRING,
            description = "input_text 时必填：要输入的文字（不支持特殊字符 ;&|`$\\\\）；click_text 时为要点击的可见文本（模糊匹配）",
            required = false
        ),
        "content_description" to ToolParameter(
            name = "content_description",
            type = ParameterType.STRING,
            description = "click_text 时可选：元素的 content_description（图标按钮常只有描述、没有文字），text 与此至少给一个",
            required = false
        ),
        "keycode" to ToolParameter(
            name = "keycode",
            type = ParameterType.STRING,
            description = "key_event 时必填：按键名，支持 BACK/HOME/RECENT/NOTIFICATIONS/QUICK_SETTINGS",
            required = false
        ),
        "third_party_only" to ToolParameter(
            name = "third_party_only",
            type = ParameterType.BOOLEAN,
            description = "list_apps 时可选：仅列出第三方应用（默认 true）",
            required = false
        ),
        "timeout" to ToolParameter(
            name = "timeout",
            type = ParameterType.INTEGER,
            description = "兼容参数（无障碍操作即时完成，该值不生效）。",
            required = false
        )
    )

    override fun buildPermissionRequest(
        callId: String,
        args: Map<String, JsonElement>,
        argsPreview: String
    ): PendingToolPermission {
        val action = args["action"]?.jsonPrimitive?.contentOrNull ?: ""
        val detail = when (action) {
            ACTION_OPEN_APP ->
                "打开应用：${args["package_name"]?.jsonPrimitive?.contentOrNull ?: "（未指定）"}"
            ACTION_READ_SCREEN -> "读屏：读取当前屏幕可交互元素列表（无障碍窗口树）"
            ACTION_CLICK_ELEMENT ->
                "点击元素：${args["element_id"]?.jsonPrimitive?.intOrNull ?: "?"}（按 id 点击）"
            ACTION_CLICK_TEXT -> {
                val text = args["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val desc = args["content_description"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val query = when {
                    text.isNotBlank() && desc.isNotBlank() ->
                        context.getString(R.string.phone_action_click_text_query, text, desc)
                    else -> text.ifBlank { desc }.ifBlank { "（未指定）" }
                }
                context.getString(R.string.phone_action_click_text_summary, query)
            }
            ACTION_TAP ->
                "点击：(${args["x"]?.jsonPrimitive?.intOrNull ?: "?"} , ${args["y"]?.jsonPrimitive?.intOrNull ?: "?"})"
            ACTION_SWIPE ->
                "滑动：(${args["x"]?.jsonPrimitive?.intOrNull ?: "?"},${args["y"]?.jsonPrimitive?.intOrNull ?: "?"}) → (${args["x2"]?.jsonPrimitive?.intOrNull ?: "?"},${args["y2"]?.jsonPrimitive?.intOrNull ?: "?"})"
            ACTION_LONG_PRESS ->
                "长按：(${args["x"]?.jsonPrimitive?.intOrNull ?: "?"} , ${args["y"]?.jsonPrimitive?.intOrNull ?: "?"})"
            ACTION_INPUT_TEXT ->
                "输入文字：${args["text"]?.jsonPrimitive?.contentOrNull ?: "（未指定）"}"
            ACTION_KEY_EVENT ->
                "按键：${args["keycode"]?.jsonPrimitive?.contentOrNull ?: "（未指定）"}"
            ACTION_LIST_APPS -> "列出已安装应用"
            else -> "未知操作"
        }
        return PendingToolPermission(
            id = callId,
            toolName = name,
            title = "确认控制手机",
            summary = detail,
            details = "超高危险操作，每次必须手动确认。\n操作：$detail",
            argsPreview = argsPreview
        )
    }

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val action = args["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("缺少必需参数：action")

        return try {
            when (action) {
                ACTION_OPEN_APP -> openApp(args)
                ACTION_READ_SCREEN -> readScreen()
                ACTION_CLICK_ELEMENT -> clickElement(args)
                ACTION_CLICK_TEXT -> clickText(args)
                ACTION_TAP -> tap(args)
                ACTION_SWIPE -> swipe(args)
                ACTION_LONG_PRESS -> longPress(args)
                ACTION_INPUT_TEXT -> inputText(args)
                ACTION_KEY_EVENT -> keyEvent(args)
                ACTION_LIST_APPS -> listApps(args)
                else -> ToolResult.Error("未知操作：$action")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FileLogger.e(TAG, "PhoneControl $action 失败", e)
            ToolResult.Error("执行无障碍控制操作失败：${e.message}")
        }
    }

    private fun a11yError(): ToolResult =
        ToolResult.Error("无障碍服务未开启，请先在「手机控制」开启无障碍", code = "A11Y_NOT_READY")

    private suspend fun openApp(args: Map<String, JsonElement>): ToolResult {
        val pkg = args["package_name"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("缺少必需参数：package_name")
        if (!PACKAGE_NAME_REGEX.matches(pkg)) {
            return ToolResult.Error("非法的包名：$pkg")
        }
        val service = PhoneAccessibilityService.current() ?: return a11yError()
        status.begin(context.getString(R.string.phone_action_open_app, pkg))
        val ok = service.openApp(pkg)
        if (ok) awaitForeground(service, pkg)
        status.end()
        return if (ok) {
            ToolResult.Success(JsonPrimitive("已打开应用 $pkg"))
        } else {
            ToolResult.Error("打开应用失败：未找到 $pkg 的启动入口")
        }
    }

    private suspend fun readScreen(): ToolResult {
        val service = PhoneAccessibilityService.current() ?: return a11yError()
        status.begin(context.getString(R.string.phone_action_read_screen))
        val elements = service.readScreenElements()
        status.end()
        if (elements.isEmpty()) {
            return ToolResult.Success(JsonPrimitive("当前屏幕无可交互元素"))
        }
        val text = elements.joinToString("\n") {
            "[${it.id}] ${it.role} ${it.label}".trimEnd()
        }
        return ToolResult.Success(JsonPrimitive("当前屏幕内容：\n$text"))
    }

    private suspend fun clickElement(args: Map<String, JsonElement>): ToolResult {
        val id = args["element_id"]?.jsonPrimitive?.intOrNull
            ?: return ToolResult.Error("缺少必需参数：element_id")
        val service = PhoneAccessibilityService.current() ?: return a11yError()
        status.begin(context.getString(R.string.phone_action_click_element, id))
        val ok = service.clickElement(id)
        status.end()
        return if (ok) {
            ToolResult.Success(JsonPrimitive("已点击元素 [$id]"))
        } else {
            ToolResult.Error("点击失败：元素 [$id] 不存在或不可点击")
        }
    }

    private suspend fun clickText(args: Map<String, JsonElement>): ToolResult {
        val text = args["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val contentDescription = args["content_description"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
        if (text == null && contentDescription == null) {
            return ToolResult.Error("缺少必需参数：text 或 content_description")
        }
        val service = PhoneAccessibilityService.current() ?: return a11yError()
        val query = text ?: contentDescription.orEmpty()
        status.begin(context.getString(R.string.phone_action_click_text, query))
        val ok = service.clickByText(text, contentDescription)
        status.end()
        return if (ok) {
            ToolResult.Success(JsonPrimitive("已点击「$query」"))
        } else {
            ToolResult.Error("点击失败：界面上没找到「$query」，可先 read_screen 看当前结构")
        }
    }

    /**
     * 轮询等待目标包名成为前台（复刻 Muse awaitForeground：最多 2s、每 50ms 查一次）。
     *
     * 不等的话紧接着的点击会落在旧界面上扑空。超时静默返回，由后续操作自己暴露问题。
     */
    private suspend fun awaitForeground(service: PhoneAccessibilityService, targetPackage: String) {
        val deadline = System.currentTimeMillis() + OPEN_APP_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (PhoneAccessibilityService.foregroundPackage == targetPackage) return
            delay(FOREGROUND_POLL_MS)
        }
    }

    private suspend fun tap(args: Map<String, JsonElement>): ToolResult {
        val x = args["x"]?.jsonPrimitive?.intOrNull
            ?: return ToolResult.Error("缺少必需参数：x")
        val y = args["y"]?.jsonPrimitive?.intOrNull
            ?: return ToolResult.Error("缺少必需参数：y")
        val service = PhoneAccessibilityService.current() ?: return a11yError()
        status.begin(context.getString(R.string.phone_action_tap, x, y))
        val ok = service.tap(x.toFloat(), y.toFloat())
        status.end()
        return if (ok) {
            ToolResult.Success(JsonPrimitive("已点击 ($x, $y)"))
        } else {
            ToolResult.Error("点击失败（无障碍手势被取消）")
        }
    }

    private suspend fun swipe(args: Map<String, JsonElement>): ToolResult {
        val x1 = args["x"]?.jsonPrimitive?.intOrNull
            ?: return ToolResult.Error("缺少必需参数：x（起点 x）")
        val y1 = args["y"]?.jsonPrimitive?.intOrNull
            ?: return ToolResult.Error("缺少必需参数：y（起点 y）")
        val x2 = args["x2"]?.jsonPrimitive?.intOrNull
            ?: return ToolResult.Error("缺少必需参数：x2（终点 x）")
        val y2 = args["y2"]?.jsonPrimitive?.intOrNull
            ?: return ToolResult.Error("缺少必需参数：y2（终点 y）")
        val duration = args["duration"]?.jsonPrimitive?.intOrNull ?: 300
        val service = PhoneAccessibilityService.current() ?: return a11yError()
        status.begin(context.getString(R.string.phone_action_swipe, x1, y1, x2, y2))
        val ok = service.swipe(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), duration.toLong())
        status.end()
        return if (ok) {
            ToolResult.Success(JsonPrimitive("已滑动 ($x1,$y1) → ($x2,$y2)，时长 ${duration}ms"))
        } else {
            ToolResult.Error("滑动失败（无障碍手势被取消）")
        }
    }

    private suspend fun longPress(args: Map<String, JsonElement>): ToolResult {
        val x = args["x"]?.jsonPrimitive?.intOrNull
            ?: return ToolResult.Error("缺少必需参数：x")
        val y = args["y"]?.jsonPrimitive?.intOrNull
            ?: return ToolResult.Error("缺少必需参数：y")
        val service = PhoneAccessibilityService.current() ?: return a11yError()
        status.begin(context.getString(R.string.phone_action_long_press, x, y))
        val ok = service.longPress(x.toFloat(), y.toFloat())
        status.end()
        return if (ok) {
            ToolResult.Success(JsonPrimitive("已长按 ($x, $y)"))
        } else {
            ToolResult.Error("长按失败（无障碍手势被取消）")
        }
    }

    private suspend fun inputText(args: Map<String, JsonElement>): ToolResult {
        val text = args["text"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("缺少必需参数：text")
        if (!SAFE_TEXT_REGEX.matches(text)) {
            return ToolResult.Error("输入文本包含不允许的字符（;&|`$\\\\）")
        }
        val service = PhoneAccessibilityService.current() ?: return a11yError()
        status.begin(context.getString(R.string.phone_action_input_text))
        val ok = service.typeText(text)
        status.end()
        return if (ok) {
            ToolResult.Success(JsonPrimitive("已输入文字：$text"))
        } else {
            ToolResult.Error("输入失败：未找到可输入的焦点")
        }
    }

    private suspend fun keyEvent(args: Map<String, JsonElement>): ToolResult {
        val keycodeName = args["keycode"]?.jsonPrimitive?.contentOrNull?.uppercase()
            ?: return ToolResult.Error("缺少必需参数：keycode")
        val globalAction = GLOBAL_ACTIONS[keycodeName]
            ?: return ToolResult.Error("不支持的按键：$keycodeName（支持：${GLOBAL_ACTIONS.keys.joinToString("/")}）")
        val service = PhoneAccessibilityService.current() ?: return a11yError()
        status.begin(context.getString(R.string.phone_action_press_key, keycodeName))
        val ok = service.performGlobal(globalAction)
        status.end()
        return if (ok) {
            ToolResult.Success(JsonPrimitive("已执行按键：$keycodeName"))
        } else {
            ToolResult.Error("按键执行失败：$keycodeName")
        }
    }

    private suspend fun listApps(args: Map<String, JsonElement>): ToolResult {
        val thirdParty = args["third_party_only"]?.jsonPrimitive?.contentOrNull
            ?.equals("true", ignoreCase = true) ?: true
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)
            .filter { !thirdParty || (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { pm.getApplicationLabel(it).toString() to it.packageName }
            .sortedBy { it.first.lowercase() }
        val text = apps.joinToString("\n") { "${it.first} (${it.second})" }
        return ToolResult.Success(JsonPrimitive("已安装应用（${apps.size} 个）：\n$text"))
    }
}
