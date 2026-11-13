package com.aiverse.feature.settings.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiverse.core.datastore.preferencesCorruptionHandler
import com.aiverse.core.theme.FileNameDisplay
import com.aiverse.core.theme.FileTreeDisplayPrefs
import com.aiverse.core.theme.FileTreeIndentDensity
import com.aiverse.core.theme.UIDensity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appearanceDataStore by preferencesDataStore(
    name = "appearance_settings",
    corruptionHandler = preferencesCorruptionHandler
)

/**
 * 持久化外观文字大小设置（「外观设置」二级页）：
 * - 聊天字体大小：聊天气泡 / 消息正文 / Markdown 文本字号（sp）
 * - 代码块字体大小：Markdown 代码块/行内代码字号（sp），与聊天字号分开调
 * - 输入框字体大小：底部聊天输入框字号（sp）
 * - 界面字体大小：全局 UI 缩放系数（1.0 为不缩放）
 * - 代码显示：自动换行 / 代码块自动折叠 / 显示行号
 * - 气泡透明度：用户与 AI 两侧气泡底色透明度（10~100%）
 *
 * 均持久化，App 重启后自动恢复；范围在 companion 中约束。
 */
@Singleton
class AppearanceSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    val chatFontSizeSpFlow: Flow<Int> = context.appearanceDataStore.data.map { prefs ->
        (prefs[CHAT_FONT_SIZE_KEY] ?: DEFAULT_CHAT_FONT_SIZE_SP).coerceIn(MIN_CHAT_FONT_SIZE_SP, MAX_CHAT_FONT_SIZE_SP)
    }

    val inputFontSizeSpFlow: Flow<Int> = context.appearanceDataStore.data.map { prefs ->
        (prefs[INPUT_FONT_SIZE_KEY] ?: DEFAULT_INPUT_FONT_SIZE_SP).coerceIn(MIN_INPUT_FONT_SIZE_SP, MAX_INPUT_FONT_SIZE_SP)
    }

    val uiFontScaleFlow: Flow<Float> = context.appearanceDataStore.data.map { prefs ->
        (prefs[UI_FONT_SCALE_KEY] ?: DEFAULT_UI_FONT_SCALE).coerceIn(MIN_UI_FONT_SCALE, MAX_UI_FONT_SCALE)
    }

    /** 界面密度档（紧凑/标准/舒适），作用于间距、圆角、组件高度（dp）缩放。默认标准档。 */
    val uiDensityFlow: Flow<UIDensity> = context.appearanceDataStore.data.map { prefs ->
        UIDensity.fromId(prefs[UI_DENSITY_KEY] ?: UIDensity.COMPACT.id)
    }

    /** 卡片圆角（dp，0~28），默认 12；与密度档叠加（标准档不缩放）。 */
    val cardCornerFlow: Flow<Int> = context.appearanceDataStore.data.map { prefs ->
        (prefs[CARD_CORNER_KEY] ?: DEFAULT_CARD_CORNER).coerceIn(MIN_CORNER, MAX_CORNER)
    }

    /** 按钮圆角（dp，0~28），默认 10。 */
    val buttonCornerFlow: Flow<Int> = context.appearanceDataStore.data.map { prefs ->
        (prefs[BUTTON_CORNER_KEY] ?: DEFAULT_BUTTON_CORNER).coerceIn(MIN_CORNER, MAX_CORNER)
    }

    /** 输入框圆角（dp，0~28），默认 12。 */
    val fieldCornerFlow: Flow<Int> = context.appearanceDataStore.data.map { prefs ->
        (prefs[FIELD_CORNER_KEY] ?: DEFAULT_FIELD_CORNER).coerceIn(MIN_CORNER, MAX_CORNER)
    }

    /** 聊天气泡圆角（dp，0~28），默认 10；仅作用于非清新绿样式（清新绿保持品牌 24dp）。 */
    val bubbleCornerFlow: Flow<Int> = context.appearanceDataStore.data.map { prefs ->
        (prefs[BUBBLE_CORNER_KEY] ?: DEFAULT_BUBBLE_CORNER).coerceIn(MIN_CORNER, MAX_CORNER)
    }

    /** 图片/附件圆角（dp，0~28），默认 12。 */
    val imageCornerFlow: Flow<Int> = context.appearanceDataStore.data.map { prefs ->
        (prefs[IMAGE_CORNER_KEY] ?: DEFAULT_IMAGE_CORNER).coerceIn(MIN_CORNER, MAX_CORNER)
    }

    /** 侧边栏文件树显示偏好：文件名显示 / 显示隐藏文件 / 缩进密度 / 弱化着色，默认与历史行为一致。 */
    val fileTreeDisplayPrefsFlow: Flow<FileTreeDisplayPrefs> = context.appearanceDataStore.data.map { prefs ->
        FileTreeDisplayPrefs(
            fileNameDisplay = FileNameDisplay.fromId(prefs[FILE_NAME_DISPLAY_KEY]),
            showHiddenFiles = prefs[SHOW_HIDDEN_FILES_KEY] ?: FileTreeDisplayPrefs.Default.showHiddenFiles,
            indentDensity = FileTreeIndentDensity.fromId(prefs[FILE_INDENT_DENSITY_KEY]),
            mutedColoring = prefs[FILE_MUTED_COLORING_KEY] ?: FileTreeDisplayPrefs.Default.mutedColoring
        )
    }

    /** 代码块字体大小（sp）：与聊天字号分开调，默认跟随现有代码块字号 13sp。 */
    val codeFontSizeSpFlow: Flow<Int> = context.appearanceDataStore.data.map { prefs ->
        (prefs[CODE_FONT_SIZE_KEY] ?: DEFAULT_CODE_FONT_SIZE_SP).coerceIn(MIN_CODE_FONT_SIZE_SP, MAX_CODE_FONT_SIZE_SP)
    }

    /** 代码块自动换行：开=行内软换行（不横向滚动）；关=横向滚动（默认关）。 */
    val codeWrapEnabledFlow: Flow<Boolean> =
        context.appearanceDataStore.data.map { prefs -> prefs[CODE_WRAP_KEY] ?: DEFAULT_CODE_WRAP_ENABLED }

    /** 代码块自动折叠：超过阈值行数时折叠，可展开（默认开）。 */
    val codeAutoFoldEnabledFlow: Flow<Boolean> =
        context.appearanceDataStore.data.map { prefs -> prefs[CODE_AUTO_FOLD_KEY] ?: DEFAULT_CODE_AUTO_FOLD_ENABLED }

    /** 代码块显示行号（默认开）。 */
    val codeShowLineNumberFlow: Flow<Boolean> =
        context.appearanceDataStore.data.map { prefs -> prefs[CODE_LINE_NUMBER_KEY] ?: DEFAULT_CODE_SHOW_LINE_NUMBER }

    /** 用户气泡底色透明度（10~100%），默认 100。 */
    val userBubbleOpacityFlow: Flow<Int> = context.appearanceDataStore.data.map { prefs ->
        (prefs[USER_BUBBLE_OPACITY_KEY] ?: DEFAULT_BUBBLE_OPACITY).coerceIn(MIN_BUBBLE_OPACITY, MAX_BUBBLE_OPACITY)
    }

    /** AI 回复气泡底色透明度（10~100%），默认 100。 */
    val assistantBubbleOpacityFlow: Flow<Int> = context.appearanceDataStore.data.map { prefs ->
        (prefs[ASSISTANT_BUBBLE_OPACITY_KEY] ?: DEFAULT_BUBBLE_OPACITY).coerceIn(MIN_BUBBLE_OPACITY, MAX_BUBBLE_OPACITY)
    }

    suspend fun setChatFontSizeSp(sizeSp: Int) {
        context.appearanceDataStore.edit { it[CHAT_FONT_SIZE_KEY] = sizeSp.coerceIn(MIN_CHAT_FONT_SIZE_SP, MAX_CHAT_FONT_SIZE_SP) }
    }

    suspend fun setInputFontSizeSp(sizeSp: Int) {
        context.appearanceDataStore.edit { it[INPUT_FONT_SIZE_KEY] = sizeSp.coerceIn(MIN_INPUT_FONT_SIZE_SP, MAX_INPUT_FONT_SIZE_SP) }
    }

    suspend fun setUiFontScale(scale: Float) {
        context.appearanceDataStore.edit { it[UI_FONT_SCALE_KEY] = scale.coerceIn(MIN_UI_FONT_SCALE, MAX_UI_FONT_SCALE) }
    }

    /** 设置界面密度档。 */
    suspend fun setUiDensity(density: UIDensity) {
        context.appearanceDataStore.edit { it[UI_DENSITY_KEY] = density.id }
    }

    /** 设置卡片圆角（dp）。 */
    suspend fun setCardCorner(corner: Int) {
        context.appearanceDataStore.edit { it[CARD_CORNER_KEY] = corner.coerceIn(MIN_CORNER, MAX_CORNER) }
    }

    /** 设置按钮圆角（dp）。 */
    suspend fun setButtonCorner(corner: Int) {
        context.appearanceDataStore.edit { it[BUTTON_CORNER_KEY] = corner.coerceIn(MIN_CORNER, MAX_CORNER) }
    }

    /** 设置输入框圆角（dp）。 */
    suspend fun setFieldCorner(corner: Int) {
        context.appearanceDataStore.edit { it[FIELD_CORNER_KEY] = corner.coerceIn(MIN_CORNER, MAX_CORNER) }
    }

    /** 设置聊天气泡圆角（dp）。 */
    suspend fun setBubbleCorner(corner: Int) {
        context.appearanceDataStore.edit { it[BUBBLE_CORNER_KEY] = corner.coerceIn(MIN_CORNER, MAX_CORNER) }
    }

    /** 设置图片/附件圆角（dp）。 */
    suspend fun setImageCorner(corner: Int) {
        context.appearanceDataStore.edit { it[IMAGE_CORNER_KEY] = corner.coerceIn(MIN_CORNER, MAX_CORNER) }
    }

    /** 设置文件树文件名显示方式。 */
    suspend fun setFileNameDisplay(display: FileNameDisplay) {
        context.appearanceDataStore.edit { it[FILE_NAME_DISPLAY_KEY] = display.id }
    }

    /** 设置是否显示隐藏文件。 */
    suspend fun setShowHiddenFiles(show: Boolean) {
        context.appearanceDataStore.edit { it[SHOW_HIDDEN_FILES_KEY] = show }
    }

    /** 设置文件树缩进密度。 */
    suspend fun setFileIndentDensity(density: FileTreeIndentDensity) {
        context.appearanceDataStore.edit { it[FILE_INDENT_DENSITY_KEY] = density.id }
    }

    /** 设置是否弱化着色 .gitignore / dotfile。 */
    suspend fun setFileMutedColoring(muted: Boolean) {
        context.appearanceDataStore.edit { it[FILE_MUTED_COLORING_KEY] = muted }
    }

    /** 设置代码块字体大小（sp）。 */
    suspend fun setCodeFontSizeSp(sizeSp: Int) {
        context.appearanceDataStore.edit { it[CODE_FONT_SIZE_KEY] = sizeSp.coerceIn(MIN_CODE_FONT_SIZE_SP, MAX_CODE_FONT_SIZE_SP) }
    }

    /** 设置代码块自动换行。 */
    suspend fun setCodeWrapEnabled(enabled: Boolean) {
        context.appearanceDataStore.edit { it[CODE_WRAP_KEY] = enabled }
    }

    /** 设置代码块自动折叠。 */
    suspend fun setCodeAutoFoldEnabled(enabled: Boolean) {
        context.appearanceDataStore.edit { it[CODE_AUTO_FOLD_KEY] = enabled }
    }

    /** 设置代码块显示行号。 */
    suspend fun setCodeShowLineNumber(enabled: Boolean) {
        context.appearanceDataStore.edit { it[CODE_LINE_NUMBER_KEY] = enabled }
    }

    /** 设置用户气泡透明度（10~100%）。 */
    suspend fun setUserBubbleOpacity(opacity: Int) {
        context.appearanceDataStore.edit { it[USER_BUBBLE_OPACITY_KEY] = opacity.coerceIn(MIN_BUBBLE_OPACITY, MAX_BUBBLE_OPACITY) }
    }

    /** 设置 AI 回复气泡透明度（10~100%）。 */
    suspend fun setAssistantBubbleOpacity(opacity: Int) {
        context.appearanceDataStore.edit { it[ASSISTANT_BUBBLE_OPACITY_KEY] = opacity.coerceIn(MIN_BUBBLE_OPACITY, MAX_BUBBLE_OPACITY) }
    }

    /** 备份快照：当前聊天字号（sp）。 */
    suspend fun chatFontSizeSnapshot(): Int = chatFontSizeSpFlow.first()

    /** 备份快照：当前输入框字号（sp）。 */
    suspend fun inputFontSizeSnapshot(): Int = inputFontSizeSpFlow.first()

    /** 备份快照：当前界面缩放系数。 */
    suspend fun uiFontScaleSnapshot(): Float = uiFontScaleFlow.first()

    /** 备份快照：当前界面密度档 id（未设置时回退标准档）。 */
    suspend fun uiDensitySnapshot(): String = uiDensityFlow.first().id

    /** 备份快照：当前代码块字号（sp）。 */
    suspend fun codeFontSizeSnapshot(): Int = codeFontSizeSpFlow.first()

    /** 备份快照：当前代码块自动换行。 */
    suspend fun codeWrapSnapshot(): Boolean = codeWrapEnabledFlow.first()

    /** 备份快照：当前代码块自动折叠。 */
    suspend fun codeAutoFoldSnapshot(): Boolean = codeAutoFoldEnabledFlow.first()

    /** 备份快照：当前代码块显示行号。 */
    suspend fun codeShowLineNumberSnapshot(): Boolean = codeShowLineNumberFlow.first()

    /** 备份快照：当前用户气泡透明度（%）。 */
    suspend fun userBubbleOpacitySnapshot(): Int = userBubbleOpacityFlow.first()

    /** 备份快照：当前 AI 气泡透明度（%）。 */
    suspend fun assistantBubbleOpacitySnapshot(): Int = assistantBubbleOpacityFlow.first()

    /** 从备份还原三档字号与密度档；任一传 null 时清除该键回退默认。 */
    suspend fun restoreFontSizes(chatSp: Int?, inputSp: Int?, uiScale: Float?, uiDensity: String? = null) {
        context.appearanceDataStore.edit {
            if (chatSp == null) it.remove(CHAT_FONT_SIZE_KEY) else it[CHAT_FONT_SIZE_KEY] = chatSp.coerceIn(MIN_CHAT_FONT_SIZE_SP, MAX_CHAT_FONT_SIZE_SP)
            if (inputSp == null) it.remove(INPUT_FONT_SIZE_KEY) else it[INPUT_FONT_SIZE_KEY] = inputSp.coerceIn(MIN_INPUT_FONT_SIZE_SP, MAX_INPUT_FONT_SIZE_SP)
            if (uiScale == null) it.remove(UI_FONT_SCALE_KEY) else it[UI_FONT_SCALE_KEY] = uiScale.coerceIn(MIN_UI_FONT_SCALE, MAX_UI_FONT_SCALE)
            if (uiDensity == null) it.remove(UI_DENSITY_KEY) else it[UI_DENSITY_KEY] = uiDensity
        }
    }

    /**
     * 从备份还原代码显示与气泡透明度；任一传 null 时清除该键回退默认。
     * 供备份导入与「恢复默认」共用：全 null 即全部回退默认。
     */
    suspend fun restoreCodeDisplay(
        codeSp: Int?,
        codeWrap: Boolean?,
        codeAutoFold: Boolean?,
        codeLineNumber: Boolean?,
        userOpacity: Int?,
        assistantOpacity: Int?
    ) {
        context.appearanceDataStore.edit {
            if (codeSp == null) it.remove(CODE_FONT_SIZE_KEY) else it[CODE_FONT_SIZE_KEY] = codeSp.coerceIn(MIN_CODE_FONT_SIZE_SP, MAX_CODE_FONT_SIZE_SP)
            if (codeWrap == null) it.remove(CODE_WRAP_KEY) else it[CODE_WRAP_KEY] = codeWrap
            if (codeAutoFold == null) it.remove(CODE_AUTO_FOLD_KEY) else it[CODE_AUTO_FOLD_KEY] = codeAutoFold
            if (codeLineNumber == null) it.remove(CODE_LINE_NUMBER_KEY) else it[CODE_LINE_NUMBER_KEY] = codeLineNumber
            if (userOpacity == null) it.remove(USER_BUBBLE_OPACITY_KEY) else it[USER_BUBBLE_OPACITY_KEY] = userOpacity.coerceIn(MIN_BUBBLE_OPACITY, MAX_BUBBLE_OPACITY)
            if (assistantOpacity == null) it.remove(ASSISTANT_BUBBLE_OPACITY_KEY) else it[ASSISTANT_BUBBLE_OPACITY_KEY] = assistantOpacity.coerceIn(MIN_BUBBLE_OPACITY, MAX_BUBBLE_OPACITY)
        }
    }

    /** 清空文件树显示偏好，回退默认（与历史行为一致）。 */
    suspend fun restoreFileTreeDisplay() {
        context.appearanceDataStore.edit {
            it.remove(FILE_NAME_DISPLAY_KEY)
            it.remove(SHOW_HIDDEN_FILES_KEY)
            it.remove(FILE_INDENT_DENSITY_KEY)
            it.remove(FILE_MUTED_COLORING_KEY)
        }
    }

    companion object {
        private val CHAT_FONT_SIZE_KEY = intPreferencesKey("chat_font_size_sp")
        private val INPUT_FONT_SIZE_KEY = intPreferencesKey("input_font_size_sp")
        private val UI_FONT_SCALE_KEY = floatPreferencesKey("ui_font_scale")
        private val UI_DENSITY_KEY = stringPreferencesKey("ui_density")
        private val FILE_NAME_DISPLAY_KEY = stringPreferencesKey("file_name_display")
        private val SHOW_HIDDEN_FILES_KEY = booleanPreferencesKey("file_show_hidden")
        private val FILE_INDENT_DENSITY_KEY = stringPreferencesKey("file_indent_density")
        private val FILE_MUTED_COLORING_KEY = booleanPreferencesKey("file_muted_coloring")
        private val CODE_FONT_SIZE_KEY = intPreferencesKey("code_font_size_sp")
        private val CODE_WRAP_KEY = booleanPreferencesKey("code_wrap_enabled")
        private val CODE_AUTO_FOLD_KEY = booleanPreferencesKey("code_auto_fold_enabled")
        private val CODE_LINE_NUMBER_KEY = booleanPreferencesKey("code_show_line_number")
        private val USER_BUBBLE_OPACITY_KEY = intPreferencesKey("user_bubble_opacity")
        private val ASSISTANT_BUBBLE_OPACITY_KEY = intPreferencesKey("assistant_bubble_opacity")
        private val CARD_CORNER_KEY = intPreferencesKey("card_corner")
        private val BUTTON_CORNER_KEY = intPreferencesKey("button_corner")
        private val FIELD_CORNER_KEY = intPreferencesKey("field_corner")
        private val BUBBLE_CORNER_KEY = intPreferencesKey("bubble_corner")
        private val IMAGE_CORNER_KEY = intPreferencesKey("image_corner")

        const val MIN_CHAT_FONT_SIZE_SP = 12
        const val MAX_CHAT_FONT_SIZE_SP = 22
        const val DEFAULT_CHAT_FONT_SIZE_SP = 16

        const val MIN_INPUT_FONT_SIZE_SP = 12
        const val MAX_INPUT_FONT_SIZE_SP = 22
        const val DEFAULT_INPUT_FONT_SIZE_SP = 16

        const val MIN_UI_FONT_SCALE = 0.85f
        const val MAX_UI_FONT_SCALE = 1.30f
        const val DEFAULT_UI_FONT_SCALE = 1.0f

        /** 代码块字号：比聊天字号（12~22）略小一档，默认跟随现有代码块字号 13sp。 */
        const val MIN_CODE_FONT_SIZE_SP = 10
        const val MAX_CODE_FONT_SIZE_SP = 22
        const val DEFAULT_CODE_FONT_SIZE_SP = 13

        const val DEFAULT_CODE_WRAP_ENABLED = false
        const val DEFAULT_CODE_AUTO_FOLD_ENABLED = true
        const val DEFAULT_CODE_SHOW_LINE_NUMBER = true

        /** 气泡透明度（%）：10 太透看不清文字，100 为完全不透明。 */
        const val MIN_BUBBLE_OPACITY = 10
        const val MAX_BUBBLE_OPACITY = 100
        const val DEFAULT_BUBBLE_OPACITY = 100

        /** 独立圆角（dp）：卡片/按钮/输入框/气泡/图片 5 项可分别调，默认值=现状。 */
        const val MIN_CORNER = 0
        const val MAX_CORNER = 28
        const val DEFAULT_CARD_CORNER = 12
        const val DEFAULT_BUTTON_CORNER = 10
        const val DEFAULT_FIELD_CORNER = 12
        const val DEFAULT_BUBBLE_CORNER = 10
        const val DEFAULT_IMAGE_CORNER = 12
    }
}
