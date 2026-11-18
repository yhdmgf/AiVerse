package com.aiverse.feature.agent.domain.container

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置文档增量同步器 [BuiltinDocsSync] 的单测：用内存假后端验证
 * 首次全量上传、内容未变短路、单篇修改只传差异、下线文档删除，以及快照指纹稳定性。
 */
class BuiltinDocsSyncTest {

    /** 内存文件系统后端：用 Map 模拟远端文件/目录，供同步器落盘。 */
    private class FakeBackend : BuiltinDocsSync.Backend {
        val files = LinkedHashMap<String, ByteArray>()
        val dirs = LinkedHashSet<String>()

        override fun type(path: String): BuiltinDocsSync.EntryType? = when {
            path.isEmpty() -> BuiltinDocsSync.EntryType.DIRECTORY
            files.containsKey(path) -> BuiltinDocsSync.EntryType.FILE
            dirs.contains(path) -> BuiltinDocsSync.EntryType.DIRECTORY
            else -> null
        }

        override fun readFile(path: String): ByteArray = files.getValue(path)
        override fun fileHash(path: String): String = BuiltinDocsSync.sha256(files.getValue(path))
        override fun createDirectory(path: String) { dirs.add(path) }
        override fun replaceAtomically(path: String, bytes: ByteArray) { files[path] = bytes }
        override fun backupFile(path: String, backupPath: String, expectedHash: String) {
            files[backupPath] = files.getValue(path).copyOf()
        }

        override fun cleanupTemporary(path: String) {
            files.keys.removeAll { it.startsWith(BuiltinDocsSync.STAGING_DIR) }
        }

        override fun deleteFile(path: String) { files.remove(path) }
    }

    @Test
    fun firstSync_uploadsAllDocuments() {
        val backend = FakeBackend()
        val sync = BuiltinDocsSync(backend)
        val snapshot = BuiltinDocsSync.snapshot(mapOf("a.md" to "A", "guide/b.md" to "B"))

        val result = sync.sync(snapshot)

        assertTrue(result.changed)
        assertEquals(2, result.uploaded)
        assertEquals(0, result.deleted)
        assertEquals("A", String(backend.files["a.md"]!!))
        assertEquals("B", String(backend.files["guide/b.md"]!!))
        // 状态文件已就绪
        assertTrue(backend.files.containsKey(BuiltinDocsSync.STATE_FILE))
    }

    @Test
    fun secondSyncWithSameContent_isNoOp() {
        val backend = FakeBackend()
        val sync = BuiltinDocsSync(backend)
        val snapshot = BuiltinDocsSync.snapshot(mapOf("a.md" to "A", "guide/b.md" to "B"))

        sync.sync(snapshot)
        val second = sync.sync(snapshot)

        assertFalse(second.changed)
        assertEquals(0, second.uploaded)
        assertEquals(0, second.deleted)
    }

    @Test
    fun modifiedDocument_uploadsOnlyChangedOne() {
        val backend = FakeBackend()
        val sync = BuiltinDocsSync(backend)
        sync.sync(BuiltinDocsSync.snapshot(mapOf("a.md" to "A", "b.md" to "B")))

        val result = sync.sync(BuiltinDocsSync.snapshot(mapOf("a.md" to "A2", "b.md" to "B")))

        assertTrue(result.changed)
        assertEquals(1, result.uploaded)
        assertEquals(0, result.deleted)
        assertEquals("A2", String(backend.files["a.md"]!!))
        assertEquals("B", String(backend.files["b.md"]!!))
    }

    @Test
    fun removedDocument_isDeleted() {
        val backend = FakeBackend()
        val sync = BuiltinDocsSync(backend)
        sync.sync(BuiltinDocsSync.snapshot(mapOf("a.md" to "A", "b.md" to "B")))

        val result = sync.sync(BuiltinDocsSync.snapshot(mapOf("a.md" to "A")))

        assertTrue(result.changed)
        assertEquals(1, result.deleted)
        assertEquals(0, result.uploaded)
        assertFalse(backend.files.containsKey("b.md"))
        assertTrue(backend.files.containsKey("a.md"))
    }

    @Test
    fun snapshot_fingerprintIsDeterministicAndHashesContent() {
        val first = BuiltinDocsSync.snapshot(mapOf("a.md" to "x"))
        val second = BuiltinDocsSync.snapshot(mapOf("a.md" to "x"))
        assertEquals(first.fingerprint, second.fingerprint)

        val doc = first.documents["a.md"]!!
        assertEquals(BuiltinDocsSync.sha256("x".toByteArray(Charsets.UTF_8)), doc.hash)
        assertTrue(doc.bytes.contentEquals("x".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun sha256_isStableHexDigest() {
        // 已知的 SHA-256("abc")
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            BuiltinDocsSync.sha256("abc".toByteArray(Charsets.UTF_8))
        )
    }
}
