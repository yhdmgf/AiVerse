package com.aiverse.feature.phonecontrol.domain

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
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
import android.widget.TextView
import androidx.core.app.NotificationCompat
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
 * 点开展开为灵动岛面板，实时展示 AI 当前操作与最近几步（思考过程）。
 *
 * 订阅 [PhoneControlStatus.state] 渲染；闲置可自动折叠成小球（点击再展开），
 * 闲置不透明度取配置。需要悬浮窗权限，未授予时关闭自身。
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
    private var subscribed = false

    private var idleOpacity = OverlayPrefs.DEFAULT_IDLE_OPACITY
    private var autoCollapse = true
    private var folded = false

    companion object {
        const val ACTION_SHOW = "com.aiverse.phonecontrol.OVERLAY_BAR_SHOW"
        const val ACTION_HIDE = "com.aiverse.phonecontrol.OVERLAY_BAR_HIDE"
        private const val CHANNEL_ID = "phone_control_bar"
        private const val NOTIFICATION_ID = 0x5101
        private const val FOLD_DELAY_MS = 6_000L

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
            showBar()
        }
        return START_STICKY
    }

    private fun render(state: PhoneControlStatus.State) {
        if (state.active && barView == null) {
            showBar()
        }
        val view = barView ?: return
        if (view is PanelView) {
            view.update(state)
            scheduleFoldIfNeeded()
        }
    }

    private fun showBar() {
        if (barView != null) return
        val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
        windowManager = wm

        val panel = PanelView(this, onFold = { foldToBall() }, onStop = { stopSelf() })
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

    private fun foldToBall() {
        val wm = windowManager ?: return
        val view = barView ?: return
        if (folded) return
        folded = true
        runCatching { wm.removeView(view) }

        val ball = TextView(this).apply {
            text = "●"
            setTextColor(0xFF3B82F6.toInt())
            textSize = 18f
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xF2111111.toInt())
            }
            alpha = 0.95f
            setOnClickListener { unfoldFromBall() }
        }
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
    }

    private fun unfoldFromBall() {
        val wm = windowManager ?: return
        val view = barView ?: return
        runCatching { wm.removeView(view) }
        folded = false
        showBar()
    }

    override fun onDestroy() {
        handler.removeCallbacks(foldRunnable)
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

    /** 灵动岛面板：标题（当前操作）+ 最近步骤列表 + 收起/停止。 */
    private inner class PanelView(
        context: Context,
        private val onFold: () -> Unit,
        private val onStop: () -> Unit
    ) : LinearLayout(context) {

        private val titleView: TextView
        private val stepsContainer: LinearLayout

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
            header.addView(titleView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            header.addView(fold)

            stepsContainer = LinearLayout(context).apply { orientation = VERTICAL }

            val stop = TextView(context).apply {
                text = context.getString(R.string.phone_overlay_stop)
                setTextColor(0xFFEF4444.toInt())
                textSize = 12f
                setPadding(0, dp(6), 0, 0)
                setOnClickListener { onStop() }
            }

            addView(header)
            addView(stepsContainer)
            addView(stop)
        }

        fun update(state: PhoneControlStatus.State) {
            titleView.text = state.currentAction ?: context.getString(R.string.phone_overlay_recent_action)
            stepsContainer.removeAllViews()
            val steps = state.recentSteps
            steps.forEach { step ->
                val row = TextView(context).apply {
                    text = "· $step"
                    setTextColor(0xFFD1D5DB.toInt())
                    textSize = 12f
                    setPadding(0, dp(2), 0, dp(2))
                }
                stepsContainer.addView(row)
            }
        }
    }
}
