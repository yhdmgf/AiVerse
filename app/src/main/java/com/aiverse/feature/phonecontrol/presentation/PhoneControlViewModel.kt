package com.aiverse.feature.phonecontrol.presentation

import com.aiverse.R
import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiverse.feature.agent.domain.shizuku.ShizukuManager
import com.aiverse.feature.agent.domain.shizuku.ShizukuState
import com.aiverse.feature.phonecontrol.data.AppRule
import com.aiverse.feature.phonecontrol.data.ConfirmMode
import com.aiverse.feature.phonecontrol.data.OverlayPrefs
import com.aiverse.feature.phonecontrol.data.PhoneControlSettings
import com.aiverse.feature.phonecontrol.data.PhoneControlSettingsRepository
import com.aiverse.feature.phonecontrol.data.PhoneUnlocks
import com.aiverse.feature.phonecontrol.domain.A11yKeeper
import com.aiverse.feature.phonecontrol.domain.PermissionGranter
import com.aiverse.feature.phonecontrol.domain.PhoneAccessibilityService
import com.aiverse.feature.phonecontrol.domain.PhoneControlStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 手机控制页 UI 状态。 */
data class PhoneControlUiState(
    val settings: PhoneControlSettings = PhoneControlSettings(),
    val recentActions: List<String> = emptyList(),
    val shizukuState: ShizukuState = ShizukuState.NOT_INSTALLED,
    val accessibilityEnabled: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val batteryExempt: Boolean = false,
    val readingScreen: Boolean = false,
    val screenText: String = ""
)

/** 「手机控制」设置页状态与操作。 */
@HiltViewModel
class PhoneControlViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: PhoneControlSettingsRepository,
    private val shizukuManager: ShizukuManager,
    private val status: PhoneControlStatus
) : ViewModel() {

    private val permissionFlags = MutableStateFlow(
        PermissionSnapshot(accessibilityEnabled = A11yKeeper.isServiceEnabled(context))
    )

    private val reading = MutableStateFlow<Pair<Boolean, String>>(false to "")

    val uiState: StateFlow<PhoneControlUiState> = combine(
        repository.settingsFlow,
        repository.recentActionsFlow,
        permissionFlags,
        reading
    ) { settings, actions, perms, read ->
        PhoneControlUiState(
            settings = settings,
            recentActions = actions,
            shizukuState = shizukuManager.state.value,
            accessibilityEnabled = perms.accessibilityEnabled,
            notificationsEnabled = perms.notificationsEnabled,
            batteryExempt = perms.batteryExempt,
            readingScreen = read.first,
            screenText = read.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhoneControlUiState())

    init {
        shizukuManager.refreshState()
        refreshPermissions()
    }

    /** 刷新系统级权限状态（从系统设置返回后调用）。 */
    fun refreshPermissions() {
        permissionFlags.value = PermissionSnapshot(
            accessibilityEnabled = A11yKeeper.isServiceEnabled(context),
            notificationsEnabled = notificationsAllowed(),
            batteryExempt = isBatteryExempt()
        )
    }

    fun setEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.setEnabled(enabled)
        log(if (enabled) context.getString(R.string.phone_control_enabled) else context.getString(R.string.phone_control_disabled))
    }

    fun setRule(packageName: String, rule: AppRule) = viewModelScope.launch {
        repository.setRule(packageName, rule)
        log(context.getString(R.string.phone_rule_applied, packageName, rule.name))
    }

    fun setConfirmMode(mode: ConfirmMode) = viewModelScope.launch {
        repository.setConfirmMode(mode)
        log(context.getString(R.string.phone_confirm_mode, mode.name))
    }

    fun setUnlocks(unlocks: PhoneUnlocks) = viewModelScope.launch {
        repository.setUnlocks(unlocks)
        log(context.getString(R.string.phone_unlock_detail, unlocks.systemPages, unlocks.secretFields, unlocks.ownApp))
    }

    fun setOverlayPrefs(overlay: OverlayPrefs) = viewModelScope.launch {
        repository.setOverlayPrefs(overlay)
    }

    fun openAccessibilitySettings() {
        // 跳转系统无障碍设置页。
        val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun requestNotificationPermission() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** 经 Shizuku/Sui 授予 WRITE_SECURE_SETTINGS 并开启无障碍服务。 */
    fun grantViaShizuku() = viewModelScope.launch {
        val granted = PermissionGranter.grantViaShizuku(shizukuManager, context.packageName).getOrDefault(false)
        if (granted) {
            A11yKeeper.enable(context)
            log(context.getString(R.string.phone_shizuku_enabled))
        } else {
            log(context.getString(R.string.phone_shizuku_failed))
        }
        refreshPermissions()
    }

    /** 经 Root 授予并开启无障碍服务。 */
    fun grantViaRoot() = viewModelScope.launch {
        val granted = PermissionGranter.grantViaRoot(context, context.packageName).getOrDefault(false)
        if (granted) {
            A11yKeeper.enable(context)
            log(context.getString(R.string.phone_root_enabled))
        } else {
            log(context.getString(R.string.phone_root_failed))
        }
        refreshPermissions()
    }

    /** adb 授权命令（供复制/展示）。 */
    fun adbCommand(): String = PermissionGranter.adbCommand(context.packageName)

    /** 直接用 A11yKeeper 开启（需已授予 WRITE_SECURE_SETTINGS）。 */
    fun enableAccessibilityNow() = viewModelScope.launch {
        val ok = A11yKeeper.enable(context)
        log(if (ok) context.getString(R.string.phone_a11y_enabled) else context.getString(R.string.phone_a11y_failed))
        refreshPermissions()
    }

    /** 关闭无障碍服务。 */
    fun disableAccessibility() = viewModelScope.launch {
        A11yKeeper.disable(context)
        refreshPermissions()
    }

    /** 读一下当前屏幕（无障碍读屏）。 */
    fun readScreen() {
        val service = PhoneAccessibilityService.current()
        if (service == null) {
            reading.value = false to context.getString(R.string.phone_a11y_not_enabled)
            return
        }
        reading.value = true to ""
        status.begin(context.getString(R.string.phone_reading_screen))
        viewModelScope.launch {
            val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                service.readScreenText()
            }
            reading.value = false to text
            status.end()
            log(context.getString(R.string.phone_screen_read, text.length))
        }
    }

    fun dismissScreenText() {
        reading.value = false to ""
    }

    /** 申请忽略电池优化（跳系统弹窗）。 */
    fun requestBatteryExempt() {
        com.aiverse.feature.phonecontrol.domain.VendorSettings.requestBatteryExempt(context)
    }

    /** 跳厂商省电策略页。 */
    fun openVendorBatterySettings() {
        com.aiverse.feature.phonecontrol.domain.VendorSettings.openVendorBatterySettings(context)
    }

    private fun notificationsAllowed(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 33) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.areNotificationsEnabled()
    }

    private fun isBatteryExempt(): Boolean =
        com.aiverse.feature.phonecontrol.domain.VendorSettings.isIgnoringBatteryOptimizations(context)

    private fun log(action: String) = viewModelScope.launch {
        repository.addRecentAction(action)
    }

    private data class PermissionSnapshot(
        val accessibilityEnabled: Boolean = false,
        val notificationsEnabled: Boolean = true,
        val batteryExempt: Boolean = false
    )
}
