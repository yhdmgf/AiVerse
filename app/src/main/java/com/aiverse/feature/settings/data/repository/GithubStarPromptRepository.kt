package com.aiverse.feature.settings.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「关于页 GitHub Star 提示卡」持久化：记录用户是否勾选了「不再提示」。
 *
 * 用 SharedPreferences（键值文件，非数据库）存储，与 [UpdateCheckSettingsRepository] 同模式。
 * 只有一个布尔状态：dismissed=true 后关于页不再展示 Star 提示卡。
 */
@Singleton
class GithubStarPromptRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 用户是否已勾选「不再提示」，默认 false（首次仍展示提示卡）。 */
    var dismissed: Boolean
        get() = prefs.getBoolean(KEY_DISMISSED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DISMISSED, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "github_star_prompt_prefs"
        const val KEY_DISMISSED = "star_prompt_dismissed"
    }
}
