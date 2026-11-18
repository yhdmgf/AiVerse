package com.aiverse.feature.agent.domain.container

import java.security.MessageDigest

/**
 * 内置文档到远程 `~/.aiverse/docs/` 的**增量**同步器：把一次全量覆盖拆成「状态文件 + 按需上传/删除」。
 *
 * 首次同步后远端会留下一个状态文件（记录每篇文档的内容哈希）。再次同步时先比对总指纹，
 * 未变化直接返回；变化时只上传内容不同的文档、删除已下线的文档。这样重连/前台回到远程模式时
 * 不必每次整篇覆盖，省流量也快。
 *
 * 同步是**可中断**的：远端阻塞 IO 不响应协程取消，靠 [checkActive] 在每步之间抛取消，
 * 并由调用方关闭任务专属 transport 唤醒阻塞读。中途断开时状态文件处于 dirty 态，
 * 下次同步会核实实际内容（保留新旧两种哈希）后补齐，不会留下半截文档。
 *
 * 所有路径先经合法性校验（见 [Companion.validatePath]），再由 [Backend] 落到临时文件后原子替换，
 * 备份旧版以便回滚。绝不原地 TRUNC 覆盖，避免并发读者看到半截内容。
 */
internal class BuiltinDocsSync(
    private val backend: Backend,
    private val checkActive: () -> Unit = {}
) {
    /** 远端文件系统后端：把同步动作落到 SFTP。实现须自行保证路径安全与原子替换。 */
    interface Backend {
        fun type(path: String): EntryType?
        fun readFile(path: String): ByteArray
        fun fileHash(path: String): String
        fun createDirectory(path: String)
        fun replaceAtomically(path: String, bytes: ByteArray)
        fun backupFile(path: String, backupPath: String, expectedHash: String)
        fun cleanupTemporary(path: String)
        fun deleteFile(path: String)
    }

    enum class EntryType { FILE, DIRECTORY, SYMLINK, OTHER }

    class Snapshot internal constructor(
        val fingerprint: String,
        internal val documents: Map<String, Document>
    )

    internal class Document(val bytes: ByteArray, val hash: String)

    data class Result(val changed: Boolean, val uploaded: Int, val deleted: Int)

    private class State(
        val ready: Boolean,
        val fingerprint: String,
        val files: Map<String, Set<String>>
    )

    fun sync(snapshot: Snapshot): Result {
        checkActive()
        backend.cleanupTemporary(STATE_FILE)
        val previous = if (ensureParents(STATE_FILE, create = false)) {
            when (type(STATE_FILE)) {
                null -> null
                EntryType.FILE -> decode(backend.readFile(STATE_FILE))
                else -> error("Unsafe built-in docs state file")
            }
        } else null
        if (previous?.ready == true && previous.fingerprint == snapshot.fingerprint) {
            return Result(changed = false, uploaded = 0, deleted = 0)
        }

        val hashes = snapshot.documents.mapValues { it.value.hash }
        val previousFiles = previous?.files.orEmpty()
        val uploads = snapshot.documents.filter { (path, doc) ->
            previous?.ready != true || previousFiles[path] != setOf(doc.hash)
        }
        val removed = previousFiles.keys - hashes.keys
        val pending = (previousFiles.keys + hashes.keys).associateWith { path ->
            previousFiles[path].orEmpty() + listOfNotNull(hashes[path])
        }
        // 中断时文件可能仍是旧版或已替换为新版；两种 hash 都保留，清理时核实实际内容。
        write(STATE_FILE, encode(State(false, snapshot.fingerprint, pending)))
        pending.keys.forEach { path ->
            checkActive()
            backend.cleanupTemporary(path)
        }
        var uploaded = 0
        uploads.forEach { (path, doc) ->
            val actualHash = existingHash(path)
            if (actualHash != null && actualHash != doc.hash && actualHash !in previousFiles[path].orEmpty()) {
                backup(path, actualHash)
            }
            if (actualHash != doc.hash) {
                write(path, doc.bytes)
                uploaded++
            }
        }
        var deleted = 0
        removed.forEach { path ->
            val actualHash = existingHash(path)
            if (actualHash != null && actualHash in previousFiles.getValue(path)) {
                checkActive()
                backend.deleteFile(path)
                deleted++
            }
        }
        write(STATE_FILE, encode(State(true, snapshot.fingerprint, hashes.mapValues { setOf(it.value) })))
        return Result(changed = true, uploaded = uploaded, deleted = deleted)
    }

    private fun backup(path: String, actualHash: String) {
        val backupPath = "$BACKUP_DIR/$actualHash/$path"
        ensureParents(backupPath, create = true)
        when (type(backupPath)) {
            null -> {
                checkActive()
                backend.backupFile(path, backupPath, actualHash)
            }
            EntryType.FILE -> check(backend.fileHash(backupPath) == actualHash) { "Built-in docs backup conflict" }
            else -> error("Unsafe built-in docs backup")
        }
    }

    private fun existingHash(path: String): String? {
        if (!ensureParents(path, create = false)) return null
        return when (type(path)) {
            null -> null
            EntryType.FILE -> {
                checkActive()
                backend.fileHash(path)
            }
            else -> error("Unsafe built-in docs file")
        }
    }

    private fun type(path: String): EntryType? {
        checkActive()
        return backend.type(path)
    }

    private fun ensureParents(path: String, create: Boolean): Boolean {
        val parts = path.split('/').dropLast(1)
        val directories = listOf("") + parts.indices.map { parts.take(it + 1).joinToString("/") }
        for (directory in directories) {
            when (type(directory)) {
                null -> {
                    if (!create) return false
                    checkActive()
                    backend.createDirectory(directory)
                    check(type(directory) == EntryType.DIRECTORY) { "Unsafe built-in docs directory" }
                }
                EntryType.DIRECTORY -> Unit
                else -> error("Unsafe built-in docs directory")
            }
        }
        return true
    }

    private fun write(path: String, bytes: ByteArray) {
        ensureParents(path, create = true)
        check(type(path).let { it == null || it == EntryType.FILE }) { "Unsafe built-in docs file" }
        checkActive()
        backend.replaceAtomically(path, bytes)
    }

    private fun encode(state: State): ByteArray = buildString {
        append(FORMAT).append('\t').append(if (state.ready) "ready" else "dirty")
            .append('\t').append(state.fingerprint).append('\n')
        state.files.toSortedMap().forEach { (path, hashes) ->
            append(hashes.sorted().joinToString(",")).append('\t').append(path).append('\n')
        }
    }.toByteArray(Charsets.UTF_8).also {
        require(it.size <= MAX_STATE_BYTES) { "Built-in docs state is too large" }
    }

    private fun decode(bytes: ByteArray): State {
        require(bytes.size <= MAX_STATE_BYTES) { "Built-in docs state is too large" }
        val text = bytes.toString(Charsets.UTF_8)
        require(text.endsWith('\n')) { "Incomplete built-in docs state" }
        val lines = text.dropLast(1).split('\n')
        val header = lines.first().split('\t')
        require(header.size == 3 && header[0] == FORMAT && header[1] in setOf("ready", "dirty") &&
            HASH.matches(header[2])) { "Invalid built-in docs state" }
        val files = linkedMapOf<String, Set<String>>()
        lines.drop(1).forEach { line ->
            val entry = line.split('\t', limit = 2)
            require(entry.size == 2) { "Invalid built-in docs entry" }
            val hashes = entry[0].split(',')
            require(hashes.all { HASH.matches(it) } && hashes.size == hashes.toSet().size) {
                "Invalid built-in docs hashes"
            }
            validatePath(entry[1])
            require(files.put(entry[1], hashes.toSet()) == null) { "Duplicate built-in docs entry" }
        }
        val ready = header[1] == "ready"
        require(!ready || files.values.all { it.size == 1 }) { "Invalid ready built-in docs hashes" }
        require(!ready || fingerprint(files.mapValues { it.value.single() }) == header[2]) {
            "Invalid built-in docs fingerprint"
        }
        return State(ready, header[2], files)
    }

    companion object {
        internal const val STATE_FILE = ".builtin-docs-state-v1"
        internal const val MAX_STATE_BYTES = 1024 * 1024
        internal const val STAGING_DIR = ".builtin-docs-staging-v1"
        internal const val BACKUP_DIR = ".builtin-docs-backups"
        private const val FORMAT = "aiverse-docs-v1"
        private val HASH = Regex("[0-9a-f]{64}")

        /** 由「相对 docs/ 路径 → 内容」构造快照：计算每篇 sha256 与整体指纹。 */
        fun snapshot(docs: Map<String, String>): Snapshot {
            val documents = docs.toSortedMap().mapValues { (path, content) ->
                validatePath(path)
                val bytes = content.toByteArray(Charsets.UTF_8)
                Document(bytes, sha256(bytes))
            }
            return Snapshot(fingerprint(documents.mapValues { it.value.hash }), documents)
        }

        internal fun temporaryPath(path: String): String =
            "$STAGING_DIR/${sha256(path.toByteArray(Charsets.UTF_8))}.part"

        private fun validatePath(path: String) {
            require(path.endsWith(".md") && !path.startsWith('/') &&
                path.substringBefore('/') !in setOf(STATE_FILE, STAGING_DIR, BACKUP_DIR) &&
                path.none { it <= '\u001f' || it == '\u007f' || it == '\\' || it == ':' } &&
                path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }) {
                "Invalid built-in docs path"
            }
        }

        private fun fingerprint(files: Map<String, String>): String = sha256(
            files.toSortedMap().entries.joinToString("") { (path, hash) -> "$path\u0000$hash\n" }
                .toByteArray(Charsets.UTF_8)
        )

        internal fun sha256(bytes: ByteArray): String =
            hexDigest(MessageDigest.getInstance("SHA-256").digest(bytes))

        internal fun hexDigest(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    }
}
