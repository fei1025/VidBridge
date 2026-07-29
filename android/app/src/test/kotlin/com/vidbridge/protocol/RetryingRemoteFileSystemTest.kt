package com.vidbridge.protocol

import com.vidbridge.protocol.api.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RetryingRemoteFileSystemTest {
    @Test
    fun retriesTimeoutAfterReconnecting() = runBlocking {
        val delegate = FakeFileSystem(failFirst = true)
        val remote = RetryingRemoteFileSystem(delegate, attempts = 2)
        assertEquals(emptyList<RemoteEntry>(), remote.list(RemotePath(""), null).items)
        assertEquals(2, delegate.listCalls)
        assertEquals(1, delegate.connectCalls)
    }

    @Test
    fun doesNotRetryAuthenticationFailure() = runBlocking {
        val delegate = FakeFileSystem(authenticationFailure = true)
        val remote = RetryingRemoteFileSystem(delegate, attempts = 2)
        assertThrows(SourceFailure.AuthenticationRejected::class.java) {
            runBlocking { remote.list(RemotePath(""), null) }
        }
        assertEquals(1, delegate.listCalls)
    }

    @Test
    fun reopensReadHandleAfterTransientReadFailure() = runBlocking {
        val delegate = FakeFileSystem(readFailsFirst = true)
        val remote = RetryingRemoteFileSystem(delegate, attempts = 2)
        remote.connect()
        remote.open(RemotePath("movie.mkv")).use { handle ->
            assertEquals("ok", String(handle.readAt(0, 2)))
        }
        assertEquals(2, delegate.openCalls)
        assertEquals(2, delegate.readCalls)
    }

    private class FakeFileSystem(
        private val failFirst: Boolean = false,
        private val authenticationFailure: Boolean = false,
        private val readFailsFirst: Boolean = false,
    ) : RemoteFileSystem {
        var listCalls = 0
        var connectCalls = 0
        var openCalls = 0
        var readCalls = 0
        override val sourceId = "test"
        override val capabilities = SourceCapabilities(true, true, true, true)
        override suspend fun connect() { connectCalls++ }
        override suspend fun list(path: RemotePath, page: PageRequest?): Page<RemoteEntry> {
            listCalls++
            if (authenticationFailure) throw SourceFailure.AuthenticationRejected()
            if (failFirst && listCalls == 1) throw SourceFailure.Timeout()
            return Page(emptyList(), null)
        }
        override suspend fun stat(path: RemotePath) = RemoteFileInfo(path, 0, null)
        override suspend fun open(path: RemotePath): RemoteReadHandle {
            openCalls++
            return object : RemoteReadHandle {
                override val size = 2L
                override val seekable = true
                override suspend fun readAt(offset: Long, length: Int): ByteArray {
                    readCalls++
                    if (readFailsFirst && readCalls == 1) throw SourceFailure.Timeout()
                    return "ok".toByteArray()
                }
                override fun stream(startOffset: Long) = kotlinx.coroutines.flow.flow { emit("ok".toByteArray()) }
                override fun close() = Unit
            }
        }
        override fun close() = Unit
    }
}
