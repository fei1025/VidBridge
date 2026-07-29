package com.vidbridge.protocol.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream

/** Read-only HTTP Range handle used by media-server download adapters. */
class HttpRangeReadHandle(
    private val client: OkHttpClient,
    private val url: HttpUrl,
    private val headers: Headers = Headers.Builder().build(),
    override val size: Long? = null,
) : RemoteReadHandle {
    override val seekable: Boolean = true
    @Volatile private var closed = false

    override suspend fun readAt(offset: Long, length: Int): ByteArray = withContext(Dispatchers.IO) {
        check(!closed) { "读取句柄已关闭" }
        require(offset >= 0L) { "offset 不能为负数" }
        require(length >= 0) { "length 不能为负数" }
        if (length == 0 || (size != null && offset >= size)) return@withContext ByteArray(0)

        val end = offset + length - 1L
        val request = Request.Builder()
            .url(url)
            .headers(headers)
            .header("Range", "bytes=$offset-$end")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 416 -> return@withContext ByteArray(0)
                response.code == 401 || response.code == 403 -> throw SourceFailure.AuthenticationRejected()
                response.code == 404 -> throw SourceFailure.NotFound()
                response.code == 408 -> throw SourceFailure.Timeout()
                response.code == 429 || response.code in 500..599 -> throw SourceFailure.HostUnreachable()
                !response.isSuccessful -> throw SourceFailure.ProtocolMismatch()
            }
            val body = response.body ?: return@withContext ByteArray(0)
            body.byteStream().use { input ->
                // A few servers ignore Range and return 200. Skip only in that case;
                // normal 206 responses already start at the requested offset.
                if (response.code == 200 && offset > 0L) skipFully(input, offset)
                readLimited(input, length)
            }
        }
    }

    override fun stream(startOffset: Long): Flow<ByteArray> = flow {
        var offset = startOffset
        while (!closed && (size?.let { offset < it } ?: true)) {
            val bytes = readAt(offset, CHUNK_SIZE)
            if (bytes.isEmpty()) break
            emit(bytes)
            offset += bytes.size
        }
    }

    override fun close() {
        closed = true
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val result = ByteArray(limit)
        var count = 0
        while (count < limit) {
            val read = input.read(result, count, limit - count)
            if (read < 0) break
            count += read
        }
        return if (count == result.size) result else result.copyOf(count)
    }

    private fun skipFully(input: InputStream, bytes: Long) {
        var remaining = bytes
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
                continue
            }
            if (input.read() < 0) throw SourceFailure.HostUnreachable()
            remaining--
        }
    }

    private companion object {
        const val CHUNK_SIZE = 1024 * 1024
    }
}
