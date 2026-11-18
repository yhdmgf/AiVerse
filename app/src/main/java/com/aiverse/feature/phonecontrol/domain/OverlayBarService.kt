package com.aiverse.feature.phonecontrol.domain

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.aiverse.MainActivity
import com.aiverse.R
import com.aiverse.feature.phonecontrol.data.OverlayPrefs
import com.aiverse.feature.phonecontrol.data.PhoneControlSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 手机控制「灵动岛」：AI 用手机控制工具操作手机期间，屏幕顶部显示悬浮球；
 * 点开展开为灵动岛面板，实时展示 AI 当前操作与最近几步（思考过程）；
 * 操作结束后点开可查看结果卡片（复制结果 / 完成 / 回到应用）。
 *
 * 订阅 [PhoneControlStatus.state] 渲染；闲置可自动折叠成小球（点击再展开），
 * 折叠球也随状态变化（进行中带脉冲动画并显示当前动作首字，结束后变绿显示对勾）。
 * 无活动且用户已收起后自动停自身，避免残留小球。需要悬浮窗权限，未授予时关闭自身。
 */
@AndroidEntryPoint
class OverlayBarService : Service() {

    @Inject
    lateinit var settings: PhoneControlSettingsRepository

    @Inject
    lateinit var status: PhoneControlStatus

    private var windowManager: WindowManager? = null
    private var barView: View? = null
    private var barParams: WindowManager.LayoutParams? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private val foldRunnable = Runnable { foldToBall() }
    private val resultCollapseRunnable = Runnable { foldToBall() }
    private val settleRunnable = Runnable { settleWhenIdle() }
    private var subscribed = false

    private var idleOpacity = OverlayPrefs.DEFAULT_IDLE_OPACITY
    private var autoCollapse = true
    private var returnAfterDone = false
    private var folded = false

    companion object {
        const val ACTION_SHOW = "com.aiverse.phonecontrol.OVERLAY_BAR_SHOW"
        const val ACTION_HIDE = "com.aiverse.phonecontrol.OVERLAY_BAR_HIDE"
        private const val CHANNEL_ID = "phone_control_bar"
        private const val NOTIFICATION_ID = 0x5101
        private const val FOLD_DELAY_MS = 6_000L

        /** 完成结果卡片展示多久后自动收起。 */
        private const val RESULT_AUTO_COLLAPSE_MS = 20_000L

        /** 折叠成小球且无活动后多久停自身（避免残留小球）。 */
        private const val SETTLE_IDLE_MS = 30_000L

        /** 显示灵动岛（无悬浮窗权限时忽略）。 */
        fun show(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            runCatching {
                context.startService(Intent(context, OverlayBarService::class.java).apply { action = ACTION_SHOW })
            }
        }

        /** 隐藏并停止灵动岛。 */
        fun hide(context: Context) {
            runCatching {
                context.startService(Intent(context, OverlayBarService::class.java).apply { action = ACTION_HIDE })
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()
        if (!subscribed) {
            subscribed = true
            scope.launch {
                status.state.collect { render(it) }
            }
        }
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
        scope.launch {
            val prefs = settings.settingsFlow.first().overlay
            idleOpacity = (prefs.idleOpacity)
                .coerceIn(OverlayPrefs.MIN_OPACITY, OverlayPrefs.MAX_OPACITY)
            autoCollapse = prefs.autoCollapse
            returnAfterDone = prefs.returnAfterDone
            showBar()
        }
        return START_STICKY
    }

    private fun render(state: PhoneControlStatus.State) {
        if (state.active && barView == null) {
            showBar()
        }
        val view = barView ?: return

        if (state.active) {
            // 有活动进行中：绝不因闲置停自身，取消结果态的收起/停止计时。
            handler.removeCallbacks(resultCollapseRunnable)
            handler.removeCallbacks(settleRunnable)
        } else {
            if (view is PanelView && state.finished) scheduleResultCollapse()
            if (folded) scheduleSettle()
        }

        when (view) {
            is PanelView -> {
                view.update(state)
                scheduleFoldIfNeeded()
            }
            is BallView -> view.update(state)
        }
    }

    private fun showBar() {
        if (barView != null) return
        val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
        windowManager = wm

        val panel = PanelView(
            this,
            onFold = { foldToBall() },
            onStop = { stopSelf() },
            onFinish = { foldToBall() },
            onCopy = { copyResult() },
            onBackToApp = { backToApp() }
        )
        panel.alpha = idleOpacity

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(48)
        }

        runCatching { wm.addView(panel, params) }
            .onFailure { stopSelf(); return }
        barView = panel
        barParams = params
        panel.update(status.state.value)
        scheduleFoldIfNeeded()
    }

    private fun scheduleFoldIfNeeded() {
        handler.removeCallbacks(foldRunnable)
        if (autoCollapse) handler.postDelayed(foldRunnable, FOLD_DELAY_MS)
    }

    private fun scheduleResultCollapse() {
        handler.removeCallbacks(resultCollapseRunnable)
        handler.postDelayed(resultCollapseRunnable, RESULT_AUTO_COLLAPSE_MS)
    }

    private fun scheduleSettle() {
        handler.removeCallbacks(settleRunnable)
        handler.postDelayed(settleRunnable, SETTLE_IDLE_MS)
    }

    private fun foldToBall() {
        val wm = windowManager ?: return
        val view = barView ?: return
        if (folded) return
        folded = true
        handler.removeCallbacks(resultCollapseRunnable)
        (view as? BallView)?.stopPulse()
        runCatching { wm.removeView(view) }

        val ball = BallView(this)
        ball.update(status.state.value)
        val ballParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(48)
        }
        runCatching { wm.addView(ball, ballParams) }
        barView = ball
        barParams = ballParams
        if (!status.state.value.active) scheduleSettle()
    }

