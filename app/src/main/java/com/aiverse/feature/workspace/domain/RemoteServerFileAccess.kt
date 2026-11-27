package com.aiverse.feature.workspace.domain

import com.aiverse.feature.agent.domain.container.RemoteExploreClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.FileAlreadyExistsException
import java.nio.file.NoSuchFileException
import java.nio.file.Paths
import java.util.EnumSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.SFTPClient

/**
 * 文件树中的独立 SSH 服务器根。
 *
 * 路径使用虚拟前缀 [RemoteServerPaths.PREFIX]/<connectionId>/<远程绝对路径>，与本地容器、工作区路径完全分开。
 * 每次文件操作都通过 [RemoteExploreClient] 建立独立 SFTP transport，因此本地执行模式下也能读取远程服务器，
 * 不会影响当前容器，也不会把远程路径误映射到本地。
 */
@Singleton
class RemoteServerFileAccess @Inject constructor(
    private val explore: RemoteExploreClient
) : FileAccessProvider {

    override fun readFile(path: String): String = String(readAll(path), Charsets.UTF_8)

    /** 远程根不保持长连接；整文件读入后按行返回，避免为惰性迭代跨多次独立连接。 */
    override fun readLines(path: String): Sequence<String> = readFile(path).lineSequence()

    override fun writeFile(path: String, content: String, overwrite: Boolean, encoding: Charset) =
        writeBytes(path, content.toByteArray(encoding), overwrite)

    override fun exists(path: String): Boolean = withTarget(path) { sftp, remote ->
        sftp.statExistence(remote) != null
    }

    override fun isDirectory(path: String): Boolean = withTarget(path) { sftp, remote ->
        sftp.statExistence(remote)?.type == FileMode.Type.DIRECTORY
    }

    override fun isFile(path: String): Boolean = withTarget(path) { sftp, remote ->
        sftp.statExistence(remote)?.type == FileMode.Type.REGULAR
    }

    override fun fileSize(path: String): Long = withTarget(path) { sftp, remote ->
        sftp.statExistence(remote)?.size ?: 0L
    }

    override fun lastModified(path: String): Long = withTarget(path) { sftp, remote ->
        (sftp.statExistence(remote)?.mtime ?: 0L) * 1000L
    }

    override fun fingerprint(path: String): String? = withTarget(path) { sftp, remote ->
        val attrs = sftp.statExistence(remote) ?: return@withTarget null
        if (attrs.type != FileMode.Type.REGULAR) return@withTarget null
        if (attrs.size > MAX_READ_BYTES) {
            throw RemoteOutputTooLargeException("远程文件超过 ${MAX_READ_BYTES / 1024 / 1024}MB，无法计算内容指纹")
        }
        sftp.open(remote).use { rf -> sha256Fingerprint(readFully(rf)) }
    }

    override fun permissions(path: String): String = withTarget(path) { sftp, remote ->
        sftp.statExistence(remote)?.permissions?.let(::formatPermissions) ?: "---"
    }

    override fun listFiles(path: String): List<FileEntry> = withTarget(path) { sftp, remote ->
        sftp.ls(remote).mapNotNull { info ->
            val name = info.name
            if (name == "." || name == "..") return@mapNotNull null
            FileEntry(
                name = name,
                isDirectory = info.isDirectory,
                size = info.attributes.size,
                lastModified = info.attributes.mtime * 1000L,
                localFile = null,
                permissions = formatPermissions(info.attributes.permissions)
            )
        }
    }

    override fun listFilesRecursive(path: String, maxDepth: Int): List<String> = withTarget(path) { sftp, remote ->
        val result = mutableListOf<String>()
        fun walk(dir: String, rel: String, depth: Int) {
            val entries = runCatching { sftp.ls(dir) }.getOrNull() ?: return
            for (info in entries) {
                val name = info.name
                if (name == "." || name == "..") continue
                val childRel = if (rel.isEmpty()) name else "$rel/$name"
                if (info.isDirectory) {
                    if (depth < maxDepth) walk(info.path, childRel, depth + 1)
                } else {
                    result += childRel
                }
            }
        }
        walk(remote, "", 1)
        result
    }

    override fun readBytes(path: String): ByteArray = readAll(path)

    override fun readBytes(path: String, maxBytes: Long): ByteArray = withTarget(path) { sftp, remote ->
        val attrs = sftp.statExistence(remote) ?: throw NoSuchFileException(File(remote))
        if (attrs.type == FileMode.Type.DIRECTORY) throw IOException("是目录，无法按文件读取: $remote")
        if (attrs.size > maxBytes) throw FileReadTooLargeException(maxBytes)
        sftp.open(remote).use { rf ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(IO_CHUNK)
            var offset = 0L
            while (out.size().toLong() < maxBytes) {
                val n = rf.read(offset, buf, 0, minOf(buf.size.toLong(), maxBytes - out.size()).toInt())
                if (n <= 0) break
                out.write(buf, 0, n)
                offset += n
            }
            out.toByteArray()
        }
    }

    override fun readBytes(path: String, offset: Long, limit: Long): ByteArray = withTarget(path) { sftp, remote ->
        val attrs = sftp.statExistence(remote) ?: throw NoSuchFileException(File(remote))
        if (attrs.type == FileMode.Type.DIRECTORY) throw IOException("是目录，无法按文件读取: $remote")
        if (offset >= attrs.size || limit <= 0) return@withTarget ByteArray(0)
        sftp.open(remote, EnumSet.of(OpenMode.READ)).use { rf ->
            val buf = ByteArray(minOf(limit, Int.MAX_VALUE.toLong()).toInt())
            var pos = offset
            var total = 0
            while (total < buf.size) {
                val n = rf.read(pos, buf, total, buf.size - total)
                if (n <= 0) break
                total += n
                pos += n
            }
            if (total == buf.size) buf else buf.copyOf(total)
        }
    }

    override fun resolvePath(path: String, followFinalLink: Boolean): String = withTarget(path) { sftp, remote ->
        val resolved = runCatching { sftp.canonicalize(remote) }.getOrElse { normalizeRemote(remote) }
        display(targetConnectionId(path), resolved)
    }

    override fun writeBytes(path: String, bytes: ByteArray, overwrite: Boolean) {
        withTarget(path) { sftp, remote ->
            if (sftp.statExistence(remote) != null && !overwrite) throw FileAlreadyExistsException(File(remote))
            ensureParent(sftp, remote)
            val tmp = "$remote$TMP_SUFFIX"
            try {
                sftp.open(tmp, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)).use { rf ->
                    writeAll(rf, bytes)
                }
                replace(sftp, tmp, remote)
            } catch (e: Exception) {
                runCatching { sftp.rm(tmp) }
                throw e
            }
        }
    }

    override fun writeStream(path: String, input: InputStream, overwrite: Boolean): Long = withTarget(path) { sftp, remote ->
        if (sftp.statExistence(remote) != null && !overwrite) throw FileAlreadyExistsException(File(remote))
        ensureParent(sftp, remote)
        val tmp = "$remote$TMP_SUFFIX"
        val written = try {
            sftp.open(tmp, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)).use { rf ->
                val buf = ByteArray(IO_CHUNK)
                var offset = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n == 0) continue
                    rf.write(offset, buf, 0, n)
                    offset += n
                }
                offset
            }
        } catch (e: Exception) {
            runCatching { sftp.rm(tmp) }
            throw e
        }
        replace(sftp, tmp, remote)
        written
    }

    override fun copyToLocal(path: String): File = withTarget(path) { sftp, remote ->
        val temp = File.createTempFile("aiverse_remote_server_", ".copy").apply { deleteOnExit() }
        try {
            if (sftp.statExistence(remote) == null) throw NoSuchFileException(File(remote))
            sftp.get(remote, temp.absolutePath)
            temp
        } catch (e: Exception) {
            runCatching { temp.delete() }
            throw e
        }
    }

    override fun delete(path: String) {
        withTarget(path) { sftp, remote ->
            val attrs = sftp.statExistence(remote) ?: return@withTarget
            if (attrs.type == FileMode.Type.DIRECTORY) sftp.rmdir(remote) else sftp.rm(remote)
        }
    }

    override fun deleteRecursively(path: String) {
        withTarget(path) { sftp, remote -> deleteRecursive(sftp, remote) }
    }

    override fun rename(path: String, newPath: String) {
        val from = requireTarget(path)
        val to = requireTarget(newPath)
        requireSameConnection(from, to)
        withTarget(path) { sftp, _ ->
            if (sftp.statExistence(from.remotePath) == null) throw NoSuchFileException(File(from.remotePath))
            if (sftp.statExistence(to.remotePath) != null) throw FileAlreadyExistsException(File(to.remotePath))
            sftp.rename(from.remotePath, to.remotePath)
        }
    }

    override fun copy(path: String, newPath: String, overwrite: Boolean) {
        val from = requireTarget(path)
        val to = requireTarget(newPath)
        requireSameConnection(from, to)
        withTarget(path) { sftp, _ ->
            val source = sftp.statExistence(from.remotePath) ?: throw NoSuchFileException(File(from.remotePath))
            if (to.remotePath == from.remotePath ||
                (source.type == FileMode.Type.DIRECTORY && Paths.get(to.remotePath).startsWith(Paths.get(from.remotePath)))
            ) {
                throw IOException("destination is the source or its descendant: $newPath")
            }
            if (sftp.statExistence(to.remotePath) != null) {
                if (!overwrite) throw FileAlreadyExistsException(File(to.remotePath))
                deleteRecursive(sftp, to.remotePath)
            }
            copyRecursive(sftp, from.remotePath, to.remotePath)
        }
    }

    override fun move(path: String, newPath: String, overwrite: Boolean) {
        val from = requireTarget(path)
        val to = requireTarget(newPath)
        requireSameConnection(from, to)
        withTarget(path) { sftp, _ ->
            val source = sftp.statExistence(from.remotePath) ?: throw NoSuchFileException(File(from.remotePath))
            if (to.remotePath == from.remotePath ||
                (source.type == FileMode.Type.DIRECTORY && Paths.get(to.remotePath).startsWith(Paths.get(from.remotePath)))
            ) {
                throw IOException("destination is the source or its descendant: $newPath")
            }
            if (sftp.statExistence(to.remotePath) != null) {
                if (!overwrite) throw FileAlreadyExistsException(File(to.remotePath))
                deleteRecursive(sftp, to.remotePath)
            }
            sftp.rename(from.remotePath, to.remotePath)
        }
    }

    override fun mkdirs(path: String) {
        withTarget(path) { sftp, remote -> sftp.mkdirs(remote) }
    }

    override fun parentPath(path: String): String? {
        val target = RemoteServerPaths.parse(path) ?: return null
        if (target.remotePath == "/") return null
        val parent = target.remotePath.substringBeforeLast('/', "").ifEmpty { "/" }
        return display(target.connectionId, parent)
    }

    override fun toDisplayPath(path: String): String {
        val target = RemoteServerPaths.parse(path) ?: return path
        return display(target.connectionId, target.remotePath)
    }

    private fun readAll(path: String): ByteArray = withTarget(path) { sftp, remote ->
        val attrs = sftp.statExistence(remote) ?: throw NoSuchFileException(File(remote))
        if (attrs.type == FileMode.Type.DIRECTORY) throw IOException("是目录，无法按文件读取: $remote")
        if (attrs.size > MAX_READ_BYTES) {
            throw RemoteOutputTooLargeException(
                "远程文件超过 ${MAX_READ_BYTES / 1024 / 1024}MB，请改用分段读取"
            )
        }
        sftp.open(remote).use { rf -> readFully(rf) }
    }

    private fun readFully(rf: RemoteFile): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(IO_CHUNK)
        var offset = 0L
        while (true) {
            val n = rf.read(offset, buf, 0, buf.size)
            if (n <= 0) break
            out.write(buf, 0, n)
            offset += n
        }
        return out.toByteArray()
    }

    private fun writeAll(rf: RemoteFile, bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val n = minOf(IO_CHUNK, bytes.size - offset)
            rf.write(offset.toLong(), bytes, offset, n)
            offset += n
        }
    }

    private fun replace(sftp: SFTPClient, tmp: String, remote: String) {
        if (sftp.statExistence(remote) != null) runCatching { sftp.rm(remote) }
        sftp.rename(tmp, remote)
    }

    private fun deleteRecursive(sftp: SFTPClient, remote: String) {
        val attrs = sftp.statExistence(remote) ?: return
        if (attrs.type == FileMode.Type.DIRECTORY) {
            for (info in sftp.ls(remote)) {
                if (info.name == "." || info.name == "..") continue
                deleteRecursive(sftp, info.path)
            }
            sftp.rmdir(remote)
        } else {
            sftp.rm(remote)
        }
    }

    private fun copyRecursive(sftp: SFTPClient, from: String, to: String) {
        val attrs = sftp.statExistence(from) ?: throw NoSuchFileException(File(from))
        if (attrs.type == FileMode.Type.DIRECTORY) {
            sftp.mkdirs(to)
            for (info in sftp.ls(from)) {
                if (info.name == "." || info.name == "..") continue
                copyRecursive(sftp, info.path, "$to/${info.name}")
            }
        } else {
            sftp.open(from).use { src ->
                sftp.open(to, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)).use { dst ->
                    val buf = ByteArray(IO_CHUNK)
                    var offset = 0L
                    while (true) {
                        val n = src.read(offset, buf, 0, buf.size)
                        if (n <= 0) break
                        dst.write(offset, buf, 0, n)
                        offset += n
                    }
                }
            }
        }
    }

    private fun ensureParent(sftp: SFTPClient, remote: String) {
        val parent = remote.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) sftp.mkdirs(parent)
    }

    private fun normalizeRemote(path: String): String {
        val absolute = path.startsWith('/')
        val parts = ArrayDeque<String>()
        for (part in path.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        return (if (absolute) "/" else "") + parts.joinToString("/")
    }

    private fun display(connectionId: String, remotePath: String): String {
        val root = RemoteServerPaths.root(connectionId)
        return if (remotePath == "/") root else root + remotePath
    }

    private fun requireTarget(path: String): RemoteServerPaths.Target =
        RemoteServerPaths.parse(path) ?: throw IllegalArgumentException("不是远程服务器文件树路径: $path")

    private fun requireSameConnection(a: RemoteServerPaths.Target, b: RemoteServerPaths.Target) {
        require(a.connectionId == b.connectionId) { "不能跨 SSH 服务器移动/复制文件" }
    }

    private fun targetConnectionId(path: String): String =
        requireTarget(path).connectionId

    private fun <T> withTarget(path: String, block: (SFTPClient, String) -> T): T {
        val target = requireTarget(path)
        return runBlocking {
            explore.withSftp(target.connectionId) { sftp, _ -> block(sftp, target.remotePath) }
        }
    }

    private companion object {
        const val IO_CHUNK = 64 * 1024
        const val MAX_READ_BYTES = 8L * 1024 * 1024
        const val TMP_SUFFIX = ".aiverse-part"
    }
}

/** 文件树独立 SSH 根使用的虚拟路径规则。 */
object RemoteServerPaths {
    const val PREFIX = "/mnt/.aiverse-remote-sftp"

    data class Target(val connectionId: String, val remotePath: String)

    fun root(connectionId: String): String = "$PREFIX/$connectionId"

    fun isPath(path: String): Boolean = path == PREFIX || path.startsWith("$PREFIX/")

    fun parse(path: String): Target? {
        val normalized = path.trimEnd('/')
        if (!normalized.startsWith("$PREFIX/")) return null
        val rest = normalized.removePrefix("$PREFIX/")
        val connectionId = rest.substringBefore('/')
        if (connectionId.isBlank()) return null
        val remotePart = rest.substringAfter('/', "")
        val remotePath = if (remotePart.isBlank()) "/" else "/" + remotePart.trimStart('/')
        return Target(connectionId, remotePath)
    }

    fun isRoot(path: String): Boolean {
        val target = parse(path) ?: return false
        return path.trimEnd('/') == root(target.connectionId)
    }
}
