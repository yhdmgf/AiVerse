package com.aiverse.core.theme

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * 锁定 [chatPanelGlassOr] 两条分支行为（issue #193）：玻璃档与非玻璃档都跟随聊天面板透明系数。
 *
 * 沿用 ChatAnimationsTest 的做法：仓库未引入 compose ui-test-junit4，故用 Robolectric +
 * 手工 [Composition]/[AbstractApplier] 跑 @Composable，不新增依赖。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ChatPanelGlassOrTest {

    /** glass 用一个 alpha=0x80 的半透明色，便于区分「乘系数」与「回退实色」两条路径。 */
    private val glass = Color(0xCC, 0xDD, 0xEE, 0x80)

    @Test
    fun glass_branch_full_opacity_keeps_glass_alpha() = runTest {
        val result = resolve(surfaceGlassStyle = true, chatPanelOpacity = 1f)
        assertEquals(0x80 / 255f, result.alpha, 0.001f)
    }

    @Test
    fun glass_branch_half_opacity_scales_glass_alpha() = runTest {
        val result = resolve(surfaceGlassStyle = true, chatPanelOpacity = 0.5f)
        assertEquals(0x80 / 255f * 0.5f, result.alpha, 0.001f)
    }

    @Test
    fun glass_branch_extremely_low_opacity_clamps_to_five_percent_floor() = runTest {
        val result = resolve(surfaceGlassStyle = true, chatPanelOpacity = 0.01f)
        // Color 把 alpha 量化到 8 位：128 × 0.05 = 6.4 → 实存 6/255，误差约半个量化步长，
        // 大于常规 0.001 容差（其余用例因 128×1.0 / 128×0.5 为整数、量化无损而不受影响），
        // 故这里改用「1 个 8 位步长」做容差。
        assertEquals(0x80 / 255f * 0.05f, result.alpha, 1f / 255f)
    }

    @Test
    fun non_glass_branch_full_opacity_returns_surface_at_full_alpha() = runTest {
        val result = resolve(surfaceGlassStyle = false, chatPanelOpacity = 1f)
        // MaterialTheme 默认 lightColorScheme().surface 为白色，alpha 下限不生效 → 不透明。
        assertEquals(1f, result.alpha, 0.001f)
    }

    @Test
    fun non_glass_branch_low_opacity_clamps_to_thirty_five_percent_floor() = runTest {
        val result = resolve(surfaceGlassStyle = false, chatPanelOpacity = 0.2f)
        assertEquals(0.35f, result.alpha, 0.001f)
    }

    /**
     * 提供 LocalSurfaceGlassStyle / LocalChatPanelOpacity 与 MaterialTheme colorScheme 后
     * 调用 [chatPanelGlassOr]，把返回的 Color 经 SideEffect 捕获出来供断言。
     */
    private fun TestScope.resolve(surfaceGlassStyle: Boolean, chatPanelOpacity: Float): Color {
        val harness = ColorComposition(this, surfaceGlassStyle, chatPanelOpacity, glass)
        try {
            harness.settle()
            return harness.result
        } finally {
            harness.close()
        }
    }

    private class TestLifecycleOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }

    private class ColorComposition(
        private val scope: TestScope,
        surfaceGlassStyle: Boolean,
        chatPanelOpacity: Float,
        glass: Color
    ) : AutoCloseable {
        val owner = TestLifecycleOwner()
        var result = Color.Unspecified
            private set
        private val frameClock = BroadcastFrameClock()
        private var frameNanos = 0L
        private val recomposer = Recomposer(scope.backgroundScope.coroutineContext + frameClock)
        private val composition = Composition(object : AbstractApplier<Unit>(Unit) {
            override fun insertTopDown(index: Int, instance: Unit) = Unit
            override fun insertBottomUp(index: Int, instance: Unit) = Unit
            override fun remove(index: Int, count: Int) = Unit
            override fun move(from: Int, to: Int, count: Int) = Unit
            override fun onClear() = Unit
        }, recomposer)

        init {
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
            scope.backgroundScope.launch(frameClock) {
                recomposer.runRecomposeAndApplyChanges()
            }
            composition.setContent {
                CompositionLocalProvider(
                    LocalSurfaceGlassStyle provides surfaceGlassStyle,
                    LocalChatPanelOpacity provides chatPanelOpacity
                ) {
                    MaterialTheme(colorScheme = lightColorScheme()) {
                        val resolved = chatPanelGlassOr(glass)
                        SideEffect { result = resolved }
                    }
                }
            }
        }

        fun settle() {
            repeat(4) { frame(16) }
        }

        private fun frame(millis: Long) {
            Snapshot.sendApplyNotifications()
            scope.runCurrent()
            frameNanos += millis * 1_000_000L
            frameClock.sendFrame(frameNanos)
            scope.runCurrent()
            Snapshot.sendApplyNotifications()
            scope.runCurrent()
        }

        override fun close() {
            composition.dispose()
            recomposer.cancel()
            scope.runCurrent()
        }
    }
}