    private fun unfoldFromBall() {
        val wm = windowManager ?: return
        val view = barView ?: return
        (view as? BallView)?.stopPulse()
        runCatching { wm.removeView(view) }
        folded = false
        handler.removeCallbacks(settleRunnable)
        showBar()
    }

    /** 无活动时收尾：按设置回到应用并停自身，避免残留小球。 */
    private fun settleWhenIdle() {
        if (status.state.value.active) return
        if (returnAfterDone) backToApp()
        stopSelf()
    }

    private fun copyResult() {
        val summary = status.state.value.resultSummary.orEmpty()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("AiVerse", summary))
        Toast.makeText(this, getString(R.string.phone_overlay_result_copied), Toast.LENGTH_SHORT).show()
    }

    private fun backToApp() {
        val open = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        runCatching { startActivity(open) }
    }

    override fun onDestroy() {
        handler.removeCallbacks(foldRunnable)
        handler.removeCallbacks(resultCollapseRunnable)
        handler.removeCallbacks(settleRunnable)
        (barView as? BallView)?.stopPulse()
        barView?.let { runCatching { windowManager?.removeView(it) } }
        barView = null
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.phone_overlay_channel_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(getString(R.string.phone_overlay_notification_title))
            .setContentText(getString(R.string.phone_overlay_notification_text))
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** 折叠球：进行中显示当前动作首字并脉冲，结束后变绿显示对勾，点按展开。 */
    private inner class BallView(context: Context) : TextView(context) {

        private var pulse: ObjectAnimator? = null

        init {
            textSize = 16f
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xF2111111.toInt())
            }
            alpha = 0.95f
            setOnClickListener { unfoldFromBall() }
        }

        fun update(state: PhoneControlStatus.State) {
            contentDescription = context.getString(R.string.phone_overlay_expand_hint)
            when {
                state.active -> {
                    val initial = state.currentAction?.trim()?.take(1).orEmpty()
                    text = if (initial.isEmpty()) "●" else "● $initial"
                    setTextColor(0xFF3B82F6.toInt())
                    startPulse()
                }
                state.finished -> {
                    text = if (state.success == false) "● !" else "● ✓"
                    setTextColor(if (state.success == false) 0xFFEF4444.toInt() else 0xFF34C759.toInt())
                    stopPulse()
                }
                else -> {
                    text = "●"
                    setTextColor(0xFF9CA3AF.toInt())
                    stopPulse()
                }
            }
        }

        fun stopPulse() {
            pulse?.cancel()
            pulse = null
            alpha = 0.95f
        }

        private fun startPulse() {
            if (pulse?.isRunning == true) return
            pulse = ObjectAnimator.ofFloat(this, View.ALPHA, 1f, 0.5f).apply {
                duration = 700L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }
    }

    /** 灵动岛面板：进行中（标题+最近步骤+停止）或完成态（结果卡片：复制/完成/回到应用）。 */
    private inner class PanelView(
        context: Context,
        private val onFold: () -> Unit,
        private val onStop: () -> Unit,
        private val onFinish: () -> Unit,
        private val onCopy: () -> Unit,
        private val onBackToApp: () -> Unit
    ) : LinearLayout(context) {

        private val badgeView: BadgeView
        private val titleView: TextView
        private val bodyScroll: ScrollView
        private val stepsContainer: LinearLayout
        private val actionsRow: LinearLayout
        private val stopView: TextView

        init {
            orientation = VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(0xF2111111.toInt())
            }

            val header = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            badgeView = BadgeView(context)
            badgeView.visibility = GONE
            titleView = TextView(context).apply {
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
                setPadding(0, 0, dp(12), 0)
            }
            val fold = TextView(context).apply {
                text = context.getString(R.string.phone_overlay_collapse)
                setTextColor(0xFF9CA3AF.toInt())
                textSize = 12f
                setPadding(dp(8), dp(2), dp(8), dp(2))
                setOnClickListener { onFold() }
            }
            header.addView(badgeView, LinearLayout.LayoutParams(dp(20), dp(20)))
            header.addView(titleView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            header.addView(fold)

            stepsContainer = LinearLayout(context).apply { orientation = VERTICAL }
            bodyScroll = ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                addView(stepsContainer)
            }

            actionsRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            actionsRow.addView(
                pillButton(context.getString(R.string.phone_overlay_copy_result), filled = false) { onCopy() },
                weightParams()
            )
            actionsRow.addView(
                pillButton(context.getString(R.string.phone_overlay_back_to_app), filled = true) { onBackToApp() },
                weightParams()
            )
            actionsRow.addView(
                pillButton(context.getString(R.string.phone_overlay_finish), filled = false) { onFinish() },
                weightParams()
            )
            actionsRow.visibility = GONE

            stopView = TextView(context).apply {
                text = context.getString(R.string.phone_overlay_stop)
                setTextColor(0xFFEF4444.toInt())
                textSize = 12f
                setPadding(0, dp(6), 0, 0)
                setOnClickListener { onStop() }
            }

            addView(header)
            addView(bodyScroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(actionsRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) })
            addView(stopView)
        }

        fun update(state: PhoneControlStatus.State) {
            val showResult = state.finished && !state.active
            badgeView.visibility = if (showResult) VISIBLE else GONE
            actionsRow.visibility = if (showResult) VISIBLE else GONE
            stopView.visibility = if (showResult) GONE else VISIBLE

            if (showResult) {
                val failed = state.success == false
                badgeView.setSuccess(!failed)
                titleView.text = context.getString(
                    if (failed) R.string.phone_overlay_failed_title else R.string.phone_overlay_done_title
                )
                stepsContainer.removeAllViews()
                val body = TextView(context).apply {
                    text = state.resultSummary
                        ?: state.recentSteps.firstOrNull()
                        ?: context.getString(R.string.phone_overlay_result_summary)
                    setTextColor(0xFFD1D5DB.toInt())
                    textSize = 13f
                    setLineSpacing(dp(4).toFloat(), 1f)
                }
                stepsContainer.addView(body, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))
                bodyScroll.layoutParams.height =
                    (resources.displayMetrics.heightPixels * 0.42f).toInt()
            } else {
                titleView.text = state.currentAction
                    ?: context.getString(
                        if (state.active) R.string.phone_overlay_running
                        else R.string.phone_overlay_recent_action
                    )
                stepsContainer.removeAllViews()
                state.recentSteps.forEach { step ->
                    val row = TextView(context).apply {
                        text = "· $step"
                        setTextColor(0xFFD1D5DB.toInt())
                        textSize = 12f
                        setPadding(0, dp(2), 0, dp(2))
                    }
                    stepsContainer.addView(row)
                }
                bodyScroll.layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
            }
            bodyScroll.requestLayout()
        }

        private fun weightParams() = LinearLayout.LayoutParams(0, dp(34), 1f).apply {
            leftMargin = dp(8)
        }

        private fun pillButton(label: String, filled: Boolean, onClick: () -> Unit): TextView =
            TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 12f
                setTextColor(if (filled) 0xFFFFFFFF.toInt() else 0xFFD1D5DB.toInt())
                background = GradientDrawable().apply {
                    cornerRadius = dp(17).toFloat()
                    setColor(if (filled) 0xFF3B82F6.toInt() else 0xFF2A2D33.toInt())
                }
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener { onClick() }
            }
    }

    /** 自绘结果徽标：成功绿色对勾，失败红色叉。 */
    private class BadgeView(context: Context) : View(context) {

        private var success = true
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = resources.displayMetrics.density * 1.8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        fun setSuccess(value: Boolean) {
            success = value
            fill.color = if (value) 0xFF34C759.toInt() else 0xFFEF4444.toInt()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val r = minOf(width, height) / 2f
            canvas.drawCircle(width / 2f, height / 2f, r, fill)
            val path = android.graphics.Path()
            if (success) {
                path.moveTo(width * 0.30f, height * 0.52f)
                path.lineTo(width * 0.45f, height * 0.67f)
                path.lineTo(width * 0.72f, height * 0.36f)
            } else {
                path.moveTo(width * 0.34f, height * 0.34f)
                path.lineTo(width * 0.66f, height * 0.66f)
                path.moveTo(width * 0.66f, height * 0.34f)
                path.lineTo(width * 0.34f, height * 0.66f)
            }
            canvas.drawPath(path, stroke)
        }
    }
}
