package com.vidbridge.protocol.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DlnaXmlParsersTest {
    @Test
    fun resolvesContentDirectoryControlUrl() {
        val result = DlnaXmlParsers.parseDeviceDescription(
            """
            <root><device><friendlyName>客厅电视</friendlyName><serviceList>
              <service><serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType><controlURL>/ctl/content</controlURL></service>
            </serviceList></device></root>
            """.trimIndent(),
            "http://192.168.1.20:8200/device.xml",
        )
        assertNotNull(result)
        assertEquals("客厅电视", result?.friendlyName)
        assertEquals("http://192.168.1.20:8200/ctl/content", result?.controlUrl)
    }

    @Test
    fun rejectsControlUrlOnDifferentHost() {
        assertNull(
            DlnaXmlParsers.parseDeviceDescription(
                "<root><device><friendlyName>TV</friendlyName><serviceList><service><serviceType>ContentDirectory</serviceType><controlURL>http://8.8.8.8/ctl</controlURL></service></serviceList></device></root>",
                "http://192.168.1.20/device.xml",
            ),
        )
    }

    @Test
    fun parsesDidlContainersAndResources() {
        val entries = DlnaXmlParsers.parseDidl(
            """
            <DIDL-Lite xmlns:dc="urn:schemas-dc-org:element-1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
              <container id="1" parentID="0"><dc:title>电影</dc:title></container>
              <item id="2" parentID="1"><dc:title>影片.mkv</dc:title><res size="12345" duration="01:02:03.500">http://192.168.1.20/video.mkv</res></item>
            </DIDL-Lite>
            """.trimIndent(),
        )

        assertEquals(2, entries.size)
        assertEquals("电影", entries[0].title)
        assertEquals("影片.mkv", entries[1].title)
        assertEquals(12345L, entries[1].size)
        assertEquals(3723L, entries[1].durationSeconds)
    }

    @Test
    fun parsesSoapBrowseResultAndTotalMatches() {
        val page = DlnaXmlParsers.parseBrowseResponse(
            """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body><u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
                <Result>&lt;DIDL-Lite&gt;&lt;item id=&quot;v1&quot; parentID=&quot;0&quot;&gt;&lt;dc:title xmlns:dc=&quot;urn:schemas-dc-org:element-1.1/&quot;&gt;影片.mkv&lt;/dc:title&gt;&lt;res&gt;http://192.168.1.20/video.mkv&lt;/res&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;</Result>
                <NumberReturned>1</NumberReturned><TotalMatches>7</TotalMatches><UpdateID>1</UpdateID>
              </u:BrowseResponse></s:Body>
            </s:Envelope>
            """.trimIndent(),
        )

        assertNotNull(page)
        assertEquals(1, page?.entries?.size)
        assertEquals("影片.mkv", page?.entries?.single()?.title)
        assertEquals(7, page?.totalMatches)
    }
}
