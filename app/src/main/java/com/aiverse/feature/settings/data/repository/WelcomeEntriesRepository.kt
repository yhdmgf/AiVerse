package com.aiverse.feature.settings.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import com.aiverse.feature.settings.domain.model.VECTOR_ICON_DEFAULT
import com.aiverse.feature.settings.domain.model.WelcomeEntry
import com.aiverse.feature.settings.domain.model.WelcomeIconKind
import com.aiverse.feature.settings.domain.model.buildDefaultWelcomeEntries
import com.aiverse.feature.settings.domain.model.defaultWelcomeEntries
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 新对话页可配置快捷入口（欢迎卡片）的存储与增删改。
 *
 * 单一真源：[entries] StateFlow；设置页与对话页订阅同一流，改动即时同步。
 * 持久化：SharedPreferences（[PREFS_NAME]）里存 JSON 列表（kotlinx.serialization）。
 * 自定义图片图标经 [saveIconImage] 落到 filesDir/[ICON_DIR]/ 下（不依赖 content URI 授权），
 * 条目里只存绝对路径；删除条目/恢复默认时同步清理图片文件。
 */
@Singleton
class WelcomeEntriesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 列表 JSON 编解码：忽略未知字段，便于向前兼容。 */
    private val json = Json { ignoreUnknownKeys = true }

    private val _entries = MutableStateFlow(loadFromPrefs())

    /** 全部欢迎卡片快照（实时，含预置项与自定义项，按展示顺序）。 */
    val entries: StateFlow<List<WelcomeEntry>> = _entries.asStateFlow()

    /** 当前快照。 */
    fun load(): List<WelcomeEntry> = _entries.value

    /**
     * 新增欢迎卡片。
     *
     * 达到 [MAX_ENTRIES] 上限时拒绝并返回 false（调用方可据此提示）；否则追加到末尾并返回 true。
     * 传空 id 时自动生成 UUID。
     */
    suspend fun add(entry: WelcomeEntry): Boolean {
        val current = _entries.value
        if (current.size >= MAX_ENTRIES) return false
        val normalized = if (entry.id.isBlank()) entry.copy(id = UUID.randomUUID().toString()) else entry
        persist(current + normalized)
        return true
    }

    /** 更新欢迎卡片（按 id 匹配；id 不存在时忽略）。 */
    suspend fun update(entry: WelcomeEntry) {
        val current = _entries.value
        if (current.none { it.id == entry.id }) return
        persist(current.map { if (it.id == entry.id) entry else it })
    }

    /** 删除欢迎卡片（按 id）；条目为图片图标时同步删除图片文件。 */
    suspend fun remove(id: String) {
        val current = _entries.value
        current.firstOrNull { it.id == id }?.let { deleteIconIfImage(it) }
        persist(current.filterNot { it.id == id })
    }

    /** 启用/停用欢迎卡片（按 id）。 */
    suspend fun setEnabled(id: String, enabled: Boolean) {
        val current = _entries.value
        persist(current.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    /**
     * 移动欢迎卡片到目标索引（0 起，上下移）。
     * [toIndex] 越界时夹到合法区间；id 不存在或位置不变时忽略。
     */
    suspend fun move(id: String, toIndex: Int) {
        val current = _entries.value
        val from = current.indexOfFirst { it.id == id }
        if (from < 0) return
        val target = toIndex.coerceIn(0, current.lastIndex)
        if (from == target) return
        val mutable = current.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(target, item)
        persist(mutable)
    }

    /**
     * 恢复默认：清空（含删除所有图片图标文件）后写入 [defaultWelcomeEntries]。
     * 用户自定义项被移除，预置项按默认排序重建。
     */
    suspend fun restoreDefaults() {
        _entries.value.forEach { deleteIconIfImage(it) }
        persist(defaultWelcomeEntries(context))
    }

    /**
     * 把所选图片保存为欢迎卡片图标：解码（采样控内存）→ 按最长边 [MAX_EDGE] 降采样 →
     * 重编码 JPEG → 写 filesDir/[ICON_DIR]/icon_<ts>.jpg。
     *
     * 返回写入文件的绝对路径（供存入 [WelcomeEntry.iconValue]）；失败（读取/解码/写盘任一环节）返回 null。
     * 统一转 JPEG：图标不需要透明通道，且避免大 PNG 常驻内存。
     */
    suspend fun saveIconImage(uri: Uri): String? {
        val bytes = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
            }.getOrNull()
        } ?: return null
        val encoded = withContext(Dispatchers.IO) {
            runCatching { normalizeToJpeg(bytes) }.getOrNull()
        } ?: return null
        val dir = File(context.filesDir, ICON_DIR)
        dir.mkdirs()
        val target = File(dir, "icon_${System.currentTimeMillis()}.jpg")
        val written = withContext(Dispatchers.IO) {
            runCatching {
                target.outputStream().use { output -> output.write(encoded) }
                target.length() > 0
            }.getOrDefault(false)
        }
        return if (written) target.absolutePath else null
    }

    /** 首次加载：无数据（或解析失败）时回退到预置项；否则返回持久化列表（含旧 emoji 数据迁移）。 */
    private fun loadFromPrefs(): List<WelcomeEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null)
        if (raw.isNullOrBlank()) return buildDefaultWelcomeEntries(context)
        return runCatching { json.decodeFromString<List<WelcomeEntry>>(raw) }
            .map { migrateLegacyEntries(it) }
            .getOrElse { buildDefaultWelcomeEntries(context) }
    }

    /**
     * 旧数据兼容：历史版本允许用户自定义入口使用 emoji 图标；现统一为 Feather 矢量图标，
     * 无法语义映射的 emoji 一律回退到 [VECTOR_ICON_DEFAULT]。
     */
    private fun migrateLegacyEntries(list: List<WelcomeEntry>): List<WelcomeEntry> =
        list.map { entry ->
            if (entry.iconKind == WelcomeIconKind.EMOJI) {
                entry.copy(iconKind = WelcomeIconKind.VECTOR, iconValue = VECTOR_ICON_DEFAULT)
            } else {
                entry
            }
        }

    /** 持久化 + 发布（单一真源）。 */
    private fun persist(list: List<WelcomeEntry>) {
        prefs.edit().putString(KEY_ENTRIES, json.encodeToString(list)).apply()
        _entries.value = list
    }

    /** 条目为图片图标时删除对应图片文件（忽略失败，避免残留文件阻塞流程）。 */
    private fun deleteIconIfImage(entry: WelcomeEntry) {
        if (entry.iconKind == WelcomeIconKind.IMAGE && entry.iconValue.isNotBlank()) {
            runCatching { File(entry.iconValue).delete() }
        }
    }

    /**
     * 把原图字节规整成最长边不超过 [MAX_EDGE] 的 JPEG。
     * 先按采样档位解码控峰值内存，再用 Matrix 精确缩放到目标尺寸（不改方向，源图按原方向落盘）。
     */
    private fun normalizeToJpeg(source: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("无法读取图片尺寸")
        }
        var sample = 1
        while (sample < 32 &&
            (bounds.outWidth / (sample * 2) >= MAX_EDGE || bounds.outHeight / (sample * 2) >= MAX_EDGE ||
                bounds.outWidth.toLong() * bounds.outHeight / (sample.toLong() * sample) > DECODE_MAX_PIXELS)
        ) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            source, 0, source.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: throw IllegalArgumentException("图片解码失败")
        val longest = maxOf(decoded.width, decoded.height)
        val scale = if (longest > MAX_EDGE) MAX_EDGE.toFloat() / longest else 1f
        val bitmap = if (scale < 1f) {
            val matrix = Matrix().apply { postScale(scale, scale) }
            val scaled = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (scaled != decoded) decoded.recycle()
            scaled
        } else {
            decoded
        }
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        bitmap.recycle()
        return output.toByteArray()
    }

    private companion object {
        /** 欢迎卡片数量上限（含预置项与自定义项）。 */
        const val MAX_ENTRIES = 6

        const val PREFS_NAME = "welcome_entries_prefs"
        const val KEY_ENTRIES = "welcome_entries_json"

        /** 图片图标落盘子目录（位于 filesDir 下）。 */
        const val ICON_DIR = "welcome_icons"

        /** 落盘图标最长边（像素）：卡片图标显示尺寸小，无需高分辨率。 */
        const val MAX_EDGE = 512
        const val DECODE_MAX_PIXELS = 4_000_000L
        const val JPEG_QUALITY = 85
    }
}
