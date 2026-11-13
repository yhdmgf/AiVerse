package com.aiverse.feature.settings.data.repository

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.aiverse.core.datastore.preferencesCorruptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.optimizeInputModelDataStore by preferencesDataStore(
    name = "optimize_input_model_prefs",
    corruptionHandler = preferencesCorruptionHandler
)

/**
 * 持久化「优化输入模型」选择（providerId + model 两字符串）。
 *
 * 「优化输入」快捷按钮把输入框内容交给模型润色后回填，默认跟随当前聊天模型；
 * 用户在此指定一个专用模型后，润色请求会临时切换到该专用模型发送，发完恢复。
 * providerId 为空（未配置）即视为「跟随当前聊天模型」。
 * DataStore 用法与 [TitleModelSettingsRepository] / [VisionModelSettingsRepository] 一致。
 */
@Singleton
class OptimizeInputModelSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) : ModelSelectionSettingsRepository(
    context.optimizeInputModelDataStore, "optimize_input_provider_id", "optimize_input_model"
) {

    /** 写入优化输入专用模型（设空字符串即等同 [clear]）。 */
    suspend fun setOptimizeInputModel(providerId: String, model: String) = setSelection(providerId, model)

    /** 清空配置——回退到「跟随当前聊天模型」。 */
    suspend fun clear() = clearSelection()

    /** 读取一次当前优化输入专用 providerId（冷读用）。 */
    suspend fun getOptimizeInputProviderId(): String = readProviderId()

    /** 读取一次当前优化输入专用 model（冷读用）。 */
    suspend fun getOptimizeInputModel(): String = readModel()
}
