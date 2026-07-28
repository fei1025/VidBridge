package com.vidbridge.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.*
import com.vidbridge.protocol.api.RemoteFileSystem
import com.vidbridge.protocol.api.RemoteFileSystemFactory
import com.vidbridge.protocol.api.RemotePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.net.URLDecoder
import java.net.URLEncoder

object PlaybackUris {
    fun remote(sourceId: String, path: RemotePath): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority(sourceId)
        .appendQueryParameter("path", path.value)
        .build()

    const val SCHEME = "vidbridge-remote"
}

@UnstableApi
class RemoteDataSource(
    private val fileSystems: RemoteFileSystemFactory,
) : BaseDataSource(true) {
    private var fs: RemoteFileSystem? = null
    private var handle: com.vidbridge.protocol.api.RemoteReadHandle? = null
    private var position = 0L
    private var remaining = 0L
    private var opened = false
    private var openedUri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val sourceId = requireNotNull(dataSpec.uri.host)
        val path = requireNotNull(dataSpec.uri.getQueryParameter("path"))
        return runBlocking(Dispatchers.IO) {
            val remote = fileSystems.create(sourceId)
            try {
                remote.connect()
                val readHandle = remote.open(RemotePath(path))
                fs = remote
                handle = readHandle
                position = dataSpec.position
                val available = (readHandle.size ?: C.LENGTH_UNSET.toLong()).let {
                    if (it == C.LENGTH_UNSET.toLong()) it else (it - position).coerceAtLeast(0)
                }
                remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) available else {
                    if (available == C.LENGTH_UNSET.toLong()) dataSpec.length else minOf(available, dataSpec.length)
                }
                opened = true
                openedUri = dataSpec.uri
                transferStarted(dataSpec)
                remaining
            } catch (error: Throwable) {
                remote.close()
                throw DataSourceException(error, PlaybackExceptionCodes.IO_UNSPECIFIED)
            }
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val requested = if (remaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), remaining).toInt()
        val bytes = try {
            runBlocking(Dispatchers.IO) { handle!!.readAt(position, requested) }
        } catch (error: Throwable) {
            throw DataSourceException(error, PlaybackExceptionCodes.IO_UNSPECIFIED)
        }
        if (bytes.isEmpty()) return C.RESULT_END_OF_INPUT
        bytes.copyInto(buffer, offset)
        position += bytes.size
        if (remaining != C.LENGTH_UNSET.toLong()) remaining -= bytes.size
        bytesTransferred(bytes.size)
        return bytes.size
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        runCatching { handle?.close() }
        runCatching { fs?.close() }
        handle = null
        fs = null
        openedUri = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(private val fileSystems: RemoteFileSystemFactory) : DataSource.Factory {
        override fun createDataSource(): DataSource = RemoteDataSource(fileSystems)
    }
}

@UnstableApi
class RoutingDataSourceFactory(
    context: Context,
    private val remoteFactory: DataSource.Factory,
) : DataSource.Factory {
    private val defaultFactory = DefaultDataSource.Factory(context)
    override fun createDataSource(): DataSource = RoutingDataSource(defaultFactory.createDataSource(), remoteFactory.createDataSource())
}

@UnstableApi
private class RoutingDataSource(
    private val fallback: DataSource,
    private val remote: DataSource,
) : DataSource {
    private var active: DataSource? = null
    override fun addTransferListener(transferListener: TransferListener) {
        fallback.addTransferListener(transferListener)
        remote.addTransferListener(transferListener)
    }
    override fun open(dataSpec: DataSpec): Long {
        active = if (dataSpec.uri.scheme == PlaybackUris.SCHEME) remote else fallback
        return active!!.open(dataSpec)
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int) = active!!.read(buffer, offset, length)
    override fun getUri(): Uri? = active?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()
    override fun close() {
        active?.close()
        active = null
    }
}

private object PlaybackExceptionCodes {
    const val IO_UNSPECIFIED = 2000
}
