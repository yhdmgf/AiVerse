package com.aiverse.core.util

import android.content.Context
import java.util.Locale

/**
 * 应用语言在 JVM 默认 Locale 上的同步。
 *
 * `createConfigurationContext` 与 `resources.updateConfiguration` 只覆盖 Activity / Application
 * 的 Resources 配置，不会改动 [Locale.getDefault]；清单多语言文本、日期/数字格式化等读取默认
 * Locale 的链路必须在这里同步，否则切换语言后仍按进程启动时的旧语言取值，切回原语言也不回退。
 */
internal object AppLocale {
    private const val PREFS_NAME = "language_prefs_sync"
    private const val PREFS_KEY = "language_tag"

    /** 进程启动时的系统默认 Locale；首次读取后缓存，跟随系统时据此还原。 */
    @Volatile
    private var systemLocale: Locale? = null

    /** 读取持久化的语言标签（attachBaseContext 阶段 Hilt 尚未注入，直接读同步 prefs）。 */
    fun storedTag(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREFS_KEY, null)

    /** 按持久化的语言标签同步 JVM 默认 Locale；tag 为空表示跟随系统。 */
    fun syncDefault(tag: String?) {
        val target = if (tag.isNullOrBlank()) systemLocale() else Locale.forLanguageTag(tag)
        Locale.setDefault(target)
    }

    private fun systemLocale(): Locale {
        return systemLocale ?: Locale.getDefault().also { systemLocale = it }
    }
}
