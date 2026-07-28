package com.vidbridge.protocol.local

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import com.vidbridge.protocol.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.time.Instant

class LocalFileSystem(
    private val context: Context,
    private val config: MediaSourceConfig,
) : RemoteFileSystem {
    override val sourceId: String = config.id
    override val capabilities = SourceCapabilities(
        canList = true,
        canStat = true,
        canSeekRead = true,
        canStreamRead = true,
    )

    private val resolver get() = context.contentResolver
    private var root: DocumentFile? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        if (root != null) return@withContext
        val rootUri = config.rootUri?.let(Uri::parse) ?: throw SourceFailure.ProtocolMismatch()
        try {
            val document = DocumentFile.fromTreeUri(context, rootUri)
                ?: throw SourceFailure.NotFound()
            if (!document.exists()) throw SourceFailure.NotFound()
            if (!document.canRead()) throw SourceFailure.PermissionDenied()
            root = document
        } catch (error: SourceFailure) {
            throw error
        } catch (error: SecurityException) {
            throw SourceFailure.PermissionDenied(error)
        } catch (error: Throwable) {
            throw SourceFailure.Unknown(error)
        }
    }

    override suspend fun list(path: RemotePath, page: PageRequest?): Page<RemoteEntry> =
        withContext(Dispatchers.IO) {
            val directory = resolve(path)
            if (!directory.isDirectory) throw SourceFailure.UnsupportedOperation()
            val all = try {
                directory.listFiles().asSequence()
                    .mapNotNull { child ->
                        val name = child.name ?: return@mapNotNull null
                        RemoteEntry(
                            name = name,
                            path = path.child(name),
                            isDirectory = child.isDirectory,
                            size = child.length().takeUnless { child.isDirectory },
                            modifiedAt = child.lastModified().takeIf { it > 0 }?.let(Instant::ofEpochMilli),
                        )
                    }
                    .sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    .toList()
            } catch (error: SecurityException) {
                throw SourceFailure.PermissionDenied(error)
            }
            val request = page ?: PageRequest(0, all.size.coerceAtLeast(1))
            val items = all.drop(request.offset).take(request.limit)
            val nextOffset = request.offset + items.size
            Page(items, if (nextOffset < all.size) PageRequest(nextOffset, request.limit) else null)
        }

    override suspend fun stat(path: RemotePath): RemoteFileInfo = withContext(Dispatchers.IO) {
        val document = resolve(path)
        RemoteFileInfo(
            path = path,
            size = document.length().takeUnless { document.isDirectory },
            modifiedAt = document.lastModified().takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        )
    }

    override suspend fun open(path: RemotePath): RemoteReadHandle = withContext(Dispatchers.IO) {
        val document = resolve(path)
        if (!document.isFile) throw SourceFailure.UnsupportedOperation()
        try {
            val descriptor = resolver.openFileDescriptor(document.uri, "r")
                ?: throw SourceFailure.NotFound()
            LocalReadHandle(descriptor, document.length())
        } catch (error: SourceFailure) {
            throw error
        } catch (error: SecurityException) {
            throw SourceFailure.PermissionDenied(error)
        } catch (error: Throwable) {
            throw SourceFailure.Unknown(error)
        }
    }

    private suspend fun resolve(path: RemotePath): DocumentFile {
        if (root == null) connect()
        var current = root ?: throw SourceFailure.NotFound()
        val segments = path.value.trim('/').split('/').filter { it.isNotEmpty() }
        for (segment in segments) {
            if (segment == "." || segment == "..") throw SourceFailure.PermissionDenied()
            current = current.findFile(segment) ?: throw SourceFailure.NotFound()
        }
        return current
    }

    override fun close() {
        root = null
    }
}

private class LocalReadHandle(
    private val descriptor: ParcelFileDescriptor,
    documentLength: Long,
) : RemoteReadHandle {
    private val input = FileInputStream(descriptor.fileDescriptor)
    private val channel = input.channel
    private val mutex = Mutex()
    @Volatile private var closed = false

    override val size: Long? = descriptor.statSize.takeIf { it >= 0 }
        ?: documentLength.takeIf { it >= 0 }
    override val seekable = true

    override suspend fun readAt(offset: Long, length: Int): ByteArray = withContext(Dispatchers.IO) {
        require(offset >= 0 && length >= 0)
        mutex.withLock {
            check(!closed) { "读取句柄已关闭" }
            if (length == 0 || (size != null && offset >= size!!)) return@withLock ByteArray(0)
            val requested = size?.let { minOf(length.toLong(), it - offset).toInt() } ?: length
            val buffer = ByteBuffer.allocate(requested)
            val count = channel.read(buffer, offset)
            if (count <= 0) ByteArray(0) else buffer.array().copyOf(count)
        }
    }

    override fun stream(startOffset: Long): Flow<ByteArray> = flow {
        var offset = startOffset
        while (!closed && (size == null || offset < size!!)) {
            val bytes = readAt(offset, CHUNK_SIZE)
            if (bytes.isEmpty()) break
            emit(bytes)
            offset += bytes.size
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { input.close() }
        runCatching { descriptor.close() }
    }

    private companion object { const val CHUNK_SIZE = 1024 * 1024 }
}
