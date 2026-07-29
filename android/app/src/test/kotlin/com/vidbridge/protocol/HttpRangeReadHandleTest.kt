package com.vidbridge.protocol

import com.vidbridge.protocol.api.HttpRangeReadHandle
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HttpRangeReadHandleTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
        client.dispatcher.executorService.shutdownNow()
        client.connectionPool.evictAll()
    }

    @Test
    fun sendsRangeAndReadsPartialResponse() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 4-7/10")
                .setBody("4567"),
        )
        val handle = HttpRangeReadHandle(client, server.url("/movie.mkv"))

        assertEquals("4567", String(handle.readAt(4, 4)))
        assertEquals("bytes=4-7", server.takeRequest().getHeader("Range"))
        handle.close()
    }

    @Test
    fun skipsOffsetWhenServerIgnoresRange() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("0123456789"))
        val handle = HttpRangeReadHandle(client, server.url("/movie.mkv"))

        assertEquals("3456", String(handle.readAt(3, 4)))
        assertEquals("bytes=3-6", server.takeRequest().getHeader("Range"))
        handle.close()
    }
}
