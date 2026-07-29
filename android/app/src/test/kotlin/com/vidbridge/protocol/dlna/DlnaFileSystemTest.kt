package com.vidbridge.protocol.dlna

import com.vidbridge.protocol.api.Endpoint
import com.vidbridge.protocol.api.MediaSourceConfig
import com.vidbridge.protocol.api.MediaSourceType
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DlnaFileSystemTest {
    @Test
    fun connectsAndBrowsesContentDirectory() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    <root><device><friendlyName>测试媒体服务器</friendlyName><serviceList>
                      <service><serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType><controlURL>/ctl</controlURL></service>
                    </serviceList></device></root>
                    """.trimIndent(),
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>
                      <u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
                        <Result>&lt;DIDL-Lite&gt;&lt;container id=&quot;folder&quot; parentID=&quot;0&quot;&gt;&lt;dc:title xmlns:dc=&quot;urn:schemas-dc-org:element-1.1/&quot;&gt;电影&lt;/dc:title&gt;&lt;/container&gt;&lt;item id=&quot;video&quot; parentID=&quot;0&quot;&gt;&lt;dc:title xmlns:dc=&quot;urn:schemas-dc-org:element-1.1/&quot;&gt;影片&lt;/dc:title&gt;&lt;res size=&quot;42&quot;&gt;${server.url("/video.mkv")}&lt;/res&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;</Result>
                        <TotalMatches>2</TotalMatches>
                      </u:BrowseResponse>
                    </s:Body></s:Envelope>
                    """.trimIndent(),
                ),
            )
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Length", "42"))
            val config = MediaSourceConfig(
                id = "dlna-test",
                displayName = "测试 DLNA",
                type = MediaSourceType.DLNA,
                endpoint = Endpoint("http", "127.0.0.1", server.port),
                rootPath = "/device.xml",
                shareName = null,
                rootUri = null,
                username = null,
                credentialId = null,
                enabled = true,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
            val fileSystem = DlnaFileSystem("dlna-test", config, OkHttpClient())

            fileSystem.connect()
            val page = fileSystem.list(com.vidbridge.protocol.api.RemotePath(""))

            assertEquals(2, page.items.size)
            assertTrue(page.items[0].isDirectory)
            assertEquals("电影", page.items[0].name)
            assertFalse(page.items[1].isDirectory)
            assertEquals("影片.mkv", page.items[1].name)
            assertEquals(42L, page.items[1].size)
            assertTrue(page.items[1].path.value.startsWith("dlna-item/"))
            fileSystem.open(page.items[1].path).use { handle -> assertEquals(42L, handle.size) }
            assertEquals("/device.xml", server.takeRequest().path)
            assertEquals("/ctl", server.takeRequest().path)
            assertEquals("/video.mkv", server.takeRequest().path)
            fileSystem.close()
        }
    }

}
