package com.aiverse.core.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.aiverse.core.theme.LocalSurfaceGlassStyle
import com.aiverse.core.theme.LocalUIDensityTokens
import com.aiverse.core.theme.Spacing
import com.aiverse.core.theme.glassOr
import com.aiverse.core.theme.semanticColors

/**
 * 修复 Material3 ModalBottomSheet 已知 bug（issuetracker 486562294）：
 * 内容接近全屏高度且有可滚动区域时，快速上滑 fling 到内容边界，未消费的向上速度会传给
 * sheet 的拖拽状态，导致 sheet 反复「向下拖动再弹回」振荡。
 *
 * 挂到可滚动内容外层：sheet 已完全展开时吞掉惯性动画每帧的剩余竖向位移与向上的剩余速度，
 * 避免驱动 sheet 的 AnchoredDraggableState；仅在子滚动消费后拦截，保留内容惯性和手指拖动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSheetFlingFix(sheetState: SheetState): NestedScrollConnection =
    remember(sheetState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset =
                if (source == NestedScrollSource.SideEffect &&
                    (sheetState.currentValue == SheetValue.Expanded || sheetState.targetValue == SheetValue.Expanded)
                ) {
                    // 惯性每帧的剩余位移也会驱动底栏；只拦截结束速度无法阻止边界处的往返振荡。
                    Offset(0f, available.y)
                } else {
                    Offset.Zero
                }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                if ((sheetState.currentValue == SheetValue.Expanded || sheetState.targetValue == SheetValue.Expanded) && available.y < 0f) {
                    available
                } else {
                    Velocity.Zero
                }
        }
    }

/**
 * 低端机判定内存阈值（总内存，字节）：设备总内存低于该值视为低端机，玻璃档真模糊退回伪磨砂。默认 4GB。
 * 集中定义为常量，便于统一调整。
 */
const val LOW_RAM_THRESHOLD_BYTES: Long = 4L * 1024 * 1024 * 1024

/**
 * 判定玻璃档是否应退回伪磨砂（不启用真模糊）。满足任一即返回 true：
 * - 系统处于省电模式（[PowerManager.isPowerSaveMode]）；
 * - 低端机（[ActivityManager.isLowRamDevice]，或设备总内存低于 [LOW_RAM_THRESHOLD_BYTES]）。
 *
 * 正常机且非省电时返回 false，保留玻璃档真模糊。这是玻璃档真模糊的唯一门控，
 * 集中判定便于统一调整；各玻璃消费处（当前唯一真模糊点为 [GlassBlurWindow]）调用它决定是否启用真模糊。
 */
fun shouldFallbackGlass(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    if (powerManager?.isPowerSaveMode == true) return true
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
    if (activityManager.isLowRamDevice) return true
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)
    return memInfo.totalMem < LOW_RAM_THRESHOLD_BYTES
}

/**
 * 轻量玻璃档（hyperos_glass）：API31+ 对独立窗口弹层启用窗口级背景模糊，<31 静默回退（保留半透明假磨砂）。
 * 低端机/省电模式时退回半透明伪磨砂，不启用真模糊。
 *
 * 仅当玻璃档生效；用安全 as? 拿独立 window，拿不到（非独立窗口场景）时直接跳过，绝不硬上——
 * 宁可保留半透明也不引入崩溃/兼容问题（用户约束：不做昂贵捕获背后图层式模糊）。
 */
@Composable
private fun GlassBlurWindow() {
    if (!LocalSurfaceGlassStyle.current) return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    // 低端机/省电模式：退回半透明伪磨砂，不调用 setBackgroundBlurRadius（避免低性能设备上窗口模糊带来卡顿/耗电）。
    if (shouldFallbackGlass(LocalContext.current)) return
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.setBackgroundBlurRadius(48)
    }
}

/**
 * 自适应模态底栏/弹窗：
 * - 紧凑屏幕（手机竖屏、窄分屏等，< 600dp）：呈现为标准贴底 [ModalBottomSheet]，保留下拉手势与拖拽手柄，
 *   并内置 [rememberSheetFlingFix] 杜绝快速滑动到边界时的抽搐振荡；
 * - 宽屏/平板（>= 600dp）：呈现为规范的居中模态 [Dialog]，移除拖拽手柄，限制最大宽度并居中展示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = glassOr(MaterialTheme.semanticColors.surfaceGlass, MaterialTheme.colorScheme.surface),
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = BottomSheetDefaults.Elevation,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    sheetGesturesEnabled: Boolean = true,
    dialogMaxWidth: Dp = 560.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val t = LocalUIDensityTokens.current
    if (isCompactWidth()) {
        val flingFix = rememberSheetFlingFix(sheetState)
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            scrimColor = scrimColor,
            dragHandle = dragHandle,
            contentWindowInsets = contentWindowInsets,
            properties = properties,
            sheetGesturesEnabled = sheetGesturesEnabled
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(flingFix)
            ) {
                content()
            }
        }
    } else {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            GlassBlurWindow()
            Surface(
                modifier = modifier
                    .widthIn(min = 280.dp, max = dialogMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.xl)
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp),
                shape = RoundedCornerShape(t.rXl),
                color = containerColor,
                contentColor = contentColor,
                tonalElevation = tonalElevation
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md)
                ) {
                    content()
                }
            }
        }
    }
}
