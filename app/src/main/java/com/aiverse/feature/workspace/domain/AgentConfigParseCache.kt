package com.aiverse.feature.workspace.domain

/**
 * 解析结果缓存：按 [AgentConfigSnapshot.identity] + [AgentConfigSnapshot.revision] 缓存
 * 「一整套配置文件 → 解析结果」的映射，避免同一份远端配置被反复 parse。
 *
 * 远程模式下技能/子代理清单来自一套远端文件，扫描与按名查找（[AgentConfigKind]）会多次解析
 * 同一 snapshot。把解析结果按身份+修订号缓存后，连接未变、文件未变时直接复用，省掉重复 IO 与解析。
 *
 * 按 **revision**（内容指纹）而非仅 identity 判定命中：文件内容一变 revision 就变，旧缓存自动失效。
 * LRU 上限按估算字节数（[MAX_BYTES]）与条目数（[MAX_ENTRIES]）双重约束，避免大配置常驻内存。
 */
class AgentConfigParseCache<T> {
    private data class Entry<T>(val revision: String, val value: T, val bytes: Long)
    private val entries = LinkedHashMap<String, Entry<T>>(16, 0.75f, true)
    private var retainedBytes = 0L

    @Synchronized
    fun get(snapshot: AgentConfigSnapshot, parse: (Map<String, String>) -> T): T {
        entries[snapshot.identity]?.takeIf { it.revision == snapshot.revision }?.let { return it.value }
        entries.remove(snapshot.identity)?.let { retainedBytes -= it.bytes }
        val parsed = parse(snapshot.files)
        val bytes = snapshot.files.entries.sumOf { (path, text) -> (path.length.toLong() + text.length) * 2 + 512 }
        if (bytes > MAX_BYTES) return parsed
        entries[snapshot.identity] = Entry(snapshot.revision, parsed, bytes)
        retainedBytes += bytes
        val iterator = entries.entries.iterator()
        while ((entries.size > MAX_ENTRIES || retainedBytes > MAX_BYTES) && iterator.hasNext()) {
            retainedBytes -= iterator.next().value.bytes
            iterator.remove()
        }
        return parsed
    }

    private companion object {
        const val MAX_BYTES = 16L * 1024 * 1024
        const val MAX_ENTRIES = 16
    }
}
