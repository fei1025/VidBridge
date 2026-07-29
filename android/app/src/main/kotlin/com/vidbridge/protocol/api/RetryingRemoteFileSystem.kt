package com.vidbridge.protocol.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val RETRYING_READ_CHUNK_SIZE = 1024 * 1024

/** Adds bounded session recovery for remote protocols without retrying user/configuration errors. */
class RetryingRemoteFileSystem(
    private val delegate: RemoteFileSystem,
    private val attempts: Int = 2,
) : RemoteFileSystem {
    override val sourceId: String = delegate.sourceId
    override val capabilities: SourceCapabilities = delegate.capabilities

    override suspend fun connect() = retry { delegate.connect() }

    override suspend fun list(path: RemotePath, page: PageRequest?): Page<RemoteEntry> =
        retry { delegate.list(path, page) }

    override suspend fun stat(path: RemotePath): RemoteFileInfo = retry { delegate.stat(path) }

    override suspend fun open(path: RemotePath): RemoteReadHandle =
        RetryingReadHandle(path, retry { delegate.open(path) })

    override fun close() = delegate.close()

    private suspend fun <T> retry(block: suspend () -> T): T {
        var last: Throwable? = null
        repeat(attempts.coerceAtLeast(1)) { index ->
            try {
                return block()
            } catch (error: SourceFailure.Timeout) {
                last = error
            } catch (error: SourceFailure.HostUnreachable) {
                last = error
            }
            if (index + 1 < attempts) {
                delegate.close()
                delay(250L * (index + 1))
                delegate.connect()
            }
        }
        throw (last ?: SourceFailure.Unknown(IllegalStateException("远程操作失败")))
    }

    private inner class RetryingReadHandle(
        private val path: RemotePath,
        private var handle: RemoteReadHandle,
    ) : RemoteReadHandle {
        @Volatile private var closed = false

        override val size: Long? get() = handle.size
        override val seekable: Boolean get() = handle.seekable

        override suspend fun readAt(offset: Long, length: Int): ByteArray {
            check(!closed) { "读取句柄已关闭" }
            var last: Throwable? = null
            repeat(attempts.coerceAtLeast(1)) { index ->
                try {
                    return handle.readAt(offset, length)
                } catch (error: SourceFailure.Timeout) {
                    last = error
                } catch (error: SourceFailure.HostUnreachable) {
                    last = error
                }
                if (index + 1 < attempts) {
                    runCatching { handle.close() }
                    delegate.close()
                    delay(250L * (index + 1))
                    handle = retry { delegate.open(path) }
                }
            }
            throw (last ?: SourceFailure.Unknown(IllegalStateException("远程读取失败")))
        }

        override fun stream(startOffset: Long): Flow<ByteArray> = flow {
            var offset = startOffset
            while (!closed && (size == null || offset < size!!)) {
                val bytes = readAt(offset, RETRYING_READ_CHUNK_SIZE)
                if (bytes.isEmpty()) break
                emit(bytes)
                offset += bytes.size
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            runCatching { handle.close() }
        }
    }
}
