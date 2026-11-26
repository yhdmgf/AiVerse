package com.aiverse.core.ui.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.aiverse.R
import com.aiverse.feature.phonecontrol.domain.PhoneControlStatus
import com.aiverse.feature.settings.data.repository.ToolSafetySettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 系统级悬浮球（工具授权页「悬浮球」开关开启后显示）。
 *
 * 悬浮球使用 App 图标，可拖动到屏幕任意位置，松手自动平滑吸附到最近的左/右边缘（可在
 * 「软件权限」页关闭）；单击快速切换到当前 App，长按在球旁弹出
 * 正方形控制面板（含「大小」与「隐藏透明度」滑块，默认 42dp、100% 不透明）。大小与
 * 透明度持久化到 [ToolSafetySettingsRepository]；App 重启后由 MainActivity 自动恢复（开关开启时）。
 *
 * 需要 [Settings.ACTION_MANAGE_OVERLAY_PERMISSION] 悬浮窗权限，未授予时关闭自身。
 */
@AndroidEntryPoint
class OverlayBallService : Service() {

    @Inject
    lateinit var toolSafety: ToolSafetySettingsRepository

    @Inject
    lateinit var phoneControlStatus: PhoneControlStatus

    private var windowManager: WindowManager? = null
    /** 悬浮球根容器：App 图标 + 状态徽标，触摸/拖动监听挂在该容器上。 */
    private var ballView: FrameLayout? = null
    /** 球本体图标（白色椭圆底 + App 图标），容器内居中、尺寸固定为球大小。 */
    private var ballIconView: ImageView? = null
    /** 右上角状态徽标：操控进行中/完成/失败用颜色圆点区分，空闲时隐藏。 */
    private var badgeView: View? = null
    /** 徽标进行中时的呼吸脉冲动画。 */
    private var badgePulse: ObjectAnimator? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var panelView: View? = null
    private var ballSizeDp: Int = DEFAULT_SIZE_DP
    private var ballAlphaPercent: Int = ToolSafetySettingsRepository.DEFAULT_ALPHA_PERCENT
    /** 吸附动画；新拖动开始时取消进行中的动画。 */
    private var snapAnimator: ValueAnimator? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 前台服务必须在 onStartCommand 后尽快 startForeground（Android 8+ 强制）。
        startForegroundCompat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        // 读取持久化开关、大小与透明度；开关已关（如系统重启拉起残留）时直接退出。
        scope.launch {
            val enabled = toolSafety.overlayBallEnabledFlow.first()
            if (!enabled) {
                stopSelf()
            } else {
                ballSizeDp = toolSafety.overlayBallSizeFlow.first().coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)
                ballAlphaPercent = toolSafety.overlayBallAlphaFlow.first().coerceIn(
                    ToolSafetySettingsRepository.MIN_ALPHA_PERCENT,
                    ToolSafetySettingsRepository.MAX_ALPHA_PERCENT
                )
                showBall()
            }
        }
        return START_STICKY
    }

    private fun showBall() {
        if (ballView != null) return
        val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
        windowManager = wm

        val sizePx = dp(ballSizeDp)
        // 球本体：白色椭圆底 + App 图标，尺寸保持原样（徽标叠加在容器内，不改变球大小与吸附计算）。
        val imageView = ImageView(this).apply {
            setImageDrawable(packageManager.getApplicationIcon(packageName))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE6FFFFFF.toInt())
                setStroke(dp(2), 0x33000000)
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
            setPadding(dp(8), dp(8), dp(8), dp(8))
            alpha = ballAlphaPercent / 100f
        }

        // 状态徽标：右上角小圆点，颜色随手机操控状态变化（空闲时隐藏）。
        val badgeSize = dp(BADGE_SIZE_DP)
        val badge = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF34C759.toInt())
                setStroke(dp(1), 0xFFFFFFFF.toInt())
            }
            contentDescription = getString(R.string.overlay_ball_tap_to_review)
            visibility = View.GONE
        }
        val badgeParams = FrameLayout.LayoutParams(badgeSize, badgeSize).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(0, dp(2), dp(2), 0)
        }

        // 根容器：叠加图标与徽标，触摸/拖动监听挂在此处，球本体尺寸不变。
        val container = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        container.addView(imageView, FrameLayout.LayoutParams(sizePx, sizePx))
        container.addView(badge, badgeParams)

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(20)
            y = dp(280)
        }

        // 拖动：ACTION_MOVE 更新坐标；单击（位移小于阈值）快速切换当前 App；长按（按住未移动）在球旁弹出控制面板。
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false
        var longPressTriggered = false
        val longPressRunnable = Runnable {
            longPressTriggered = true
            showPanel()
        }
        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    longPressTriggered = false
                    // 长按计时：按住未移动达到阈值即弹出控制面板
                    container.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT_MS)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > TOUCH_SLOP || abs(dy) > TOUCH_SLOP) {
                        moved = true
                        // 拖动后取消长按
                        container.removeCallbacks(longPressRunnable)
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    runCatching { wm.updateViewLayout(container, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    container.removeCallbacks(longPressRunnable)
                    if (!moved && !longPressTriggered) {
                        if (phoneControlStatus.state.value.active) {
                            // 操控中：禁止单击切回应用，避免把 AiVerse 切到前台导致读屏读到自身界面（死循环根因）。
                            showRunningToast()
                        } else {
                            // 单击：快速切换当前 App（球保留，可继续使用）
                            launchMainActivity()
                        }
                    } else if (moved) {
                        // 拖动结束：松手自动平滑吸附到最近的左/右边缘（固定行为）
                        snapToNearestEdge()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    container.removeCallbacks(longPressRunnable)
                    // 手势被打断但球已拖离原位：同样吸附到最近边缘，避免停在屏幕中间
                    if (moved) {
                        snapToNearestEdge()
                    }
                    true
                }
                else -> false
            }
        }

        runCatching { wm.addView(container, params) }
            .onSuccess {
                ballView = container
                ballIconView = imageView
                badgeView = badge
                ballParams = params
                observePhoneControlStatus()
            }
            .onFailure {
                stopSelf()
            }
    }

    /** 订阅手机操控状态，轻量刷新徽标（服务长期存在时仅状态变化时回调）。 */
    private fun observePhoneControlStatus() {
        scope.launch {
            phoneControlStatus.state.collect { state -> updateBallStatus(state) }
        }
    }

    /** 据手机操控状态渲染徽标：进行中蓝点脉冲、失败红、完成绿、空闲隐藏。 */
    private fun updateBallStatus(state: PhoneControlStatus.State) {
        val badge = badgeView ?: return
        val color = when {
            state.active -> BADGE_COLOR_ACTIVE
            state.finished && state.success == false -> BADGE_COLOR_FAILED
            state.finished -> BADGE_COLOR_DONE
            else -> null
        }
        if (color == null) {
            stopBadgePulse()
            badge.visibility = View.GONE
            return
        }
        badge.visibility = View.VISIBLE
        badge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(1), 0xFFFFFFFF.toInt())
        }
        badge.contentDescription = when {
            state.active -> if (state.currentAction.isNullOrBlank()) {
                getString(R.string.overlay_ball_status_running)
            } else {
                getString(R.string.overlay_ball_status_running_with_action, state.currentAction)
            }
            state.finished && state.success == false -> getString(R.string.overlay_ball_status_failed)
            else -> getString(R.string.overlay_ball_status_done)
        }
        if (state.active) startBadgePulse() else stopBadgePulse()
    }

    /** 进行中：徽标透明度呼吸脉冲。 */
    private fun startBadgePulse() {
        if (badgePulse?.isRunning == true) return
        val badge = badgeView ?: return
        badgePulse = ObjectAnimator.ofFloat(badge, View.ALPHA, 1f, 0.35f).apply {
            duration = BADGE_PULSE_DURATION_MS
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopBadgePulse() {
        badgePulse?.cancel()
        badgePulse = null
        badgeView?.alpha = 1f
    }

    /** 操控中单击悬浮球：提示当前状态，不切回应用（打破读屏死循环）。 */
    private fun showRunningToast() {
        val state = phoneControlStatus.state.value
        val action = state.currentAction
        val message = if (action.isNullOrBlank()) {
            getString(R.string.overlay_ball_status_running)
        } else {
            getString(R.string.overlay_ball_status_running_with_action, action)
        }
        runCatching {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    /** 拖动结束：计算最近的左/右边缘目标 x，并平滑动画吸附过去（y 保持松手位置不变）。 */
    private fun snapToNearestEdge() {
        val params = ballParams ?: return
        val screenW = resources.displayMetrics.widthPixels
        val ballW = params.width
        val margin = dp(SNAP_EDGE_MARGIN_DP)
        val centerX = params.x + ballW / 2
        val targetX = if (centerX <= screenW / 2) {
            margin
        } else {
            screenW - ballW - margin
        }.coerceIn(0, (screenW - ballW).coerceAtLeast(0))
        animateBallTo(targetX)
    }

    /** 用 [ValueAnimator] 把球从当前 x 平滑滑动到 [targetX]（瞬移改为补间动画）。 */
    private fun animateBallTo(targetX: Int) {
        val view = ballView ?: return
        val params = ballParams ?: return
        val wm = windowManager ?: return
        snapAnimator?.cancel()
        val animator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = SNAP_ANIMATION_MS
            addUpdateListener { anim ->
                params.x = anim.animatedValue as Int
                runCatching { wm.updateViewLayout(view, params) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (snapAnimator === animation) snapAnimator = null
                }
            })
            start()
        }
        snapAnimator = animator
    }

    private fun hideBall() {
        snapAnimator?.cancel()
        snapAnimator = null
        stopBadgePulse()
        ballView?.let { view -> runCatching { windowManager?.removeView(view) } }
        ballView = null
        ballIconView = null
        badgeView = null
        ballParams = null
    }

    /** 单击：快速切换到当前 App（AiVerse 主界面，球保留可继续使用）。 */
    private fun launchMainActivity() {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(launch) }
    }

    /** 长按：在球旁弹出正方形控制面板，含「大小」与「隐藏透明度」滑块，改动即时生效并持久化。 */
    private fun showPanel() {
        if (panelView != null) return
        val wm = windowManager ?: return
        val context = this
        val ballP = ballParams ?: return

        val panelSize = dp(PANEL_SIZE_DP)
        // 正方形根容器
        val root = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0x22000000)
            }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(20))
        }

        // 标题行：标题 + 关闭按钮
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(
            TextView(context).apply {
                text = getString(R.string.overlay_ball_panel_title)
                textSize = 16f
                setTextColor(0xFF1A1A1A.toInt())
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        titleRow.addView(TextView(context).apply {
            text = "\u00d7"
            textSize = 20f
            setTextColor(0xFF8A8A8A.toInt())
            setPadding(dp(10), 0, 0, 0)
            setOnClickListener { closePanel() }
        })
        content.addView(titleRow)

        // 大小滑块
        content.addView(
            sliderRow(
                getString(R.string.overlay_ball_size_label),
                MAX_SIZE_DP - MIN_SIZE_DP,
                ballSizeDp - MIN_SIZE_DP,
                getString(R.string.overlay_ball_size_value, ballSizeDp)
            ) { progress ->
                val newSize = MIN_SIZE_DP + progress
                ballSizeDp = newSize
                scope.launch { toolSafety.setOverlayBallSize(newSize) }
                ballView?.let { v ->
                    val p = ballParams ?: return@let
                    val sp = dp(newSize)
                    p.width = sp
                    p.height = sp
                    runCatching { wm.updateViewLayout(v, p) }
                }
                getString(R.string.overlay_ball_size_value, newSize)
            }
        )

        // 隐藏透明度滑块
        content.addView(
            sliderRow(
                getString(R.string.overlay_ball_alpha_label),
                ToolSafetySettingsRepository.MAX_ALPHA_PERCENT - ToolSafetySettingsRepository.MIN_ALPHA_PERCENT,
                ballAlphaPercent - ToolSafetySettingsRepository.MIN_ALPHA_PERCENT,
                getString(R.string.overlay_ball_alpha_value, ballAlphaPercent)
            ) { progress ->
                val newAlpha = ToolSafetySettingsRepository.MIN_ALPHA_PERCENT + progress
                ballAlphaPercent = newAlpha
                scope.launch { toolSafety.setOverlayBallAlpha(newAlpha) }
                // 透明度只作用于球本体图标，状态徽标保持清晰可见。
                ballIconView?.alpha = newAlpha / 100f
                getString(R.string.overlay_ball_alpha_value, newAlpha)
            }
        )

        root.addView(content)

        // 定位：优先放球右侧，其次左侧；两侧都不够时放球上方或下方（避开球），确保球保留可见。
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val gap = dp(12)
        val rightX = ballP.x + ballP.width + gap
        val leftX = ballP.x - panelSize - gap
        var overlapsBall = false
        val (px, py) = when {
            rightX + panelSize <= screenW -> rightX to ballP.y
            leftX >= dp(8) -> leftX to ballP.y
            else -> {
                val cx = (ballP.x - (panelSize - ballP.width) / 2)
                    .coerceIn(dp(8), (screenW - panelSize - dp(8)).coerceAtLeast(dp(8)))
                val aboveY = ballP.y - panelSize - gap
                val belowY = ballP.y + ballP.height + gap
                when {
                    aboveY >= dp(8) -> cx to aboveY
                    belowY + panelSize <= screenH - dp(8) -> cx to belowY
                    else -> {
                        overlapsBall = true
                        cx to ballP.y
                    }
                }
            }
        }
        val yMax = (screenH - panelSize - dp(8)).coerceAtLeast(dp(8))
        val fx = px.coerceIn(dp(8), (screenW - panelSize - dp(8)).coerceAtLeast(dp(8)))
        val fy = py.coerceIn(dp(8), yMax)

        val params = WindowManager.LayoutParams(
            panelSize,
            panelSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = fx
            this.y = fy
        }
        runCatching { wm.addView(root, params) }
            .onSuccess {
                panelView = root
                // 极端小屏下面板必须与球重叠时，把球提到面板上层，确保球始终可见。
                if (overlapsBall) {
                    ballView?.let { v ->
                        val p = ballParams ?: return@let
                        runCatching { wm.removeView(v) }
                        runCatching { wm.addView(v, p) }
                    }
                }
            }
            .onFailure { /* 添加失败不致命：球仍在，可再次长按 */ }
    }

    /** 构建「标签 + 滑块 + 数值」行；拖动时回调 [onProgress]，以其返回值刷新数值文本。 */
    private fun sliderRow(
        label: String,
        max: Int,
        progress: Int,
        initialValue: String,
        onProgress: (Int) -> String
    ): LinearLayout {
        val context = this
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(4))
        }
        row.addView(
            TextView(context).apply {
                text = label
                textSize = 15f
                setTextColor(0xFF1A1A1A.toInt())
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        val value = TextView(context).apply {
            text = initialValue
            textSize = 15f
            setTextColor(0xFF666666.toInt())
            setPadding(dp(10), 0, 0, 0)
        }
        val seek = SeekBar(context).apply {
            this.max = max
            this.progress = progress.coerceIn(0, max)
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                value.text = onProgress(progress)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        row.addView(seek, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f))
        row.addView(value)
        return row
    }

    private fun closePanel() {
        panelView?.let { view -> runCatching { windowManager?.removeView(view) } }
        panelView = null
        // 开关仍开时保持悬浮球（重读最新大小与透明度）
        scope.launch {
            if (toolSafety.overlayBallEnabledFlow.first()) {
                ballSizeDp = toolSafety.overlayBallSizeFlow.first().coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)
                ballAlphaPercent = toolSafety.overlayBallAlphaFlow.first().coerceIn(
                    ToolSafetySettingsRepository.MIN_ALPHA_PERCENT,
                    ToolSafetySettingsRepository.MAX_ALPHA_PERCENT
                )
                showBall()
            }
        }
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.overlay_ball_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.overlay_ball_notification))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching { startForeground(NOTIFICATION_ID, notification) }
    }

    override fun onDestroy() {
        snapAnimator?.cancel()
        snapAnimator = null
        panelView?.let { view -> runCatching { windowManager?.removeView(view) } }
        panelView = null
        stopBadgePulse()
        ballView?.let { view -> runCatching { windowManager?.removeView(view) } }
        ballView = null
        ballIconView = null
        badgeView = null
        ballParams = null
        windowManager = null
        scope.cancel()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "OverlayBallService"
        private const val NOTIFICATION_ID = 10
        private const val CHANNEL_ID = "overlay_ball"
        private const val TOUCH_SLOP = 10
        private const val LONG_PRESS_TIMEOUT_MS = 500L
        /** 吸附动画时长（ms）。 */
        private const val SNAP_ANIMATION_MS = 250L
        /** 吸附后离屏幕边缘的留白（dp），保证球完整可见、便于再次拖动。 */
        private const val SNAP_EDGE_MARGIN_DP = 8
        const val DEFAULT_SIZE_DP = 42
        const val MIN_SIZE_DP = 32
        const val MAX_SIZE_DP = 80
        /** 控制面板边长（dp），正方形。 */
        private const val PANEL_SIZE_DP = 260
        /** 右上角状态徽标圆点直径（dp）。 */
        private const val BADGE_SIZE_DP = 12
        /** 徽标呼吸脉冲半周期（ms）。 */
        private const val BADGE_PULSE_DURATION_MS = 600L
        /** 徽标颜色：进行中蓝、完成绿、失败红。 */
        private const val BADGE_COLOR_ACTIVE = 0xFF3B82F6.toInt()
        private const val BADGE_COLOR_DONE = 0xFF34C759.toInt()
        private const val BADGE_COLOR_FAILED = 0xFFEF4444.toInt()
        const val ACTION_SHOW = "com.aiverse.action.SHOW_OVERLAY_BALL"
        const val ACTION_HIDE = "com.aiverse.action.HIDE_OVERLAY_BALL"

        /** 开启悬浮球（幂等；需已授予悬浮窗权限，否则服务自动退出）。 */
        fun show(context: Context) {
            val intent = Intent(context, OverlayBallService::class.java).apply { action = ACTION_SHOW }
            runCatching { context.startForegroundService(intent) }
        }

        /** 关闭悬浮球。 */
        fun hide(context: Context) {
            val intent = Intent(context, OverlayBallService::class.java).apply { action = ACTION_HIDE }
            runCatching { context.startForegroundService(intent) }
        }
    }
}
