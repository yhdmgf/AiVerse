package com.aiverse.feature.agent.data.repository

import android.content.Context

/**
 * 子代理状态横幅「关闭（×）」的持久化：
 * 记录用户在每个父会话里点过 × 隐藏的子会话 id。仅隐藏状态横幅显示，
 * 不删除任何子代理数据，也不影响侧边栏会话列表（子代理会话仍可从侧边栏查看）。
 *
 * 按父会话分组存储：key 形如 `subagent_dismissed_<parentSessionId>`，
 * value 为该父会话下已关闭的子会话 id 集合（StringSet）。重启 App、切换父会话后
 * 读取该集合过滤，已关闭的子代理不再冒出。
 *
 * 存储用 SharedPreferences（与 feature/settings 的 InputBarSettingsRepository 同口径，简单、按父会话分 key 自然）。
 * 读取 [dismissed] 为同步磁盘读，调用方应在 IO 协程里调用；写入 [dismiss] 用 apply 异步落盘，不阻塞调用线程。
 */
class SubAgentDismissPrefsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 某父会话下已关闭的子会话 id 集合（防御性拷贝，调用方可安全持有）。 */
    fun dismissed(parentSessionId: String): Set<String> =
        prefs.getStringSet(KEY_PREFIX + parentSessionId, emptySet()).orEmpty().toSet()

    /** 追加关闭若干子会话 id（合并去重后立即持久化，apply 异步落盘）。 */
    fun dismiss(parentSessionId: String, subSessionIds: Collection<String>) {
        if (subSessionIds.isEmpty()) return
        val merged = dismissed(parentSessionId) + subSessionIds
        prefs.edit().putStringSet(KEY_PREFIX + parentSessionId, merged).apply()
    }

    private companion object {
        const val PREFS_NAME = "subagent_dismiss_prefs"
        const val KEY_PREFIX = "subagent_dismissed_"
    }
}
